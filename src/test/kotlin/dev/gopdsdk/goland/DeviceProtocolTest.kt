package dev.gopdsdk.goland

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

class DeviceProtocolTest {
    @Test fun `log editor files are read only and known disk mode survives a USB-only probe`() {
        val file = deviceLogFile("crashlog.txt", "content\r\n")
        assertFalse(file.isWritable)
        assertEquals("content\r\n", file.content.toString())
        assertEquals(DeviceConnection.DISK, deviceFailureConnection(DeviceConnection.DISK, DeviceOperation.CONNECTION, "not-connected"))
        assertEquals(DeviceConnection.DISCONNECTED, deviceFailureConnection(DeviceConnection.CONNECTED, DeviceOperation.CONNECTION, "not-connected"))
        assertEquals(DeviceConnection.UNKNOWN, deviceFailureConnection(DeviceConnection.DISK, DeviceOperation.UNMOUNT, "reconnect-timeout"))
    }

    private fun envelope(operation: DeviceOperation, body: String) =
        """{"schema":"gopdsdk-tooling-result/v1","command":"${operation.command}","ok":true,"result":$body}"""

    private fun capability(operation: DeviceOperation) =
        """{"schema":"gopdsdk-tooling-result/v1","command":"capabilities","ok":true,"result":{"schema":"gopdsdk-tooling-capabilities/v1","commands":[{"name":"${operation.capability}","modes":["json"],"resultSchemas":["${operation.schema}"]}]}}"""

    @Test fun `arguments preserve SDK paths and never inject implicit log reads`() {
        for (operation in DeviceOperation.entries) {
            val arguments = operation.arguments(" C:/SDK with spaces ")
            assertEquals(operation.command.split(' '), arguments.take(operation.command.split(' ').size))
            assertTrue(arguments.windowed(2).contains(listOf("--sdk", "C:/SDK with spaces")))
            assertEquals(operation != DeviceOperation.CONNECTION, "--progress" in arguments)
        }
        assertEquals(listOf("run", "device", "--format", "json", "--progress", "."), DeviceOperation.RUN.arguments(""))
        assertEquals("build", DeviceOperation.BUILD.capability)
        assertEquals("probe", DeviceOperation.CONNECTION.capability)
        assertEquals("device disk unmount", DeviceOperation.UNMOUNT.capability)
    }

    @Test fun `USB readiness requires probe evidence and does not accept discovery`() {
        val body = """{"schema":"gopdsdk-probe/v1","probe":"connection","ready":true,"discovered":true,"evidenceLevel":"usb"}"""
        assertEquals(DeviceConnection.CONNECTED, DeviceProtocol.decode(envelope(DeviceOperation.CONNECTION, body), DeviceOperation.CONNECTION).connection)
        for (invalid in listOf(body.replace("usb", "discovery"), body.replace("true", "false"), body.replace("connection", "device"))) {
            assertThrows(Exception::class.java) { DeviceProtocol.decode(envelope(DeviceOperation.CONNECTION, invalid), DeviceOperation.CONNECTION) }
        }
    }

    @Test fun `build does not establish USB readiness and run requires both deployment and launch`() {
        val build = """{"schema":"gopdsdk-build/v1","target":"device","package":"example/game","artifact":"game.pdx"}"""
        assertNull(DeviceProtocol.decode(envelope(DeviceOperation.BUILD, build), DeviceOperation.BUILD).connection)
        val run = """{"schema":"gopdsdk-run/v1","target":"device","package":"example/game","deployment":"installed","execution":"launched"}"""
        assertEquals(DeviceConnection.CONNECTED, DeviceProtocol.decode(envelope(DeviceOperation.RUN, run), DeviceOperation.RUN).connection)
        for (invalid in listOf(run.replace("launched", "pending"), run.replace("installed", "pending"), run.replace("device", "simulator"))) {
            assertThrows(Exception::class.java) { DeviceProtocol.decode(envelope(DeviceOperation.RUN, invalid), DeviceOperation.RUN) }
        }
    }

    @Test fun `disk mount and eject require distinct confirmed modes`() {
        val mount = """{"schema":"gopdsdk-device-disk/v1","mode":"disk","mountPath":"E:/"}"""
        assertEquals(DeviceConnection.DISK, DeviceProtocol.decode(envelope(DeviceOperation.MOUNT, mount), DeviceOperation.MOUNT).connection)
        assertThrows(Exception::class.java) { DeviceProtocol.decode(envelope(DeviceOperation.UNMOUNT, mount), DeviceOperation.UNMOUNT) }
        val eject = """{"schema":"gopdsdk-device-disk/v1","mode":"connected"}"""
        assertEquals(DeviceConnection.CONNECTED, DeviceProtocol.decode(envelope(DeviceOperation.UNMOUNT, eject), DeviceOperation.UNMOUNT).connection)
    }

    @Test fun `log decoding preserves content and validates kind encoding and length`() {
        val text = "line one\r\nОшибка\n"
        val bytes = text.toByteArray(Charsets.UTF_8)
        for (operation in listOf(DeviceOperation.CRASHLOG, DeviceOperation.ERRORLOG)) {
            val body = """{"schema":"gopdsdk-device-log/v1","metadata":{"kind":"${operation.command}.txt","path":"E:/${operation.command}.txt","byteCount":${bytes.size}},"content":{"encoding":"base64","data":"${Base64.getEncoder().encodeToString(bytes)}"}}"""
            val result = DeviceProtocol.decode(envelope(operation, body), operation)
            assertEquals(text, result.log)
            assertEquals(DeviceConnection.DISK, result.connection)
            for (invalid in listOf(body.replace("base64", "utf8"), body.replace("\"byteCount\":${bytes.size}", "\"byteCount\":1"), body.replace("${operation.command}.txt", "foreign.txt"))) {
                assertThrows(Exception::class.java) { DeviceProtocol.decode(envelope(operation, invalid), operation) }
            }
        }
    }

    @Test fun `typed failures never become successful connection results`() {
        for (category in listOf("not-connected", "deployment-failed", "launch-failed", "eject-failed", "reconnect-timeout")) {
            val failure = """{"schema":"gopdsdk-tooling-result/v1","command":"run device","ok":false,"failure":{"category":"$category"}}"""
            val result = DeviceProtocol.decode(failure, DeviceOperation.RUN)
            assertEquals(category, result.failure)
            assertNull(result.connection)
            assertNull(result.log)
            assertThrows(Exception::class.java) { DeviceProtocol.decode(failure, DeviceOperation.BUILD) }
        }
    }

    @Test fun `progress frames split chunks and rejects foreign duplicate and stale events`() {
        val stages = mutableListOf<String>()
        val progress = DeviceProgress(DeviceOperation.RUN, stages::add)
        fun event(sequence: Int, stage: String, command: String = "run device") =
            """{"schema":"gopdsdk-progress/v1","command":"$command","sequence":$sequence,"stage":"$stage"}""" + "\n"
        val first = event(1, "compilation")
        progress.append(first.take(25)); progress.append(first.drop(25))
        progress.append(event(1, "duplicate") + event(0, "stale") + event(9, "foreign", "run"))
        progress.append(event(2, "connection") + event(3, "deployment") + event(4, "launch"))
        assertEquals(listOf("compilation", "connection", "deployment", "launch"), stages)
    }

    @Test fun `cancellation before or after command cannot return a log`() {
        for (cancelAt in 1..3) {
            var checks = 0
            val calls = mutableListOf<List<String>>()
            assertThrows(ProcessCanceledException::class.java) {
                executeDeviceOperation(DeviceOperation.CRASHLOG, "", {
                    if (++checks == cancelAt) throw ProcessCanceledException()
                }) { args ->
                    calls.add(args)
                    if (args.first() == "capabilities") capability(DeviceOperation.CRASHLOG) else "unused cancelled output"
                }
            }
            assertEquals(cancelAt - 1, calls.size)
        }
    }

    @Test fun `unsupported capabilities never invoke a device command and failed run never reads logs`() {
        val calls = mutableListOf<List<String>>()
        assertThrows(IllegalArgumentException::class.java) {
            executeDeviceOperation(DeviceOperation.RUN, "", {}) { args -> calls.add(args); capability(DeviceOperation.BUILD) }
        }
        assertEquals(listOf(listOf("capabilities")), calls)
        calls.clear()
        val result = executeDeviceOperation(DeviceOperation.RUN, "", {}) { args ->
            calls.add(args)
            if (args.first() == "capabilities") capability(DeviceOperation.RUN)
            else """{"schema":"gopdsdk-tooling-result/v1","command":"run device","ok":false,"failure":{"category":"launch-failed"}}"""
        }
        assertEquals("launch-failed", result.failure)
        assertEquals(listOf(listOf("capabilities"), DeviceOperation.RUN.arguments("")), calls)
    }
}

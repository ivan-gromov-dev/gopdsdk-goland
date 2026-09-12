package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ExecutableContractTest {
    @Test
    fun `configured executable wins over PATH`() {
        val root = Files.createTempDirectory("gopdsdk-discovery")
        val configured = executable(root.resolve("configured"))
        executable(root.resolve("gopdsdk"))

        assertEquals(configured.toAbsolutePath(), ExecutableDiscovery.find(configured.toString(), root.toString(), false))
    }

    @Test
    fun `missing executable is rejected`() {
        val root = Files.createTempDirectory("gopdsdk-missing")
        assertNull(ExecutableDiscovery.find("", root.toString(), false))
        assertNull(ExecutableDiscovery.find(root.resolve("missing").toString(), root.toString(), false))
    }

    @Test
    fun `compatible server is shut down cleanly`() {
        val process = FakeProcess(frames(initialize("v1", true), """{"jsonrpc":"2.0","id":2,"result":null}"""))
        val result = LspProbe.probe(Path.of("gopdsdk"), null) { _, _ -> process }

        assertEquals(ProbeResult.Compatible("v1"), result)
        val requests = process.requests.toString(StandardCharsets.UTF_8)
        assertTrue(requests.contains("\"method\":\"shutdown\""))
        assertTrue(requests.contains("\"method\":\"exit\""))
    }

    @Test
    fun `old and capability-incompatible servers are rejected`() {
        val old = LspProbe.probe(Path.of("gopdsdk"), null) { _, _ -> FakeProcess(frames(initialize("v0", true))) }
        val incomplete = LspProbe.probe(Path.of("gopdsdk"), null) { _, _ -> FakeProcess(frames(initialize("v1", false))) }

        assertTrue((old as ProbeResult.Incompatible).message.contains("requires analyzer v1"))
        assertTrue((incomplete as ProbeResult.Incompatible).message.contains("missing required"))
    }

    @Test
    fun `crashing server reports its exit code`() {
        val result = LspProbe.probe(Path.of("gopdsdk"), null) { _, _ -> FakeProcess(ByteArray(0), alive = false, exit = 23) }
        assertTrue((result as ProbeResult.Incompatible).message.contains("exit code 23"))
    }

    @Test
    fun `logs redact credentials and are bounded`() {
        val redacted = Redaction.message("token=abc password=hunter2 " + "x".repeat(3_000))
        assertTrue(redacted.contains("token=<redacted>"))
        assertTrue(redacted.contains("password=<redacted>"))
        assertEquals(2_000, redacted.length)
    }

    private fun executable(path: Path): Path {
        Files.writeString(path, "fixture")
        path.toFile().setExecutable(true)
        return path
    }

    private fun initialize(version: String, complete: Boolean): String {
        val actions = if (complete) "\"codeActionProvider\":true," else ""
        return """{"jsonrpc":"2.0","id":1,"result":{"capabilities":{$actions"diagnosticProvider":{}},"serverInfo":{"name":"fixture","version":"$version"}}}"""
    }

    private fun frames(vararg messages: String): ByteArray = messages.flatMap { message ->
        val body = message.toByteArray(StandardCharsets.UTF_8)
        ("Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII) + body).asIterable()
    }.toByteArray()

    private class FakeProcess(input: ByteArray, private var alive: Boolean = true, private val exit: Int = 0) : Process() {
        val requests = ByteArrayOutputStream()
        private val responses = ByteArrayInputStream(input)
        override fun getOutputStream(): OutputStream = requests
        override fun getInputStream(): InputStream = responses
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int { alive = false; return exit }
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean { alive = false; return true }
        override fun exitValue(): Int = exit
        override fun destroy() { alive = false }
        override fun destroyForcibly(): Process { alive = false; return this }
        override fun isAlive(): Boolean = alive
    }
}

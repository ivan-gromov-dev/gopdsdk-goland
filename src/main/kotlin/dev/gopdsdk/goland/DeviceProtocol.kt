package dev.gopdsdk.goland

import com.google.gson.JsonParser
import java.util.Base64

/** Device contracts are owned by the CLI; discovery alone never establishes connectivity. */
internal enum class DeviceOperation(val command: String, val schema: String, val label: String) {
    BUILD("build device", "gopdsdk-build/v1", "Build for Device"),
    RUN("run device", "gopdsdk-run/v1", "Build, Install and Run on Device"),
    CONNECTION("probe connection", "gopdsdk-probe/v1", "Check Device Connection"),
    CRASHLOG("crashlog", "gopdsdk-device-log/v1", "Read Crash Log"),
    ERRORLOG("errorlog", "gopdsdk-device-log/v1", "Read Error Log"),
    MOUNT("device disk mount", "gopdsdk-device-disk/v1", "Mount Data Disk"),
    UNMOUNT("device disk unmount", "gopdsdk-device-disk/v1", "Safely Eject Data Disk");

    val capability: String get() = if (this == BUILD || this == RUN || this == CONNECTION) command.substringBefore(' ') else command
    fun arguments(sdk: String): List<String> = buildList {
        addAll(command.split(' ')); addAll(listOf("--format", "json"))
        if (this@DeviceOperation != CONNECTION) add("--progress")
        if (sdk.isNotBlank()) addAll(listOf("--sdk", sdk.trim()))
        if (this@DeviceOperation == BUILD || this@DeviceOperation == RUN) add(".")
    }
}

internal fun executeDeviceOperation(
    operation: DeviceOperation,
    sdk: String,
    checkCancelled: () -> Unit,
    run: (List<String>) -> String,
): DeviceResult {
    checkCancelled()
    require(AnalyzerAdministration.supports(run(listOf("capabilities")), operation.capability, operation.schema)) {
        "This gopdsdk does not support ${operation.command}; update gopdsdk"
    }
    checkCancelled()
    val output = run(operation.arguments(sdk))
    checkCancelled()
    return DeviceProtocol.decode(output, operation)
}

internal enum class DeviceConnection { UNCHECKED, CHECKING, CONNECTED, DISK, DISCONNECTED, UNKNOWN }
internal data class DeviceResult(val connection: DeviceConnection? = null, val log: String? = null, val failure: String? = null)

internal fun deviceFailureConnection(previous: DeviceConnection, operation: DeviceOperation, category: String): DeviceConnection = when {
    category == "not-connected" && previous == DeviceConnection.DISK && operation == DeviceOperation.CONNECTION -> DeviceConnection.DISK
    category == "not-connected" -> DeviceConnection.DISCONNECTED
    else -> DeviceConnection.UNKNOWN
}

internal object DeviceProtocol {
    fun decode(text: String, operation: DeviceOperation): DeviceResult {
        val envelope = JsonParser.parseString(text).asJsonObject
        require(envelope["schema"]?.asString == "gopdsdk-tooling-result/v1" && envelope["command"]?.asString == operation.command) { "Unsupported device response; update gopdsdk" }
        if (envelope["ok"]?.asBoolean == false) {
            require(!envelope.has("result"))
            val category = envelope.getAsJsonObject("failure")["category"].asString
            require(category.isNotBlank())
            return DeviceResult(failure = category)
        }
        require(envelope["ok"]?.asBoolean == true && !envelope.has("failure"))
        val result = envelope.getAsJsonObject("result")
        require(result["schema"]?.asString == operation.schema)
        return when (operation) {
            DeviceOperation.CONNECTION -> {
                require(result["probe"]?.asString == "connection" && result["ready"]?.asBoolean == true &&
                    result["discovered"]?.asBoolean == true && result["evidenceLevel"]?.asString == "usb")
                DeviceResult(DeviceConnection.CONNECTED)
            }
            DeviceOperation.BUILD, DeviceOperation.RUN -> {
                require(result["target"]?.asString == "device" && !result["package"]?.asString.isNullOrBlank())
                if (operation == DeviceOperation.BUILD) {
                    require(!result["artifact"]?.asString.isNullOrBlank())
                    DeviceResult()
                } else {
                    require(result["deployment"]?.asString == "installed" && result["execution"]?.asString == "launched")
                    DeviceResult(DeviceConnection.CONNECTED)
                }
            }
            DeviceOperation.MOUNT, DeviceOperation.UNMOUNT -> {
                if (operation == DeviceOperation.MOUNT) {
                    require(result["mode"]?.asString == "disk" && !result["mountPath"]?.asString.isNullOrBlank())
                    DeviceResult(DeviceConnection.DISK)
                } else {
                    require(result["mode"]?.asString == "connected")
                    DeviceResult(DeviceConnection.CONNECTED)
                }
            }
            DeviceOperation.CRASHLOG, DeviceOperation.ERRORLOG -> {
                val metadata = result.getAsJsonObject("metadata")
                val content = result.getAsJsonObject("content")
                require(metadata["kind"]?.asString == "${operation.command}.txt" && !metadata["path"]?.asString.isNullOrBlank())
                require(content["encoding"]?.asString == "base64")
                val encoded = content["data"].asString
                val bytes = Base64.getDecoder().decode(encoded)
                require(bytes.size == metadata["byteCount"].asInt && Base64.getEncoder().encodeToString(bytes) == encoded)
                DeviceResult(DeviceConnection.DISK, String(bytes, Charsets.UTF_8))
            }
        }
    }
}

/** Frames stderr chunks and ignores foreign or stale events without interpreting prose. */
internal class DeviceProgress(private val operation: DeviceOperation, private val update: (String) -> Unit) {
    private val pending = StringBuilder()
    private var sequence = 0
    fun append(text: String) {
        pending.append(text)
        while (true) {
            val end = pending.indexOf("\n")
            if (end < 0) break
            val message = SimulatorProtocol.decode(pending.substring(0, end).trim())
            pending.delete(0, end + 1)
            if (message is ToolMessage.Progress && message.command == operation.command && message.sequence > sequence && message.stage.isNotBlank()) {
                sequence = message.sequence
                update(message.stage.replace('-', ' '))
            }
        }
        require(pending.length <= 1_048_576) { "Device progress line is too large" }
    }
}

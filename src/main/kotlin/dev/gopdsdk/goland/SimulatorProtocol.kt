package dev.gopdsdk.goland

import com.google.gson.JsonParser
import java.nio.file.Path

internal data class BuildLocation(val path: String, val line: Int, val column: Int)
internal data class ToolFailure(val category: String, val locations: List<BuildLocation>)

internal sealed interface ToolMessage {
    data class Progress(val command: String, val sequence: Int, val stage: String) : ToolMessage
    data class Result(val command: String, val ok: Boolean, val artifact: String?, val failure: ToolFailure?) : ToolMessage
}

internal object SimulatorProtocol {
    fun decode(value: String): ToolMessage? = runCatching {
        val json = JsonParser.parseString(value).asJsonObject
        when (json["schema"]?.asString) {
            "gopdsdk-progress/v1" -> ToolMessage.Progress(
                json["command"].asString,
                json["sequence"].asInt,
                json["stage"].asString,
            )
            "gopdsdk-tooling-result/v1" -> {
                val ok = json["ok"].asBoolean
                val result = json.getAsJsonObject("result")
                val failure = json.getAsJsonObject("failure")
                ToolMessage.Result(
                    command = json["command"].asString,
                    ok = ok,
                    artifact = result?.get("artifact")?.asString,
                    failure = failure?.let {
                        ToolFailure(
                            category = it["category"].asString,
                            locations = it.getAsJsonArray("locations")?.map { item ->
                                val location = item.asJsonObject
                                BuildLocation(location["path"].asString, location["line"].asInt, location["column"].asInt).also { parsed ->
                                    require(!Path.of(parsed.path).isAbsolute && !parsed.path.replace('\\', '/').startsWith("../"))
                                    require(parsed.line > 0 && parsed.column > 0)
                                }
                            }.orEmpty(),
                        )
                    },
                )
            }
            else -> null
        }
    }.getOrNull()
}

internal fun simulatorArguments(
    operation: SimulatorOperation,
    packagePath: String,
    sdkPath: String,
    force: Boolean,
): List<String> = buildList {
    add(if (operation == SimulatorOperation.BUILD) "build" else "run")
    addAll(listOf("--format", "json", "--progress"))
    if (force && operation == SimulatorOperation.BUILD) add("--force")
    if (sdkPath.isNotBlank()) addAll(listOf("--sdk", sdkPath.trim()))
    add(packagePath.ifBlank { "." })
}

package dev.gopdsdk.goland

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

internal enum class HealthStatus { READY, MISSING, INCOMPATIBLE, UNVERIFIED }

internal data class HealthRemediation(val action: String, val value: String? = null)
internal data class HealthCheck(
    val id: String,
    val status: HealthStatus,
    val discovered: Boolean,
    val evidenceLevel: String,
    val remediation: HealthRemediation? = null,
)
internal data class HealthReport(val host: String, val sdkVersion: String?, val checks: List<HealthCheck>, val raw: String)

internal object HealthProtocol {
    fun decodeDoctor(value: String): HealthReport? = runCatching {
        val envelope = JsonParser.parseString(value).asJsonObject
        require(envelope.string("schema") == "gopdsdk-tooling-result/v1")
        require(envelope.string("command") == "doctor" && envelope["ok"].asBoolean)
        val result = envelope.getAsJsonObject("result")
        require(result.string("schema") == "gopdsdk-doctor/v1")
        val checks = result.getAsJsonArray("checks").map { item ->
            val check = item.asJsonObject
            val status = when (check.string("status")) {
                "ready" -> HealthStatus.READY
                "missing" -> HealthStatus.MISSING
                "incompatible" -> HealthStatus.INCOMPATIBLE
                "unverified" -> HealthStatus.UNVERIFIED
                else -> error("unknown health status")
            }
            val remediation = check.getAsJsonObject("remediation")?.let {
                HealthRemediation(it.string("action"), it["value"]?.asString)
            }
            HealthCheck(check.string("id"), status, check["discovered"].asBoolean, check.string("evidenceLevel"), remediation)
        }
        HealthReport(result.string("host"), result.getAsJsonObject("sdk")?.get("version")?.asString, checks, value)
    }.getOrNull()

    fun decodeProbe(value: String): HealthCheck? = runCatching {
        val envelope = JsonParser.parseString(value).asJsonObject
        require(envelope.string("schema") == "gopdsdk-tooling-result/v1")
        if (!envelope["ok"].asBoolean) {
            val failure = envelope.getAsJsonObject("failure")
            val remediation = failure.getAsJsonObject("remediation")?.let { HealthRemediation(it.string("action"), it["value"]?.asString) }
            return@runCatching HealthCheck(envelope.string("command").removePrefix("probe "), HealthStatus.MISSING, false, "none", remediation)
        }
        val result = envelope.getAsJsonObject("result")
        require(result.string("schema") == "gopdsdk-probe/v1")
        HealthCheck(
            result.string("probe"),
            if (result["ready"].asBoolean) HealthStatus.READY else HealthStatus.UNVERIFIED,
            result["discovered"].asBoolean,
            result.string("evidenceLevel"),
        )
    }.getOrNull()

    private fun JsonObject.string(name: String): String = get(name)?.asString ?: error("missing $name")
}

internal fun doctorArguments(sdkPath: String): List<String> = buildList {
    addAll(listOf("doctor", "--format", "json"))
    if (sdkPath.isNotBlank()) addAll(listOf("--sdk", sdkPath.trim()))
}

internal fun probeArguments(id: String, sdkPath: String): List<String>? = when (id) {
    "simulator" -> listOf("probe", "simulator", "--format", "json")
    "device-build", "device" -> listOf("probe", "device", "--format", "json")
    "device-deploy", "connection" -> listOf("probe", "connection", "--format", "json")
    else -> null
}?.let { arguments -> if (sdkPath.isBlank()) arguments else arguments + listOf("--sdk", sdkPath.trim()) }

internal fun localProjectChecks(root: Path, executableReady: Boolean, analyzerReady: Boolean): List<HealthCheck> {
    fun file(id: String, path: String, action: String) = HealthCheck(
        id,
        if (Files.isRegularFile(root.resolve(path))) HealthStatus.READY else HealthStatus.MISSING,
        Files.isRegularFile(root.resolve(path)),
        "project",
        if (Files.isRegularFile(root.resolve(path))) null else HealthRemediation(action, path),
    )
    return listOf(
        HealthCheck("gopdsdk", if (executableReady) HealthStatus.READY else HealthStatus.MISSING, executableReady, "discovery", if (executableReady) null else HealthRemediation("configure-gopdsdk")),
        HealthCheck("analyzer-protocol", if (analyzerReady) HealthStatus.READY else HealthStatus.INCOMPATIBLE, analyzerReady, "protocol", if (analyzerReady) null else HealthRemediation("configure-gopdsdk")),
        file("module", "go.mod", "create-go-module"),
        file("manifest", "pdxinfo", "create-manifest"),
        file("analyzer-configuration", ".gopdsdk-check.json", "create-analyzer-configuration"),
    )
}

internal fun mergeHealthChecks(doctor: List<HealthCheck>, local: List<HealthCheck>, probe: HealthCheck? = null): List<HealthCheck> {
    val result = linkedMapOf<String, HealthCheck>()
    (local + doctor).forEach { result[it.id] = it }
    if (probe != null) result[when (probe.id) { "device" -> "device-build"; "connection" -> "device-deploy"; else -> probe.id }] = probe
    return result.values.toList()
}

@Service(Service.Level.PROJECT)
internal class ProjectHealthService(private val project: Project) {
    @Volatile var report: HealthReport? = null
        private set

    fun refresh(): HealthReport {
        val settings = GopdsdkSettings.getInstance(project).state
        val executable = ExecutableDiscovery.find(settings.executablePath)
        val root = Path.of(project.basePath ?: ".")
        val analyzerReady = executable?.let { LspProbe.probe(it, project.basePath) is ProbeResult.Compatible } == true
        val local = localProjectChecks(root, executable != null, analyzerReady)
        if (executable == null) return HealthReport("", null, local, "gopdsdk executable not found").also { report = it }
        val command = GeneralCommandLine(executable.toString()).withParameters(doctorArguments(settings.playdateSDK)).withWorkDirectory(project.basePath)
        val output = CapturingProcessHandler(command).runProcess(30_000)
        val decoded = HealthProtocol.decodeDoctor(output.stdout)
        return (decoded?.copy(checks = mergeHealthChecks(decoded.checks, local), raw = output.stdout + output.stderr)
            ?: HealthReport("", null, local, output.stdout + output.stderr)).also { report = it }
    }

    fun probe(id: String): HealthReport {
        val current = report ?: refresh()
        val settings = GopdsdkSettings.getInstance(project).state
        val executable = ExecutableDiscovery.find(settings.executablePath) ?: return current
        val arguments = probeArguments(id, settings.playdateSDK) ?: return current
        val output = CapturingProcessHandler(
            GeneralCommandLine(executable.toString()).withParameters(arguments).withWorkDirectory(project.basePath),
        ).runProcess(60_000)
        val probe = HealthProtocol.decodeProbe(output.stdout)
        return current.copy(
            checks = mergeHealthChecks(current.checks, emptyList(), probe),
            raw = current.raw + "\n\n$ ${arguments.joinToString(" ")}\n" + output.stdout + output.stderr,
        ).also { report = it }
    }

    companion object { fun getInstance(project: Project): ProjectHealthService = project.getService(ProjectHealthService::class.java) }
}

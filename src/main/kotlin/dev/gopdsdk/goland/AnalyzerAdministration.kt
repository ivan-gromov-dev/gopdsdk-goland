package dev.gopdsdk.goland

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

/** Module-scoped analyzer administration; all rule semantics and baseline identities belong to gopdsdk. */
internal object AnalyzerAdministration {
    val targets = listOf("both", "simulator", "device")
    val profiles = listOf("default", "experimental", "deep")
    val severities = listOf("error", "warning", "performance", "information")
    const val configName = ".gopdsdk-check.json"

    fun configuration(text: String): JsonObject = if (text.isBlank()) {
        JsonObject().apply { addProperty("schema", "gopdsdk-check-config/v1") }
    } else JsonParser.parseString(text).asJsonObject.also {
        require(it["schema"]?.asString == "gopdsdk-check-config/v1") { "Unsupported analyzer configuration schema" }
    }

    fun encode(value: JsonObject): String = GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n"

    fun select(value: JsonObject, target: String, profile: String): JsonObject {
        require(target in targets && profile in profiles)
        return value.deepCopy().apply { addProperty("target", target); addProperty("profile", profile) }
    }

    fun rule(value: JsonObject, id: String, mode: String, severity: String): JsonObject {
        require(mode in listOf("inherit", "enable", "exclude"))
        require(severity == "inherit" || severity in severities)
        return value.deepCopy().apply {
            for (field in listOf("rules", "excludeRules")) {
                val entries = getAsJsonArray(field)?.map { it.asString }.orEmpty().filter { it != id }.toMutableList()
                if ((field == "rules" && mode == "enable") || (field == "excludeRules" && mode == "exclude")) entries.add(id)
                if (has(field) || entries.isNotEmpty()) add(field, com.google.gson.Gson().toJsonTree(entries.distinct().sorted()))
            }
            val overrides = getAsJsonObject("severities")?.deepCopy() ?: JsonObject()
            if (severity == "inherit") overrides.remove(id) else overrides.addProperty(id, severity)
            add("severities", overrides)
        }
    }

    fun result(text: String, command: String, schema: String): JsonObject {
        val envelope = JsonParser.parseString(text).asJsonObject
        require(envelope["schema"]?.asString == "gopdsdk-tooling-result/v1" && envelope["command"]?.asString == command) { "Unsupported tooling response" }
        require(envelope["ok"]?.asBoolean == true) { "gopdsdk operation failed" }
        return envelope.getAsJsonObject("result").also {
            require(it["schema"]?.asString == schema) { "Unsupported $command schema; update gopdsdk" }
        }
    }

    fun supports(text: String, command: String, schema: String): Boolean =
        result(text, "capabilities", "gopdsdk-tooling-capabilities/v1").getAsJsonArray("commands").any {
            val item = it.asJsonObject
            item["name"]?.asString == command && item.getAsJsonArray("modes").any { mode -> mode.asString == "json" } &&
                item.getAsJsonArray("resultSchemas").any { entry -> entry.asString == schema }
        }

    fun catalog(text: String): AnalyzerCatalog {
        val inventory = result(text, "rules", "gopdsdk-analyzer-contracts/v1")
        val contracts = inventory.getAsJsonArray("contracts").associate { it.asJsonObject["id"].asString to it.asJsonObject }
        val rules = inventory.getAsJsonArray("rules").map {
            val rule = it.asJsonObject
            AnalyzerRule(rule["id"].asString, rule["family"].asString, rule["summary"].asString,
                rule["defaultSeverity"].asString, rule["suppressible"].asBoolean,
                "${rule["summary"].asString}\n\nCategory: ${rule["family"].asString}\nDefault severity: ${rule["defaultSeverity"].asString}\n" +
                    "Confidence: ${rule["confidence"].asString}\nTargets: ${rule.getAsJsonArray("targets").joinToString { target -> target.asString }}\n" +
                    "Suppressible: ${rule["suppressible"].asBoolean}\nSafe fixes: ${rule["safeFixPolicy"].asString}\n\n" +
                    rule.getAsJsonArray("contractIds").joinToString("\n\n") { id ->
                        val contract = requireNotNull(contracts[id.asString])
                        "${contract["subject"].asString}\n${contract["statement"].asString}\n" +
                            "Valid: ${contract["positiveCase"].asString}\nInvalid: ${contract["negativeCase"].asString}\nReference: ${contract["normativeRef"].asString}"
                    })
        }
        require(rules.map { it.id }.distinct().size == rules.size)
        require(rules.all { it.severity in severities })
        return AnalyzerCatalog(rules)
    }

    fun report(text: String): AnalyzerReport {
        val report = JsonParser.parseString(text).asJsonObject
        require(report["schema"]?.asString == "gopdsdk-check/v1") { "Unsupported check schema" }
        return AnalyzerReport(report["analyzerVersion"].asString, report["sdkVersion"].asString,
            report.getAsJsonArray("diagnostics").map {
                val item = it.asJsonObject
                val location = item.getAsJsonObject("primary")
                val start = location.getAsJsonObject("start")
                val path = location["path"].asString
                relativePath(path)
                val line = start["line"].asInt
                val column = start["column"].asInt
                require(line > 0 && column > 0)
                val target = item["target"].asString
                require(target in listOf("shared", "simulator", "device"))
                AnalyzerFinding(item["rule"].asString, target, item["severity"].asString, path, line, column,
                    item["message"].asString, item["documentation"]?.asString, item.has("suppression"))
            }, text)
    }

    fun baselineResult(text: String, operation: String): JsonObject =
        result(text, "baseline $operation", "gopdsdk-baseline-result/v1").also {
            require(it["operation"]?.asString == operation && it["entries"].asInt >= 0)
            relativePath(it["path"].asString)
            require(it["staleEntries"].isJsonArray)
        }

    fun checkArguments(target: String? = null): List<String> {
        require(target == null || target in targets || target == "shared")
        return listOf("check", "--format", "json", "--fail-on", "none", "--baseline", "") +
            if (target == null) emptyList() else listOf("--target", target)
    }

    // Match VS Code synchronizeLSPConfiguration, including its experimental-profile expansion.
    fun lspSettings(value: JsonObject, previous: GopdsdkSettings.State, catalogRuleIds: List<String>? = null): GopdsdkSettings.State {
        fun strings(name: String) = value.getAsJsonArray(name)?.map { it.asString }?.toMutableList() ?: mutableListOf()
        val profile = value["profile"]?.asString ?: "default"
        require(profile in profiles)
        require(profile != "experimental" || catalogRuleIds != null) { "Load the analyzer catalog before enabling the experimental profile" }
        return previous.copy(
            target = value["target"]?.asString ?: "both",
            rules = if (profile == "experimental") requireNotNull(catalogRuleIds).toMutableList() else strings("rules"),
            categories = strings("categories"), excludeRules = strings("excludeRules"),
            severities = value.getAsJsonObject("severities")?.entrySet()?.associate { it.key to it.value.asString }?.toMutableMap() ?: linkedMapOf(),
            baseline = value["baseline"]?.asString ?: "", changedFiles = strings("changedFiles"), deep = profile == "deep",
        )
    }

    fun compare(reports: List<AnalyzerReport>): AnalyzerReport {
        require(reports.isNotEmpty())
        require(reports.all { it.analyzerVersion == reports.first().analyzerVersion && it.sdkVersion == reports.first().sdkVersion }) { "Analyzer versions changed during comparison" }
        val findings = reports.flatMap { it.findings }.distinctBy { listOf(it.target, it.rule, it.path, it.line, it.column, it.message) }
            .sortedWith(compareBy({ it.path }, { it.line }, { it.rule }, { it.target }))
        return reports.first().copy(findings = findings, raw = reports.joinToString("\n") { it.raw })
    }

    fun relativePath(name: String): Path {
        require(name.isNotBlank() && !name.contains('\\') && !name.contains(':')) { "Use a module-relative slash path" }
        val path = Path.of(name)
        require(!path.isAbsolute && path.none { it.toString() == ".." }) { "Path must stay inside the selected module" }
        return path
    }

    fun contained(root: Path, name: String): Path {
        val base = root.toRealPath()
        val path = base.resolve(relativePath(name)).normalize()
        var existing = path
        while (!Files.exists(existing)) existing = requireNotNull(existing.parent)
        require(existing.toRealPath().startsWith(base)) { "Path resolves outside the selected module" }
        return path
    }

    fun moduleRoot(start: Path): Path? = generateSequence(if (Files.isDirectory(start)) start else start.parent) { it.parent }
        .firstOrNull { Files.isRegularFile(it.resolve("go.mod")) }

    fun suppression(text: String, line: Int, rule: String, reason: String, expected: String): Pair<Int, String> {
        require(text == expected) { "Source changed since analysis; run Compare findings again" }
        require(Regex("[a-z0-9]+(?:-[a-z0-9]+)*").matches(rule))
        require(reason.isNotBlank() && !reason.contains('\r') && !reason.contains('\n')) { "A single-line reason is required" }
        val lines = text.split('\n')
        require(line in 1..lines.size && lines[line - 1].isNotBlank()) { "Stale diagnostic location" }
        val offset = lines.take(line - 1).sumOf { it.length + 1 }
        val indent = lines[line - 1].takeWhile { it == ' ' || it == '\t' }
        val eol = if (text.contains("\r\n")) "\r\n" else "\n"
        return offset to "$indent//gopdsdk:ignore $rule -- ${reason.trim()}$eol"
    }
}

internal data class AnalyzerRule(val id: String, val family: String, val summary: String, val severity: String, val suppressible: Boolean, val help: String) {
    override fun toString(): String = "$id — $summary"
}
internal data class AnalyzerCatalog(val rules: List<AnalyzerRule>)
internal data class AnalyzerFinding(val rule: String, val target: String, val severity: String, val path: String, val line: Int, val column: Int,
    val message: String, val documentation: String?, val suppressed: Boolean)
internal data class AnalyzerReport(val analyzerVersion: String, val sdkVersion: String, val findings: List<AnalyzerFinding>, val raw: String)

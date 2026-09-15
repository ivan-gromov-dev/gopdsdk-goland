package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AnalyzerAdministrationTest {
    @TempDir lateinit var root: Path

    @Test fun `configuration round trips unrelated fields and explicit selections`() {
        val original = AnalyzerAdministration.configuration("""{"schema":"gopdsdk-check-config/v1","patterns":["./game/..."],"tests":false,"buildTags":["release"],"rules":["first-rule"],"excludeRules":["second-rule"],"severities":{"ownership":"warning"}}""")
        val selected = AnalyzerAdministration.select(original, "device", "experimental")
        val edited = AnalyzerAdministration.rule(selected, "second-rule", "enable", "error")
        val roundTrip = AnalyzerAdministration.configuration(AnalyzerAdministration.encode(edited))
        assertEquals(edited, roundTrip)
        assertEquals(original["patterns"], edited["patterns"])
        assertEquals(original["buildTags"], edited["buildTags"])
        assertFalse(edited["tests"].asBoolean)
        assertEquals(listOf("first-rule", "second-rule"), edited.getAsJsonArray("rules").map { it.asString })
        assertEquals(0, edited.getAsJsonArray("excludeRules").size())
        assertEquals("warning", edited.getAsJsonObject("severities")["ownership"].asString)
        assertEquals("error", edited.getAsJsonObject("severities")["second-rule"].asString)
        assertEquals("error", roundTrip.getAsJsonObject("severities")["second-rule"].asString)
        assertFalse(original.has("target"))
        assertFalse(AnalyzerAdministration.rule(edited, "second-rule", "inherit", "inherit").getAsJsonObject("severities").has("second-rule"))
    }

    @Test fun `unknown configuration schema and invalid choices are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.configuration("""{"schema":"future"}""") }
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.select(AnalyzerAdministration.configuration(""), "shared", "default") }
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.rule(AnalyzerAdministration.configuration(""), "rule", "exclude", "loud") }
    }

    @Test fun `suppression is reasoned and rejects stale source`() {
        val source = "package game\r\n\twork()\r\n"
        val (offset, text) = AnalyzerAdministration.suppression(source, 2, "test-rule", " accepted debt ", source)
        assertEquals("package game\r\n".length, offset)
        assertEquals("\t//gopdsdk:ignore test-rule -- accepted debt\r\n", text)
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.suppression(source + " ", 2, "test-rule", "reason", source) }
        for (reason in listOf("", "  ", "reason\ncode", "reason\rcode")) {
            assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.suppression(source, 2, "test-rule", reason, source) }
        }
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.suppression(source, 20, "test-rule", "reason", source) }
    }

    @Test fun `module selection isolates nested and sibling modules`() {
        Files.writeString(root.resolve("go.mod"), "module parent")
        val nested = Files.createDirectories(root.resolve("nested"))
        Files.writeString(nested.resolve("go.mod"), "module nested")
        val source = Files.writeString(nested.resolve("game.go"), "package game")
        assertEquals(nested, AnalyzerAdministration.moduleRoot(source))
        assertEquals(root, AnalyzerAdministration.moduleRoot(root))
        assertEquals(root.toRealPath().resolve("baseline.json"), AnalyzerAdministration.contained(root, "baseline.json"))
    }

    @Test fun `all host path escape forms are rejected`() {
        for (name in listOf("../other.json", "dir/../../other.json", "/tmp/file", "C:/file", "C:\\file", "\\\\host\\share", "")) {
            assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.contained(root, name) }
        }
        assertEquals(root.toRealPath().resolve("baseline.json"), AnalyzerAdministration.contained(root, "baseline.json"))
    }

    @Test fun `capabilities require advertised command mode and schema`() {
        val text = envelope("capabilities", """{"schema":"gopdsdk-tooling-capabilities/v1","commands":[{"name":"rules","modes":["json"],"resultSchemas":["gopdsdk-analyzer-contracts/v1"]}]}""")
        assertTrue(AnalyzerAdministration.supports(text, "rules", "gopdsdk-analyzer-contracts/v1"))
        assertFalse(AnalyzerAdministration.supports(text, "baseline", "gopdsdk-baseline-result/v1"))
        assertFalse(AnalyzerAdministration.supports(text, "rules", "future"))
    }

    @Test fun `catalog help is obtained from the executing analyzer`() {
        val text = envelope("rules", """{"schema":"gopdsdk-analyzer-contracts/v1","contracts":[{"id":"contract","subject":"Subject","statement":"Exact contract","positiveCase":"Good","negativeCase":"Bad","normativeRef":"API.md#contract"}],"rules":[{"id":"test-rule","family":"test","summary":"Summary","defaultSeverity":"warning","confidence":"proven","targets":["shared"],"suppressible":true,"safeFixPolicy":"none","contractIds":["contract"]}]}""")
        val rule = AnalyzerAdministration.catalog(text).rules.single()
        assertEquals("test", rule.family)
        assertTrue(rule.help.contains("Exact contract"))
        assertTrue(rule.suppressible)
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.catalog(text.replace("gopdsdk-analyzer-contracts/v1", "future")) }
    }

    @Test fun `baseline results retain stale entries and reject mismatched operation`() {
        val result = envelope("baseline validate", """{"schema":"gopdsdk-baseline-result/v1","operation":"validate","path":"baseline.json","entries":1,"staleEntries":[{"rule":"test-rule"}],"future":true}""")
        assertEquals(1, AnalyzerAdministration.baselineResult(result, "validate").getAsJsonArray("staleEntries").size())
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.baselineResult(result, "update") }
    }

    @Test fun `comparison consumes CLI targets without recategorizing diagnostics`() {
        val diagnostics = listOf("shared", "simulator", "device").joinToString(",") { target ->
            """{"rule":"test-rule","target":"$target","severity":"warning","message":"finding","primary":{"path":"game.go","start":{"line":2,"column":1}}}"""
        }
        val report = """{"schema":"gopdsdk-check/v1","analyzerVersion":"v1","sdkVersion":"v1.0.0","diagnostics":[$diagnostics]}"""
        assertEquals(listOf("shared", "simulator", "device"), AnalyzerAdministration.report(report).findings.map { it.target })
        assertEquals(listOf("check", "--format", "json", "--fail-on", "none", "--baseline", "", "--target", "both"), AnalyzerAdministration.checkArguments("both"))
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.report(report.replace("game.go", "../other/game.go")) }
        val parsed = AnalyzerAdministration.report(report)
        assertEquals(3, AnalyzerAdministration.compare(listOf(parsed, parsed)).findings.size)
        assertEquals("shared", AnalyzerAdministration.checkArguments("shared").last())
        assertThrows(IllegalArgumentException::class.java) { AnalyzerAdministration.compare(listOf(parsed, parsed.copy(analyzerVersion = "other"))) }
    }

    @Test fun `LSP projection matches VS Code for every profile and preserves compatibility floors`() {
        val previous = GopdsdkSettings.State(gopdsdkFloor = "v1.0.0", playdateSDK = "3.0.0")
        val config = AnalyzerAdministration.configuration("""{"schema":"gopdsdk-check-config/v1","target":"device","rules":["first-rule"],"categories":["ownership"],"excludeRules":["second-rule"],"severities":{"first-rule":"error"},"baseline":"baseline.json","changedFiles":["game.go"]}""")
        for (profile in AnalyzerAdministration.profiles) {
            val selected = AnalyzerAdministration.select(config, "device", profile)
            val state = AnalyzerAdministration.lspSettings(selected, previous, listOf("first-rule", "second-rule"))
            assertEquals("device", state.target)
            assertEquals(if (profile == "experimental") listOf("first-rule", "second-rule") else listOf("first-rule"), state.rules)
            assertEquals(profile == "deep", state.deep)
            assertEquals(listOf("ownership"), state.categories)
            assertEquals(listOf("second-rule"), state.excludeRules)
            assertEquals(mapOf("first-rule" to "error"), state.severities)
            assertEquals("baseline.json", state.baseline)
            assertEquals(listOf("game.go"), state.changedFiles)
            assertEquals(previous.gopdsdkFloor, state.gopdsdkFloor)
            assertEquals(previous.playdateSDK, state.playdateSDK)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnalyzerAdministration.lspSettings(AnalyzerAdministration.select(config, "both", "experimental"), previous)
        }
    }

    @Test fun `module LSP settings survive reload without leaking to siblings`() {
        val modules = AnalyzerModuleSettings()
        val first = root.resolve("first")
        val second = root.resolve("second")
        val fallback = GopdsdkSettings.State()
        modules.put(first, fallback.copy(target = "device", deep = true))
        val restored = AnalyzerModuleSettings()
        restored.loadState(modules.state)
        assertEquals("device", restored.forRoot(first, fallback).target)
        assertTrue(restored.forRoot(first, fallback).deep)
        assertEquals(fallback, restored.forRoot(second, fallback))
    }

    private fun envelope(command: String, result: String) = """{"schema":"gopdsdk-tooling-result/v1","command":"$command","ok":true,"result":$result}"""
}

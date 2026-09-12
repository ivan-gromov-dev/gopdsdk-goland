package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AnalyzerConfigurationTest {
    @Test
    fun `defaults match analyzer protocol`() {
        assertEquals(
            AnalyzerSettings("both", "", "", emptyList(), emptyList(), emptyList(), emptyMap(), "", emptyList(), false),
            GopdsdkSettings.State().analyzerSettings(),
        )
    }

    @Test
    fun `settings normalize lists paths and severity selectors`() {
        val settings = GopdsdkSettings.State(
            target = "device",
            gopdsdkFloor = " v1.2.3 ",
            playdateSDK = " 3.1.1 ",
            rules = mutableListOf(" ownership ", "", "ownership"),
            categories = mutableListOf(" performance "),
            excludeRules = mutableListOf("device-goroutine"),
            severities = linkedMapOf(" ownership " to "information"),
            baseline = " .gopdsdk-baseline.json ",
            changedFiles = mutableListOf(" game.go ", ""),
            deep = true,
        ).analyzerSettings()

        assertEquals("device", settings.target)
        assertEquals("v1.2.3", settings.gopdsdkFloor)
        assertEquals("3.1.1", settings.playdateSDK)
        assertEquals(listOf("ownership"), settings.rules)
        assertEquals(listOf("performance"), settings.categories)
        assertEquals(mapOf("ownership" to "information"), settings.severities)
        assertEquals(".gopdsdk-baseline.json", settings.baseline)
        assertEquals(listOf("game.go"), settings.changedFiles)
        assertEquals(true, settings.deep)
    }

    @Test
    fun `editor parsers reject incomplete severity entries`() {
        assertEquals(mutableListOf("one", "two"), commaSeparated(" one, , two, one "))
        assertEquals(
            mapOf("ownership" to "warning"),
            severityOverrides("ownership=warning, missing, other=loud"),
        )
        assertFalse(severityOverrides("missing").containsKey("missing"))
        assertEquals("missing", invalidSeverityOverride("ownership=warning, missing"))
        assertEquals(null, invalidSeverityOverride("ownership=warning, workspace=information"))
    }
}

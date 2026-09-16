package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlaydateOverviewTest {
    @TempDir lateinit var directory: Path
    private val report = HealthReport("windows", "3.1.1", listOf(
        HealthCheck("simulator", HealthStatus.UNVERIFIED, true, "discovery")), "raw", "v1")

    @Test fun `selection resolves nearest module including nested applications`() {
        Files.writeString(directory.resolve("go.mod"), "module outer")
        val nested = Files.createDirectories(directory.resolve("games/nested"))
        Files.writeString(nested.resolve("go.mod"), "module inner")
        val source = Files.writeString(nested.resolve("main.go"), "package main")
        assertEquals(nested, AnalyzerAdministration.moduleRoot(source))
        assertEquals(nested, AnalyzerAdministration.moduleRoot(nested))
        assertEquals(directory, AnalyzerAdministration.moduleRoot(directory.resolve("main.go")))
        assertEquals(source, buildSourcePath(nested, "main.go"))
        assertNotEquals(directory.resolve("main.go"), buildSourcePath(nested, "main.go"))
    }

    @Test fun `late results never cross module selection or overwrite newer refresh`() {
        val health = OverviewHealth()
        val first = directory.resolve("one")
        val second = directory.resolve("two")
        val token = health.begin(first)
        health.select(second)
        health.finish(first, token, report, null)
        assertNull(health.report)
        assertFalse(health.loading)
        health.select(first)
        val next = health.begin(first)
        health.finish(first, token, report, null)
        assertTrue(health.loading)
        health.finish(first, next, report, null)
        assertEquals(report, health.report)
    }

    @Test fun `empty loading failure cancellation and retry remain explicit`() {
        val health = OverviewHealth()
        fun text(root: Path?) = overviewText("Game", root, "device", health, "Simulator: build complete", "Device: unchecked", null)
        assertTrue(text(null).contains("No Go module selected"))
        val token = health.begin(directory)
        assertTrue(text(directory).contains("Health: loading"))
        health.finish(directory, token, null, "Cancelled; refresh to retry")
        assertTrue(text(directory).contains("Health: Cancelled"))
        val retry = health.begin(directory)
        health.finish(directory, retry, null, "Unsupported health response")
        assertTrue(text(directory).contains("Health: Unsupported health response"))
        val success = health.begin(directory)
        health.finish(directory, success, report, null)
        val result = text(directory)
        assertTrue(result.contains("simulator: unverified (discovery)"))
        assertTrue(result.contains("analyzer protocol: v1"))
        assertTrue(result.contains("Playdate SDK: 3.1.1"))
        assertTrue(result.contains("Analysis target: device"))
        assertTrue(result.contains("Device: unchecked"))
        assertFalse(result.contains("Health: Unsupported"))
    }

    @Test fun `overview reuses registered actions and labels Problems scope`() {
        val xml = javaClass.getResource("/META-INF/plugin.xml")!!.readText()
        overviewActionIds.forEach { assertTrue(xml.contains("id=\"gopdsdk.$it\""), it) }
        val value = overviewText("Game", directory, "both", OverviewHealth(), "Simulator", "Device: disk", 7)
        assertTrue(value.contains("Module Problems (all IDE sources): 7"))
        assertTrue(value.contains("Device: disk"))
        assertTrue(value.contains("release version: not exposed"))
    }
}

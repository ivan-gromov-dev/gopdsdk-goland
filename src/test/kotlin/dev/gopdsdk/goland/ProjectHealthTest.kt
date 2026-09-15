package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class ProjectHealthTest {
    @Test fun `doctor envelope maps every stable health state and ignores additions`() {
        val report = HealthProtocol.decodeDoctor("""{
          "schema":"gopdsdk-tooling-result/v1","command":"doctor","ok":true,"future":true,
          "result":{"schema":"gopdsdk-doctor/v1","host":"windows/amd64","sdk":{"version":"3.1.1"},"checks":[
            {"id":"sdk","discovered":true,"status":"ready","evidenceLevel":"discovery"},
            {"id":"simulator","discovered":true,"status":"unverified","evidenceLevel":"discovery","remediation":{"action":"run-doctor-probe","value":"simulator"}},
            {"id":"device-build","discovered":false,"status":"missing","evidenceLevel":"discovery"},
            {"id":"develop","discovered":true,"status":"incompatible","evidenceLevel":"discovery"}
          ]}}
        """)!!
        assertEquals("3.1.1", report.sdkVersion)
        assertEquals(listOf(HealthStatus.READY, HealthStatus.UNVERIFIED, HealthStatus.MISSING, HealthStatus.INCOMPATIBLE), report.checks.map { it.status })
        assertEquals("run-doctor-probe", report.checks[1].remediation?.action)
    }

    @Test fun `unknown schemas malformed states and prose are rejected`() {
        assertNull(HealthProtocol.decodeDoctor("not json"))
        assertNull(HealthProtocol.decodeDoctor("""{"schema":"gopdsdk-tooling-result/v2","command":"doctor","ok":true,"result":{}}"""))
        assertNull(HealthProtocol.decodeDoctor("""{"schema":"gopdsdk-tooling-result/v1","command":"doctor","ok":true,"result":{"schema":"gopdsdk-doctor/v1","host":"x","checks":[{"id":"sdk","discovered":true,"status":"future","evidenceLevel":"x"}]}}"""))
    }

    @Test fun `probe success and failure remain structured`() {
        val success = HealthProtocol.decodeProbe("""{"schema":"gopdsdk-tooling-result/v1","command":"probe simulator","ok":true,"result":{"schema":"gopdsdk-probe/v1","probe":"simulator","discovered":true,"ready":true,"evidenceLevel":"sdk-integration","values":[]}}""")!!
        assertEquals(HealthStatus.READY, success.status)
        val failure = HealthProtocol.decodeProbe("""{"schema":"gopdsdk-tooling-result/v1","command":"probe connection","ok":false,"failure":{"category":"probe-failed","remediation":{"action":"check-device-connection"}}}""")!!
        assertEquals(HealthStatus.MISSING, failure.status)
        assertEquals("check-device-connection", failure.remediation?.action)
    }

    @Test fun `project checks distinguish all local prerequisites`() {
        val root = Files.createTempDirectory("gopdsdk-health")
        Files.writeString(root.resolve("go.mod"), "module example.com/game")
        Files.writeString(root.resolve("pdxinfo"), "name=Game")
        val checks = localProjectChecks(root, executableReady = true, analyzerReady = false).associateBy { it.id }
        assertEquals(HealthStatus.READY, checks.getValue("module").status)
        assertEquals(HealthStatus.READY, checks.getValue("manifest").status)
        assertEquals(HealthStatus.MISSING, checks.getValue("analyzer-configuration").status)
        assertEquals(HealthStatus.INCOMPATIBLE, checks.getValue("analyzer-protocol").status)
    }

    @Test fun `commands preserve structured arguments and optional values`() {
        assertEquals(listOf("doctor", "--format", "json", "--sdk", "C:/SDK"), doctorArguments(" C:/SDK "))
        assertEquals(listOf("probe", "connection", "--format", "json"), probeArguments("device-deploy", ""))
        assertEquals(listOf("init", "--module", "example.com/game", "--name", "Game", "/tmp/game"), initArguments(NewGameRequest("/tmp/game", " example.com/game ", "Game", "", "")))
        assertTrue(probeArguments("module", "") == null)
    }

    @Test fun `creation distinguishes success failure and cancellation without parsing logs`() {
        val request = NewGameRequest("/tmp/game", "example.com/game", "", "", "")
        assertEquals(CreationOutcome.Created("/tmp/game"), creationOutcome(request, 0, "arbitrary output", false))
        assertEquals(CreationOutcome.Cancelled, creationOutcome(request, 130, "cancelled", true))
        val failure = creationOutcome(request, 2, "token=secret failure", false) as CreationOutcome.Failed
        assertTrue(failure.message.contains("<redacted>"))
    }
}

package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimulatorProtocolTest {
    @Test fun `decodes ordered progress`() {
        assertEquals(ToolMessage.Progress("build", 2, "compilation"), SimulatorProtocol.decode(
            """{"schema":"gopdsdk-progress/v1","command":"build","sequence":2,"stage":"compilation"}""",
        ))
    }

    @Test fun `decodes build failure locations and additive fields`() {
        val message = SimulatorProtocol.decode("""{
          "schema":"gopdsdk-tooling-result/v1","command":"build","ok":false,"future":true,
          "failure":{"category":"compilation-failed","locations":[{"path":"game.go","line":7,"column":3}]}
        }""") as ToolMessage.Result
        assertEquals("compilation-failed", message.failure?.category)
        assertEquals(BuildLocation("game.go", 7, 3), message.failure?.locations?.single())
    }

    @Test fun `rejects malformed and unknown protocol messages`() {
        assertNull(SimulatorProtocol.decode("not json"))
        assertNull(SimulatorProtocol.decode("""{"schema":"gopdsdk-progress/v2"}"""))
        assertNull(SimulatorProtocol.decode("""{"schema":"gopdsdk-tooling-result/v1","command":"build","ok":false,"failure":{"category":"failed","locations":[{"path":"../secret.go","line":1,"column":1}]}}"""))
    }

    @Test fun `build and run commands use structured cancellable contract`() {
        assertEquals(listOf("build", "--format", "json", "--progress", "--force", "--sdk", "C:/SDK", "./game"),
            simulatorArguments(SimulatorOperation.BUILD, "./game", " C:/SDK ", true))
        val run = simulatorArguments(SimulatorOperation.RUN, ".", "", true)
        assertEquals(listOf("run", "--format", "json", "--progress", "."), run)
        assertTrue("--force" !in run)
    }
}

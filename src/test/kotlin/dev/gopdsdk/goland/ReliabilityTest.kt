package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class ReliabilityTest {
    @Test
    fun `repeated reload crash and shutdown cycles remain bounded`() {
        val processes = mutableListOf<StressProcess>()
        val elapsed = measureTimeMillis {
            repeat(RELOADS) { index ->
                val process = if (index % CRASH_INTERVAL == CRASH_INTERVAL - 1) {
                    StressProcess(ByteArray(0), alive = false, exit = 23)
                } else {
                    StressProcess(frames(initialize(), shutdown()))
                }
                processes += process

                val result = LspProbe.probe(Path.of("gopdsdk"), null) { _, _ -> process }
                if (index % CRASH_INTERVAL == CRASH_INTERVAL - 1) {
                    assertTrue((result as ProbeResult.Incompatible).message.contains("exit code 23"))
                } else {
                    assertEquals(ProbeResult.Compatible("v1"), result)
                }
            }
        }

        val clean = processes.filterIndexed { index, _ -> index % CRASH_INTERVAL != CRASH_INTERVAL - 1 }
        assertTrue(clean.all { it.requestsText().contains("\"method\":\"shutdown\"") })
        assertTrue(clean.all { it.requestsText().contains("\"method\":\"exit\"") })
        assertTrue(processes.none(Process::isAlive), "every probe process must be reaped")
        assertTrue(elapsed < MAX_ELAPSED_MILLIS, "reload stress took $elapsed ms")

        println(
            """{"evidence":"plugin-unit","reloads":$RELOADS,"forcedCrashes":${RELOADS / CRASH_INTERVAL},"elapsedMs":$elapsed,"processesAlive":0}""",
        )
    }

    @Test
    fun `realistic workspace configuration churn keeps deep analysis opt in`() {
        val files = (0 until WORKLOAD_FILES).map { index -> "module-${index % MODULES}/game-$index.go" }
        val state = GopdsdkSettings.State()

        repeat(RAPID_CHANGES) { revision ->
            state.changedFiles = files.shuffled(java.util.Random(revision.toLong())).take(12).toMutableList()
            state.baseline = if (revision % 2 == 0) ".gopdsdk-baseline.json" else ""
            state.target = if (revision % 3 == 0) "device" else "both"
            val settings = state.analyzerSettings()

            assertEquals(12, settings.changedFiles.size)
            assertFalse(settings.deep)
        }

        assertEquals(WORKLOAD_FILES, files.distinct().size)
        assertEquals(MODULES, files.map { it.substringBefore('/') }.distinct().size)
    }

    private fun initialize(): String =
        """{"jsonrpc":"2.0","id":1,"result":{"capabilities":{"codeActionProvider":true,"diagnosticProvider":{}},"serverInfo":{"name":"fixture","version":"v1"}}}"""

    private fun shutdown(): String = """{"jsonrpc":"2.0","id":2,"result":null}"""

    private fun frames(vararg messages: String): ByteArray = messages.flatMap { message ->
        val body = message.toByteArray(StandardCharsets.UTF_8)
        ("Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII) + body).asIterable()
    }.toByteArray()

    private class StressProcess(
        input: ByteArray,
        private var alive: Boolean = true,
        private val exit: Int = 0,
    ) : Process() {
        private val requests = ByteArrayOutputStream()
        private val responses = ByteArrayInputStream(input)

        fun requestsText(): String = requests.toString(StandardCharsets.UTF_8)
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

    private companion object {
        const val WORKLOAD_FILES = 603
        const val MODULES = 3
        const val RAPID_CHANGES = 40
        const val RELOADS = 60
        const val CRASH_INTERVAL = 10
        const val MAX_ELAPSED_MILLIS = 15_000
    }
}

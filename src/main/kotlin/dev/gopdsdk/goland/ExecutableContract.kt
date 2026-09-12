package dev.gopdsdk.goland

import com.intellij.openapi.util.SystemInfoRt
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

internal object ExecutableDiscovery {
    fun find(configured: String, path: String? = System.getenv("PATH"), windows: Boolean = SystemInfoRt.isWindows): Path? {
        if (configured.isNotBlank()) return usable(Paths.get(configured), windows)
        val names = if (windows) listOf("gopdsdk.exe") else listOf("gopdsdk")
        return path.orEmpty().split(java.io.File.pathSeparatorChar).asSequence()
            .filter { it.isNotBlank() }
            .flatMap { directory -> names.asSequence().map { Paths.get(directory).resolve(it) } }
            .mapNotNull { usable(it, windows) }.firstOrNull()
    }

    private fun usable(candidate: Path, windows: Boolean): Path? {
        val normalized = candidate.toAbsolutePath().normalize()
        if (!Files.isRegularFile(normalized) || (!windows && !Files.isExecutable(normalized))) return null
        return normalized
    }
}

internal sealed class ProbeResult(open val message: String) {
    data class Compatible(val analyzerVersion: String) : ProbeResult("gopdsdk analyzer $analyzerVersion")
    data class Incompatible(override val message: String) : ProbeResult(message)
}

internal fun interface ProcessLauncher {
    fun start(command: List<String>, workingDirectory: String?): Process
}

internal object LspProbe {
    private const val timeoutMillis = 5_000L
    private const val maximumMessageBytes = 1024 * 1024
    private val launcher = ProcessLauncher { command, workingDirectory ->
        ProcessBuilder(command).apply {
            if (workingDirectory != null) directory(java.io.File(workingDirectory))
            redirectErrorStream(false)
        }.start()
    }

    fun probe(executable: Path, workingDirectory: String?): ProbeResult = probe(executable, workingDirectory, launcher)

    internal fun probe(executable: Path, workingDirectory: String?, processLauncher: ProcessLauncher): ProbeResult {
        val process = try {
            processLauncher.start(listOf(executable.toString(), "lsp"), workingDirectory)
        } catch (_: IOException) {
            return ProbeResult.Incompatible("gopdsdk could not be executed. Check the configured path and permissions.")
        }
        try {
            val output = BufferedOutputStream(process.outputStream)
            val input = BufferedInputStream(process.inputStream)
            writeMessage(output, """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"capabilities":{}}}""")
            val response = readMessage(input, process, timeoutMillis) ?: return ProbeResult.Incompatible(exitMessage(process))
            val version = Regex(""""serverInfo"\s*:\s*\{.*?"version"\s*:\s*"([^"]+)"""").find(response)?.groupValues?.get(1)
                ?: return ProbeResult.Incompatible("gopdsdk is incompatible: the LSP initialize response has no analyzer version.")
            if (version != "v1") return ProbeResult.Incompatible("gopdsdk analyzer $version is unsupported; this plugin requires analyzer v1.")
            if (!Regex(""""diagnosticProvider"\s*:""").containsMatchIn(response) ||
                !Regex(""""codeActionProvider"\s*:""").containsMatchIn(response)
            ) return ProbeResult.Incompatible("gopdsdk analyzer v1 is missing required diagnostic or safe-action capabilities.")
            writeMessage(output, """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""")
            readMessage(input, process, timeoutMillis)
            writeMessage(output, """{"jsonrpc":"2.0","method":"exit","params":null}""")
            return ProbeResult.Compatible(version)
        } catch (_: ProbeTimeout) {
            return ProbeResult.Incompatible("gopdsdk compatibility check timed out after 5 seconds.")
        } catch (_: IOException) {
            return ProbeResult.Incompatible(exitMessage(process))
        } finally {
            runCatching { process.outputStream.close() }
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.destroy()
                if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            }
        }
    }

    private fun writeMessage(output: BufferedOutputStream, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun readMessage(input: BufferedInputStream, process: Process, timeout: Long): String? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout)
        val header = StringBuilder()
        while (!header.endsWith("\r\n\r\n")) {
            if (System.nanoTime() >= deadline) throw ProbeTimeout()
            if (input.available() == 0) {
                if (!process.isAlive) return null
                Thread.sleep(10)
                continue
            }
            header.append(input.read().toChar())
            if (header.length > 8192) throw IOException("oversized LSP header")
        }
        val length = Regex("(?i)Content-Length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw IOException("missing LSP content length")
        if (length !in 1..maximumMessageBytes) throw IOException("invalid LSP message size")
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            if (System.nanoTime() >= deadline) throw ProbeTimeout()
            if (input.available() == 0) {
                if (!process.isAlive) throw IOException("server exited during response")
                Thread.sleep(10)
                continue
            }
            offset += input.read(body, offset, length - offset)
        }
        return String(body, StandardCharsets.UTF_8)
    }

    private fun exitMessage(process: Process): String = if (process.isAlive) {
        "gopdsdk closed the compatibility check without an LSP response."
    } else {
        "gopdsdk exited during the compatibility check (exit code ${process.exitValue()})."
    }

    private class ProbeTimeout : IOException()
}

internal object Redaction {
    fun message(value: String): String = value
        .replace(Regex("(?i)(token|password|secret|authorization)=([^\\s&]+)"), "$1=<redacted>")
        .take(2_000)
}

package dev.gopdsdk.goland

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.testFramework.LightVirtualFile
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit device operations, serialized per project to avoid conflicting USB/disk transitions. */
@Service(Service.Level.PROJECT)
internal class DeviceWorkflow(private val project: Project) : com.intellij.openapi.Disposable {
    private val busy = AtomicBoolean()
    @Volatile private var activeIndicator: ProgressIndicator? = null
    @Volatile var connection = DeviceConnection.UNCHECKED
        private set
    @Volatile var text = "Device: unchecked"
        private set

    private fun update(value: String) {
        text = value
        SimulatorWorkflowState.getInstance(project).refresh()
    }

    fun execute(operation: DeviceOperation, root: Path) {
        FileDocumentManager.getInstance().saveAllDocuments()
        if (!busy.compareAndSet(false, true)) {
            notify("A device operation is already running", false, root)
            return
        }
        val previousConnection = connection
        val settings = GopdsdkSettings.getInstance(project).state.copy()
        object : Task.Backgroundable(project, operation.label, true) {
            override fun run(indicator: ProgressIndicator) {
                activeIndicator = indicator
                try {
                    if (project.isDisposed) throw ProcessCanceledException()
                    val executable = ExecutableDiscovery.find(settings.executablePath)
                        ?: error("Configure gopdsdk in Settings | Tools | gopdsdk")
                    if (operation == DeviceOperation.CONNECTION) connection = DeviceConnection.CHECKING
                    update("Device: ${operation.label}")
                    val progress = DeviceProgress(operation) { stage ->
                        indicator.text = "${operation.label}: $stage"
                        update("Device: $stage")
                    }
                    val result = executeDeviceOperation(operation, settings.playdateSDK, { indicator.checkCanceled() }) { arguments ->
                        process(executable, root, arguments, indicator, if (arguments.first() == "capabilities") null else progress)
                    }
                    if (result.failure != null) {
                        connection = deviceFailureConnection(previousConnection, operation, result.failure)
                        error("${operation.command}: ${result.failure}")
                    }
                    result.connection?.let { connection = it }
                    update("Device: ${connection.name.lowercase()} — ${operation.label} complete")
                    if (result.log != null) ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            val file = deviceLogFile("${root.fileName}-${operation.command}.txt", result.log)
                            FileEditorManager.getInstance(project).openFile(file, true)
                        }
                    }
                    if (operation == DeviceOperation.UNMOUNT) notify("Data Disk safely ejected; USB reconnection confirmed by gopdsdk", false, root)
                } catch (cancelled: ProcessCanceledException) {
                    connection = DeviceConnection.UNKNOWN
                    update("Device: cancelled — connection unknown; check connection explicitly")
                    throw cancelled
                } catch (failure: Exception) {
                    if (connection != DeviceConnection.DISCONNECTED && connection != DeviceConnection.DISK) connection = DeviceConnection.UNKNOWN
                    update("Device: ${connection.name.lowercase()} — ${operation.label} failed")
                    notify(failure.message ?: "Device operation failed", operation == DeviceOperation.RUN, root)
                } finally {
                    activeIndicator = null
                }
            }
            override fun onFinished() { busy.set(false) }
        }.queue()
    }

    private fun process(executable: Path, root: Path, arguments: List<String>, indicator: ProgressIndicator, progress: DeviceProgress? = null): String {
        indicator.checkCanceled()
        val handler = CapturingProcessHandler(GeneralCommandLine(executable.toString()).withParameters(arguments)
            .withWorkDirectory(root.toFile()).withCharset(Charsets.UTF_8))
        handler.addProcessListener(object : ProcessListener {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType == ProcessOutputTypes.STDERR) progress?.append(event.text)
            }
        })
        val output = handler.runProcessWithProgressIndicator(indicator, 600_000)
        if (output.isCancelled) throw ProcessCanceledException()
        indicator.checkCanceled()
        require(!output.isTimeout) { "Device operation timed out; check device connection" }
        // Typed failures use a nonzero exit code and must still be decoded.
        require(output.stdout.isNotBlank()) { "gopdsdk returned no structured result (exit ${output.exitCode})" }
        if (output.exitCode != 0) {
            val envelope = com.google.gson.JsonParser.parseString(output.stdout).asJsonObject
            require(envelope["ok"]?.asBoolean == false) { "gopdsdk exited with ${output.exitCode}" }
        }
        return output.stdout
    }

    private fun notify(message: String, offerLogs: Boolean, root: Path) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                val notification = NotificationGroupManager.getInstance().getNotificationGroup("gopdsdk")
                    .createNotification("Playdate device", Redaction.message(message), if (offerLogs) NotificationType.WARNING else NotificationType.INFORMATION)
                if (offerLogs) for (operation in listOf(DeviceOperation.CRASHLOG, DeviceOperation.ERRORLOG)) {
                    notification.addAction(NotificationAction.createSimple("${operation.label} (mounts Data Disk)") { execute(operation, root) })
                }
                notification.notify(project)
            }
        }
    }

    override fun dispose() { activeIndicator?.cancel() }

    companion object { fun getInstance(project: Project): DeviceWorkflow = project.getService(DeviceWorkflow::class.java) }
}

internal fun deviceLogFile(name: String, content: String): LightVirtualFile =
    LightVirtualFile(name, PlainTextFileType.INSTANCE, content).apply { isWritable = false }

internal abstract class DeviceAction(private val operation: DeviceOperation) : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selected = event.getData(CommonDataKeys.VIRTUAL_FILE)?.path
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path ?: project.basePath ?: return
        val root = AnalyzerAdministration.moduleRoot(Path.of(selected)) ?: return
        DeviceWorkflow.getInstance(project).execute(operation, root)
    }
}

internal class BuildDeviceAction : DeviceAction(DeviceOperation.BUILD)
internal class RunDeviceAction : DeviceAction(DeviceOperation.RUN)
internal class CheckDeviceConnectionAction : DeviceAction(DeviceOperation.CONNECTION)
internal class ReadCrashLogAction : DeviceAction(DeviceOperation.CRASHLOG)
internal class ReadErrorLogAction : DeviceAction(DeviceOperation.ERRORLOG)
internal class MountDeviceDiskAction : DeviceAction(DeviceOperation.MOUNT)
internal class UnmountDeviceDiskAction : DeviceAction(DeviceOperation.UNMOUNT)

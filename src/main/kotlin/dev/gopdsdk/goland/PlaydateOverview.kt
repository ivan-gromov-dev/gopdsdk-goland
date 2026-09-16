package dev.gopdsdk.goland

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.Timer

/** A read-only projection of existing IDE and CLI state; the timer never runs a command. */
internal class PlaydateOverview(private val project: Project) : JPanel(BorderLayout()), UiDataProvider, Disposable {
    private val summary = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        accessibleContext.accessibleName = "Playdate project overview"
    }
    private val refresh = JButton("Refresh project health")
    private val health = OverviewHealth()
    private var root: Path? = null
    private var disposed = false
    private var busy = false
    @Volatile private var activeIndicator: ProgressIndicator? = null
    private val timer = Timer(1000) { if (isShowing) render() }

    init {
        val controls = JPanel().apply {
            add(refresh)
            for ((title, id) in listOf("Open Problems" to "Problems View", "Open Run output" to "Run")) {
                add(JButton(title).apply { addActionListener { ToolWindowManager.getInstance(project).getToolWindow(id)?.activate(null) } })
            }
        }
        add(controls, BorderLayout.NORTH)
        add(JBScrollPane(summary), BorderLayout.CENTER)
        val actions = DefaultActionGroup().apply {
            for (id in overviewActionIds) add(ActionManager.getInstance().getAction("gopdsdk.$id"))
        }
        add(ActionManager.getInstance().createActionToolbar("PlaydateOverview", actions, false).apply {
            targetComponent = this@PlaydateOverview
        }.component, BorderLayout.WEST)
        refresh.addActionListener { load() }
        render()
        timer.start()
    }

    override fun uiDataSnapshot(sink: DataSink) {
        sink[CommonDataKeys.PROJECT] = project
        activePlaydateModule(project)?.let { LocalFileSystem.getInstance().findFileByPath(it.toString()) }?.let { sink[CommonDataKeys.VIRTUAL_FILE] = it }
    }

    private fun load() {
        render()
        val selected = root ?: return
        if (busy) return
        busy = true
        val generation = health.begin(selected)
        render()
        object : Task.Backgroundable(project, "Checking Playdate project health", true) {
            private var report: HealthReport? = null
            override fun run(indicator: ProgressIndicator) {
                activeIndicator = indicator
                indicator.checkCanceled()
                report = ProjectHealthService.getInstance(project).refresh(selected)
                indicator.checkCanceled()
            }
            override fun onSuccess() { finish(requireNotNull(report), null) }
            override fun onThrowable(error: Throwable) { finish(null, Redaction.message(error.message ?: "Health check failed")) }
            override fun onCancel() { finish(null, "Cancelled; refresh to retry") }
            override fun onFinished() { activeIndicator = null; busy = false; render() }
            private fun finish(report: HealthReport?, error: String?) {
                if (disposed || project.isDisposed) return
                health.finish(selected, generation, report, error)
                render()
            }
        }.queue()
    }

    private fun render() {
        if (disposed || project.isDisposed) return
        root = activePlaydateModule(project)
        health.select(root)
        val selected = root
        refresh.isEnabled = selected != null && !busy
        val settings = selected?.let { AnalyzerModuleSettings.settings(project, it) } ?: GopdsdkSettings.getInstance(project).state
        val collector = ProblemsCollector.getInstance(project)
        val count = selected?.let { module -> collector.getProblemFiles().filter {
            AnalyzerAdministration.moduleRoot(Path.of(it.path)) == module
        }.sumOf { collector.getFileProblemCount(it) } }
        val value = overviewText(project.name, selected, settings.target, health,
            SimulatorWorkflowState.getInstance(project).text, DeviceWorkflow.getInstance(project).text, count)
        if (summary.text != value) summary.text = value
    }

    override fun dispose() { disposed = true; timer.stop(); activeIndicator?.cancel() }
}

internal val overviewActionIds = listOf("buildSimulator", "runSimulator", "buildDevice", "runDevice",
    "checkDeviceConnection", "mountDeviceDisk", "unmountDeviceDisk", "readCrashLog", "readErrorLog",
    "analyzer", "refreshDiagnostics", "showLogs")

internal fun activePlaydateModule(project: Project): Path? =
    (FileEditorManager.getInstance(project).selectedFiles.firstOrNull()?.path ?: project.basePath)
        ?.let { AnalyzerAdministration.moduleRoot(Path.of(it)) }

/** Generation and module guards keep a late health result out of another module's overview. */
internal class OverviewHealth {
    var root: Path? = null; private set
    var report: HealthReport? = null; private set
    var error: String? = null; private set
    var loading = false; private set
    private var generation = 0
    fun select(value: Path?) {
        if (root == value) return
        root = value; report = null; error = null; loading = false; generation++
    }
    fun begin(value: Path): Int {
        select(value)
        report = null; error = null; loading = true
        return ++generation
    }
    fun finish(value: Path, token: Int, result: HealthReport?, failure: String?) {
        if (root != value || token != generation) return
        report = result; error = failure; loading = false
    }
}

internal fun overviewText(project: String, root: Path?, target: String, health: OverviewHealth,
    simulator: String, device: String, problems: Int?): String = buildString {
    appendLine("Project: $project")
    appendLine("Module: ${root ?: "No Go module selected — open a file in the application module"}")
    appendLine("Analysis target: $target")
    appendLine("gopdsdk release version: not exposed by the CLI contract")
    appendLine("gopdsdk analyzer protocol: ${health.report?.analyzerVersion ?: "unchecked"}")
    appendLine("Playdate SDK: ${health.report?.sdkVersion ?: "unverified"}")
    appendLine("Health: ${when { root == null -> "no module"; health.loading -> "loading"; health.error != null -> health.error; health.report == null -> "unchecked — refresh explicitly"; else -> "last explicit check" }}")
    health.report?.checks?.forEach { appendLine("  ${it.id}: ${it.status.name.lowercase()} (${it.evidenceLevel})") }
    appendLine("Last project operations (shared with the status bar):")
    appendLine(simulator)
    appendLine(device)
    appendLine("Module Problems (all IDE sources): ${problems ?: "unavailable"}")
    appendLine("Counts reflect currently published Problems, not a full-project analysis.")
    appendLine("Simulator output is in Run. Device logs are read only on request and mount Data Disk.")
}

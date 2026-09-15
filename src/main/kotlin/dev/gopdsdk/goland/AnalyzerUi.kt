package dev.gopdsdk.goland

import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.DefaultTableModel

internal class AnalyzerAdministrationAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selected = event.getData(CommonDataKeys.VIRTUAL_FILE)?.path ?: project.basePath ?: return
        val root = AnalyzerAdministration.moduleRoot(Path.of(selected))
        if (root == null) {
            Messages.showInfoMessage(project, "Select a file in the Go module to configure.", "gopdsdk Analyzer")
            return
        }
        AnalyzerDialog(project, root).show()
    }
}

/** Explicit, cancellable CLI operations. The dialog stays bound to its original module. */
private class AnalyzerDialog(private val project: Project, private val root: Path) : DialogWrapper(project, false) {
    private val target = JComboBox(AnalyzerAdministration.targets.toTypedArray())
    private val profile = JComboBox(AnalyzerAdministration.profiles.toTypedArray())
    private val category = JComboBox(arrayOf("All categories"))
    private val rules = JList<AnalyzerRule>()
    private val help = JBTextArea().apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val output = JBTextArea().apply { isEditable = false }
    private val findingsModel = object : DefaultTableModel(arrayOf("Target", "Rule", "Severity", "Location", "Finding"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val findingsTable = JTable(findingsModel)
    private val buttons = mutableListOf<JButton>()
    private var configText = ""
    private var configuration = AnalyzerAdministration.configuration("")
    private var catalog: AnalyzerCatalog? = null
    private var findings = emptyList<AnalyzerFinding>()
    private var sources = emptyMap<String, String>()
    private val activeIndicators = java.util.concurrent.ConcurrentHashMap.newKeySet<ProgressIndicator>()
    private var pendingTasks = 0
    @Volatile private var closed = false
    private val configPath = root.resolve(AnalyzerAdministration.configName)

    init {
        title = "gopdsdk Analyzer — $root"
        isModal = false
        setOKButtonText("Close")
        init()
        guarded {
            configText = readConfiguration()
            configuration = AnalyzerAdministration.configuration(configText)
            target.selectedItem = configuration["target"]?.asString ?: "both"
            profile.selectedItem = configuration["profile"]?.asString ?: "default"
        }
    }

    override fun createCenterPanel(): JComponent {
        val settings = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(javax.swing.JLabel("Target:")); add(target)
            add(javax.swing.JLabel("Profile:")); add(profile)
            add(button("Save target/profile") { saveConfiguration(AnalyzerAdministration.select(configuration, target.selectedItem as String, profile.selectedItem as String)) })
            add(button("Load rules") { loadRules() })
        }
        rules.selectionMode = ListSelectionModel.SINGLE_SELECTION
        rules.addListSelectionListener { help.text = rules.selectedValue?.help.orEmpty(); help.caretPosition = 0 }
        category.addActionListener { filterRules() }
        val ruleButtons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(category)
            add(button("Show exact-version help") { showRuleHelp() })
            add(button("Configure selected rule") { configureRule() })
        }
        val rulePanel = JPanel(BorderLayout()).apply {
            add(ruleButtons, BorderLayout.NORTH)
            add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JBScrollPane(rules), JBScrollPane(help)).apply { resizeWeight = 0.45 }, BorderLayout.CENTER)
        }
        val findingButtons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(button("Compare findings") { compare() })
            add(button("Open source") { selectedFinding()?.let { open(it.path, it.line, it.column) } })
            add(button("Rule help") { selectedFinding()?.documentation?.let { url ->
                val uri = java.net.URI(url)
                require(uri.scheme in listOf("https", "http")) { "Unsupported documentation URL" }
                BrowserUtil.browse(url)
            } })
            add(button("Suppress with reason…") { suppress() })
        }
        val findingPanel = JPanel(BorderLayout()).apply { add(findingButtons, BorderLayout.NORTH); add(JBScrollPane(findingsTable), BorderLayout.CENTER) }
        val baselineButtons = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            for (operation in listOf("create", "update", "validate")) add(button("${operation.replaceFirstChar { it.uppercase() }} baseline…") { baseline(operation) })
            add(button("Inspect baseline") { open(baselineName()) })
        }
        val baselinePanel = JPanel(BorderLayout()).apply { add(baselineButtons, BorderLayout.NORTH); add(JBScrollPane(output), BorderLayout.CENTER) }
        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(1000, 600)
            add(settings, BorderLayout.NORTH)
            add(JTabbedPane().apply { addTab("Rules", rulePanel); addTab("Findings", findingPanel); addTab("Baselines / operation output", baselinePanel) }, BorderLayout.CENTER)
        }
    }

    private fun button(label: String, action: () -> Unit): JButton = JButton(label).also { button ->
        buttons.add(button)
        button.addActionListener { guarded(action) }
    }

    private fun guarded(action: () -> Unit) {
        try { action() } catch (error: Exception) {
            Messages.showErrorDialog(project, Redaction.message(error.message ?: "Analyzer operation failed"), "gopdsdk Analyzer")
        }
    }

    private fun <T> background(title: String, work: (ProgressIndicator, Path) -> T, done: (T) -> Unit) {
        val executable = ExecutableDiscovery.find(GopdsdkSettings.getInstance(project).state.executablePath)
            ?: error("Configure the gopdsdk executable in Settings | Tools | gopdsdk")
        buttons.forEach { it.isEnabled = false }
        pendingTasks++
        object : Task.Backgroundable(project, title, true) {
            private var value: T? = null
            private var taskIndicator: ProgressIndicator? = null
            override fun run(indicator: ProgressIndicator) {
                taskIndicator = indicator
                activeIndicators.add(indicator)
                if (closed) indicator.cancel()
                indicator.checkCanceled()
                value = work(indicator, executable)
            }
            override fun onSuccess() { if (!closed && !project.isDisposed) guarded { @Suppress("UNCHECKED_CAST") done(value as T) } }
            override fun onThrowable(error: Throwable) { if (!closed && !project.isDisposed) guarded { throw IllegalStateException(error.message ?: "Analyzer operation failed") } }
            override fun onFinished() {
                taskIndicator?.let { activeIndicators.remove(it) }
                pendingTasks--
                if (!closed && pendingTasks == 0) buttons.forEach { it.isEnabled = true }
            }
        }.queue()
    }

    private fun run(executable: Path, args: List<String>, indicator: ProgressIndicator): String {
        indicator.checkCanceled()
        val result = CapturingProcessHandler(GeneralCommandLine(executable.toString()).withParameters(args).withWorkDirectory(root.toString())
            .withCharset(Charsets.UTF_8)).runProcessWithProgressIndicator(indicator, 120_000)
        indicator.checkCanceled()
        require(!result.isTimeout && result.exitCode == 0) { "gopdsdk ${args.first()} failed (exit ${result.exitCode}); inspect the command in a terminal" }
        return result.stdout
    }

    private fun requireCapability(executable: Path, command: String, schema: String, indicator: ProgressIndicator) {
        require(AnalyzerAdministration.supports(run(executable, listOf("capabilities"), indicator), command, schema)) {
            "This gopdsdk does not advertise $command / $schema. Update gopdsdk; existing language-server features remain available."
        }
    }

    private fun loadRules() = background("Load gopdsdk rule catalog", { indicator, executable ->
        requireCapability(executable, "rules", "gopdsdk-analyzer-contracts/v1", indicator)
        AnalyzerAdministration.catalog(run(executable, listOf("rules", "--format", "json"), indicator))
    }) { value ->
        catalog = value
        category.removeAllItems(); category.addItem("All categories")
        value.rules.map { it.family }.distinct().sorted().forEach { category.addItem(it) }
        filterRules()
    }

    private fun filterRules() { rules.setListData(catalog?.rules.orEmpty().filter { category.selectedIndex <= 0 || it.family == category.selectedItem }.toTypedArray()) }

    private fun showRuleHelp() {
        val rule = rules.selectedValue ?: return
        val client = com.intellij.platform.lsp.api.LspClientManager.getInstance(project)
            .getClients(GopdsdkLspIntegrationProvider::class.java).firstOrNull {
                (it.descriptor as? GopdsdkLspClientDescriptor)?.moduleRoot == root
            } ?: error("Open a Go file in this module to start the language server")
        background("Load exact-version rule help", { _, _ ->
            requireNotNull(client.sendRequestSync(10_000) { server -> (server as GopdsdkLanguageServer).ruleHelp(mapOf("rule" to rule.id)) }) { "Rule help request timed out" }
        }) { response ->
            require(response.getAsJsonObject("rule")["id"].asString == rule.id)
            val url = response["documentation"].asString
            require(java.net.URI(url).scheme in listOf("https", "http"))
            help.text = "${rule.help}\n\nExact-version documentation: $url"
            BrowserUtil.browse(url)
        }
    }

    private fun configureRule() {
        val rule = rules.selectedValue ?: return
        val mode = JComboBox(arrayOf("inherit", "enable", "exclude"))
        mode.selectedItem = when {
            configuration.getAsJsonArray("excludeRules")?.any { it.asString == rule.id } == true -> "exclude"
            configuration.getAsJsonArray("rules")?.any { it.asString == rule.id } == true -> "enable"
            else -> "inherit"
        }
        val severity = JComboBox((listOf("inherit") + AnalyzerAdministration.severities).toTypedArray())
        severity.selectedItem = configuration.getAsJsonObject("severities")?.get(rule.id)?.asString ?: "inherit"
        val dialog = object : DialogWrapper(project) {
            init { title = rule.id; init() }
            override fun createCenterPanel(): JComponent = JPanel(java.awt.GridLayout(0, 1)).apply {
                add(javax.swing.JLabel("Rule selection (enable adds to the explicit allowlist):")); add(mode)
                add(javax.swing.JLabel("Severity (analyzer default: ${rule.severity}):")); add(severity)
            }
        }
        if (dialog.showAndGet()) saveConfiguration(AnalyzerAdministration.rule(configuration, rule.id, mode.selectedItem as String, severity.selectedItem as String))
    }

    private fun readConfiguration(): String {
        AnalyzerAdministration.contained(root, AnalyzerAdministration.configName)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(configPath)
        return file?.let { FileDocumentManager.getInstance().getDocument(it)?.text } ?: if (Files.exists(configPath)) Files.readString(configPath) else ""
    }

    private fun saveConfiguration(value: JsonObject) {
        if (value["profile"]?.asString == "experimental" && catalog == null) {
            background("Load experimental analyzer profile", { indicator, executable ->
                requireCapability(executable, "rules", "gopdsdk-analyzer-contracts/v1", indicator)
                AnalyzerAdministration.catalog(run(executable, listOf("rules", "--format", "json"), indicator))
            }) { loaded -> catalog = loaded; saveConfiguration(value) }
            return
        }
        require(readConfiguration() == configText) { "Configuration changed while this dialog was open. Reopen Analyzer and retry." }
        val settings = AnalyzerAdministration.lspSettings(value, AnalyzerModuleSettings.settings(project, root), catalog?.rules?.map { it.id })
        val text = AnalyzerAdministration.encode(value)
        WriteCommandAction.runWriteCommandAction(project) {
            val directory = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root))
            val file = directory.findChild(AnalyzerAdministration.configName) ?: directory.createChildData(this, AnalyzerAdministration.configName)
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
            require(document.text == configText)
            document.setText(text)
            FileDocumentManager.getInstance().saveDocument(document)
        }
        configText = text; configuration = value
        AnalyzerModuleSettings.getInstance(project).put(root, settings)
        AnalyzerModuleSettings.refresh(project)
        SimulatorWorkflowState.getInstance(project).refresh()
        output.text = "Saved ${AnalyzerAdministration.configName} in $root and refreshed this module's language-server settings."
    }

    private fun snapshot(indicator: ProgressIndicator): Map<String, String> = Files.walk(root).use { paths ->
        paths.filter { Files.isRegularFile(it) && (it.toString().endsWith(".go") || it.fileName.toString() in listOf("go.mod", "go.sum", AnalyzerAdministration.configName)) }
            .map { indicator.checkCanceled(); root.relativize(it).toString().replace('\\', '/') to Files.readString(it).replace("\r\n", "\n") }
            .toList().toMap()
    }

    private fun compare() {
        FileDocumentManager.getInstance().saveAllDocuments()
        background("Compare gopdsdk findings", { indicator, executable ->
            requireCapability(executable, "check", "gopdsdk-check/v1", indicator)
            val before = snapshot(indicator)
            val report = AnalyzerAdministration.compare(listOf("shared", "simulator", "device").map { target ->
                AnalyzerAdministration.report(run(executable, AnalyzerAdministration.checkArguments(target), indicator))
            })
            require(before == snapshot(indicator)) { "Module changed during analysis; retry Compare findings" }
            report to before
        }) { (report, before) ->
            sources = before; findings = report.findings
            findingsModel.rowCount = 0
            findings.forEach { findingsModel.addRow(arrayOf(it.target, it.rule, it.severity, "${it.path}:${it.line}:${it.column}", it.message + if (it.suppressed) " [suppressed]" else "")) }
            output.text = "Analyzer ${report.analyzerVersion}, SDK ${report.sdkVersion}\n${findings.size} findings; grouped by analyzer-provided target.\n${report.raw}"
        }
    }

    private fun selectedFinding(): AnalyzerFinding? = findingsTable.selectedRow.takeIf { it >= 0 }?.let { findings[findingsTable.convertRowIndexToModel(it)] }

    private fun suppress() {
        val finding = selectedFinding() ?: return
        require(!finding.suppressed) { "This finding is already suppressed" }
        val expected = sources[finding.path] ?: error("Run Compare findings first")
        val reason = Messages.showInputDialog(project, "Why is this finding accepted?", "Suppress ${finding.rule}", null) ?: return
        require(reason.isNotBlank() && !reason.contains('\n') && !reason.contains('\r')) { "A single-line reason is required" }
        background("Verify suppression policy", { indicator, executable ->
            requireCapability(executable, "rules", "gopdsdk-analyzer-contracts/v1", indicator)
            val currentCatalog = AnalyzerAdministration.catalog(run(executable, listOf("rules", "--format", "json"), indicator))
            require(currentCatalog.rules.singleOrNull { it.id == finding.rule }?.suppressible == true) { "Analyzer does not allow suppression of this rule" }
        }) {
            val path = AnalyzerAdministration.contained(root, finding.path)
            require(AnalyzerAdministration.moduleRoot(path)?.toRealPath() == root.toRealPath()) { "Finding belongs to another Go module" }
            val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(file))
            WriteCommandAction.runWriteCommandAction(project) {
                val (offset, text) = AnalyzerAdministration.suppression(document.text, finding.line, finding.rule, reason, expected)
                document.insertString(offset, text)
                FileDocumentManager.getInstance().saveDocument(document)
            }
            open(finding.path, finding.line)
        }
    }

    private fun baselineName(): String = configuration["baseline"]?.asString ?: ".gopdsdk-check-baseline.json"

    private fun baseline(operation: String) {
        val name = Messages.showInputDialog(project, "Module-relative baseline path", "$operation baseline", null, baselineName(), null) ?: return
        val path = AnalyzerAdministration.contained(root, name)
        require(path != configPath && path.fileName.toString().endsWith(".json")) { "Choose a baseline JSON file" }
        val reason = if (operation == "validate") null else Messages.showInputDialog(project, "Reason for new baseline entries", "$operation baseline", null, "accepted migration debt", null) ?: return
        if (reason != null) require(reason.isNotBlank()) { "A reason is required" }
        FileDocumentManager.getInstance().saveAllDocuments()
        background("$operation gopdsdk baseline", { indicator, executable ->
            requireCapability(executable, "baseline", "gopdsdk-baseline-result/v1", indicator)
            requireCapability(executable, "check", "gopdsdk-check/v1", indicator)
            val before = snapshot(indicator)
            val report = run(executable, AnalyzerAdministration.checkArguments(), indicator)
            AnalyzerAdministration.report(report)
            require(before == snapshot(indicator)) { "Module changed during analysis; retry baseline operation" }
            val temporary = Files.createTempFile(root, ".gopdsdk-report-", ".json")
            try {
                Files.writeString(temporary, report)
                val args = listOf("baseline", operation, "--input", temporary.fileName.toString(), "--output", name) +
                    if (reason == null) emptyList() else listOf("--reason", reason.trim())
                AnalyzerAdministration.baselineResult(run(executable, args, indicator), operation)
            } finally { Files.deleteIfExists(temporary) }
        }) { result ->
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)?.refresh(false, false)
            if (operation != "validate") saveConfiguration(configuration.deepCopy().apply { addProperty("baseline", name) })
            output.text = AnalyzerAdministration.encode(result)
        }
    }

    private fun open(name: String, line: Int = 1, column: Int = 1) {
        val file = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(AnalyzerAdministration.contained(root, name))) { "File does not exist" }
        FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file, line - 1, column - 1), true)
    }

    override fun dispose() { closed = true; activeIndicators.forEach { it.cancel() }; super.dispose() }
}

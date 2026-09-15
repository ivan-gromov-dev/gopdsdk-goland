package dev.gopdsdk.goland

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClientManager
import java.nio.file.Path
import org.eclipse.lsp4j.DidChangeConfigurationParams

/** Per-module settings mirror VS Code's workspace-folder configuration scope. */
@Service(Service.Level.PROJECT)
@State(name = "GopdsdkAnalyzerModules", storages = [Storage("gopdsdk.xml")])
internal class AnalyzerModuleSettings : PersistentStateComponent<AnalyzerModuleSettings.State> {
    data class State(var modules: MutableMap<String, GopdsdkSettings.State> = linkedMapOf())
    private var value = State()
    override fun getState(): State = value
    override fun loadState(state: State) { value = state }
    fun forRoot(root: Path, fallback: GopdsdkSettings.State): GopdsdkSettings.State = value.modules[key(root)] ?: fallback
    fun put(root: Path, settings: GopdsdkSettings.State) { value.modules[key(root)] = settings }
    private fun key(root: Path) = root.toAbsolutePath().normalize().toString().replace('\\', '/')

    companion object {
        fun getInstance(project: Project): AnalyzerModuleSettings = project.getService(AnalyzerModuleSettings::class.java)
        fun settings(project: Project, root: Path): GopdsdkSettings.State = getInstance(project).forRoot(root, GopdsdkSettings.getInstance(project).state)
        fun refresh(project: Project) {
            LspClientManager.getInstance(project).getClients(GopdsdkLspIntegrationProvider::class.java).forEach { client ->
                val descriptor = client.descriptor as? GopdsdkLspClientDescriptor ?: return@forEach
                client.sendNotification { server -> server.workspaceService.didChangeConfiguration(
                    DidChangeConfigurationParams(settings(project, descriptor.moduleRoot).analyzerSettings()),
                ) }
            }
        }
    }
}

package dev.gopdsdk.goland

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "GopdsdkSettings", storages = [Storage("gopdsdk.xml")])
internal class GopdsdkSettings : PersistentStateComponent<GopdsdkSettings.State> {
    data class State(var executablePath: String = "")
    private var settings = State()
    override fun getState(): State = settings
    override fun loadState(state: State) { settings = state }

    companion object {
        fun getInstance(project: Project): GopdsdkSettings = project.getService(GopdsdkSettings::class.java)
    }
}

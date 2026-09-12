package dev.gopdsdk.goland

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "GopdsdkSettings", storages = [Storage("gopdsdk.xml")])
internal class GopdsdkSettings : PersistentStateComponent<GopdsdkSettings.State> {
    data class State(
        var executablePath: String = "",
        var target: String = "both",
        var gopdsdkFloor: String = "",
        var playdateSDK: String = "",
        var rules: MutableList<String> = mutableListOf(),
        var categories: MutableList<String> = mutableListOf(),
        var excludeRules: MutableList<String> = mutableListOf(),
        var severities: MutableMap<String, String> = linkedMapOf(),
        var baseline: String = "",
        var changedFiles: MutableList<String> = mutableListOf(),
        var deep: Boolean = false,
    )
    private var settings = State()
    override fun getState(): State = settings
    override fun loadState(state: State) { settings = state }

    companion object {
        fun getInstance(project: Project): GopdsdkSettings = project.getService(GopdsdkSettings::class.java)
    }
}

internal data class AnalyzerSettings(
    val target: String,
    val gopdsdkFloor: String,
    val playdateSDK: String,
    val rules: List<String>,
    val categories: List<String>,
    val excludeRules: List<String>,
    val severities: Map<String, String>,
    val baseline: String,
    val changedFiles: List<String>,
    val deep: Boolean,
)

internal fun GopdsdkSettings.State.analyzerSettings(): AnalyzerSettings = AnalyzerSettings(
    target = target,
    gopdsdkFloor = gopdsdkFloor.trim(),
    playdateSDK = playdateSDK.trim(),
    rules = normalizedStrings(rules),
    categories = normalizedStrings(categories),
    excludeRules = normalizedStrings(excludeRules),
    severities = severities.mapKeys { it.key.trim() }.filterKeys { it.isNotEmpty() },
    baseline = baseline.trim(),
    changedFiles = normalizedStrings(changedFiles),
    deep = deep,
)

private fun normalizedStrings(values: Iterable<String>): List<String> =
    values.map(String::trim).filter(String::isNotEmpty).distinct()

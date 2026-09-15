package dev.gopdsdk.goland

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformApiCompatibilityTest {
    @Test fun `tool window inherits platform defaults without compatibility bridges`() {
        val methods = PlaydateToolWindowFactory::class.java.declaredMethods.map { it.name }.toSet()
        // These generated super calls caused the 0.2.0 Marketplace warnings.
        for (name in listOf("isApplicable", "isDoNotActivateOnStart", "getAnchor", "getIcon", "manage")) {
            assertFalse(name in methods, "Unexpected platform compatibility bridge: $name")
        }
        assertTrue("createToolWindowContent" in methods)
    }

    @Test fun `status widget implements only the current presentation overload`() {
        val widget = SimulatorStatusBarWidgetFactory::class.java.declaredClasses.single { it.simpleName == "Widget" }
        val presentations = widget.declaredMethods.filter { it.name == "getPresentation" }
        assertTrue(presentations.any { it.parameterCount == 0 })
        assertFalse(presentations.any { it.parameterCount != 0 }, "Deprecated PlatformType overload must remain inherited")
    }
}

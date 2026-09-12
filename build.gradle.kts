plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.gopdsdk"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

dependencies {
    intellijPlatform {
        goland("2026.1.4")
        bundledPlugin("org.jetbrains.plugins.go")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "dev.gopdsdk"
        name = "gopdsdk"
        version = project.version.toString()
        description = "Playdate diagnostics and safe fixes powered by gopdsdk."
        ideaVersion {
            sinceBuild = "261.26222"
            untilBuild = "262.*"
        }
        vendor { name = "gopdsdk" }
    }
    pluginVerification { ides { recommended() } }
}

tasks { test { useJUnitPlatform() } }

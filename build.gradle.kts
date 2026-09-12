import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.gopdsdk"
version = "0.1.0"

val minimumGoLandVersion = providers.gradleProperty("minimumGoLandVersion")
val latestGoLandVersion = providers.gradleProperty("latestGoLandVersion")
val platformVersion = providers.gradleProperty("platformVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
    }
}

dependencies {
    intellijPlatform {
        goland(platformVersion)
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
    pluginVerification {
        ides {
            create(IntelliJPlatformType.GoLand, minimumGoLandVersion)
            create(IntelliJPlatformType.GoLand, latestGoLandVersion)
        }
    }
}

tasks { test { useJUnitPlatform() } }

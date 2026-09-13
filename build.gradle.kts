import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.io.File

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.gopdsdk"
version = "0.1.0"

val minimumGoLandVersion = providers.gradleProperty("minimumGoLandVersion")
val latestGoLandVersion = providers.gradleProperty("latestGoLandVersion")
val platformVersion = providers.gradleProperty("platformVersion")
val verifierGoLandVersion = providers.gradleProperty("verifierGoLandVersion")
val certificateChainFile = layout.file(
    providers.environmentVariable("CERTIFICATE_CHAIN_FILE").map(::File),
)
val privateKeyFile = layout.file(
    providers.environmentVariable("PRIVATE_KEY_FILE").map(::File),
)

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
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
    testRuntimeOnly("junit:junit:4.13.2")
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
    signing {
        certificateChainFile = certificateChainFile
        privateKeyFile = privateKeyFile
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("releaseChannel")
            .map { listOf(if (it == "stable") "default" else "eap") }
            .orElse(listOf("eap"))
    }
    pluginVerification {
        ides {
            if (verifierGoLandVersion.isPresent) {
                create(IntelliJPlatformType.GoLand, verifierGoLandVersion)
            } else {
                create(IntelliJPlatformType.GoLand, minimumGoLandVersion)
                create(IntelliJPlatformType.GoLand, latestGoLandVersion)
            }
        }
    }
}

tasks {
    test { useJUnitPlatform() }
    withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}

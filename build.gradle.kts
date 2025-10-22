import org.gradle.api.GradleException
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    base
    kotlin("jvm") version "1.9.24" apply false
}

allprojects {
    group = "io.inneren.mh"
    version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"
}

subprojects {
    // не пакуем *.bin внутрь наших артефактов
    tasks.withType<Jar>().configureEach { exclude("**/*.bin") }
}

// Root guard: падаем ТОЛЬКО если *.bin отслеживаются Git (а не лежат в build/*)
tasks.register("verifyNoBin") {
    group = "verification"
    description = "Fail if any tracked *.bin files are present in the repository"
    doLast {
        val out = java.io.ByteArrayOutputStream()
        exec {
            commandLine("git", "ls-files", "-z")
            standardOutput = out
        }
        val trackedBins = out.toString(Charsets.UTF_8.name())
            .split("\u0000")
            .filter { it.isNotBlank() }
            .filter { it.endsWith(".bin", ignoreCase = true) }
        if (trackedBins.isNotEmpty()) {
            throw GradleException(
                "Banned tracked *.bin files detected:\n - " + trackedBins.joinToString("\n - ")
            )
        }
    }
}

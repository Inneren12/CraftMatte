import org.gradle.api.GradleException
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.registering

plugins {
    base
    kotlin("jvm") version "1.9.24" apply false
}

allprojects {
    group = "io.inneren.mh"
    version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"
}

subprojects {
    // Никогда не пакуем *.bin внутрь наших артефактов
    tasks.withType<Jar>().configureEach { exclude("**/*.bin") }
}

// Доп. безопасный таск (по желанию): проверяет ТОЛЬКО трекнутые Git файлы.
// Не привязан к 'check' и не будет срабатывать при обычной сборке.
val verifyNoTrackedBin by tasks.registering {
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
            .filter { it.isNotBlank() && it.lowercase().endsWith(".bin") }
        if (trackedBins.isNotEmpty()) {
            throw GradleException(
                "Banned tracked *.bin files detected:\n - " + trackedBins.joinToString("\n - ")
            )
        } else {
            println("No tracked *.bin files found.")
        }
    }
}

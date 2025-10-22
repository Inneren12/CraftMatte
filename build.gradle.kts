import java.util.zip.ZipFile
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.jvm.tasks.Jar

plugins {
    base
}

fun Project.configureBinGuards() {
    tasks.withType<Jar>().configureEach {
        exclude("**/*.bin")
    }

    val verifyNoBin = tasks.register("verifyNoBin") {
        group = "verification"
        description = "Fail build if any *.bin found in sources/resources"
        doLast {
            val banned = fileTree(projectDir) {
                include("**/*.bin")
                exclude(
                    ".git/**",
                    ".gradle/**",
                    "build/**",
                    "**/out/**"
                )
            }.files

            if (banned.isNotEmpty()) {
                val list = banned.joinToString("\n - ") { it.relativeTo(projectDir).path }
                throw GradleException("Banned *.bin files detected in repo:\n - $list")
            }
        }
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(verifyNoBin)
    }

    val assertNoBinInJar = tasks.register("assertNoBinInJar") {
        group = "verification"
        description = "Open produced jars and assert no *.bin entries exist"
        dependsOn(tasks.withType<Jar>())
        doLast {
            tasks.withType<Jar>().forEach { jarTask ->
                val archive = jarTask.archiveFile.orNull?.asFile
                if (archive != null && archive.exists()) {
                    ZipFile(archive).use { zf ->
                        val hasBin = zf.entries().asSequence()
                            .any { it.name.endsWith(".bin", ignoreCase = true) }
                        if (hasBin) {
                            throw GradleException("Jar ${'$'}{archive.name} contains banned .bin entries")
                        }
                    }
                }
            }
        }
    }

    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(assertNoBinInJar)
    }
}

configureBinGuards()

subprojects {
    configureBinGuards()
}

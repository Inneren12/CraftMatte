import org.gradle.jvm.tasks.Jar
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    kotlin("jvm") version "1.9.24" apply false
}

allprojects {
    group = "io.inneren.mh"
    version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"
}

// Минимум: исключить *.bin из JAR-артефактов (без падений и гардеров)
subprojects {
    tasks.withType<Jar>().configureEach { exclude("**/*.bin") }

    apply(plugin = "jacoco")

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

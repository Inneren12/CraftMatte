import org.gradle.jvm.tasks.Jar

plugins {
    base
    kotlin("jvm") version "1.9.24" apply false
}

allprojects {
    group = "io.inneren.mh"
    version = System.getenv("VERSION") ?: "0.1.0-SNAPSHOT"
}

// Минимум: исключить *.bin из JAR-артефактов (без падений и гардеров)
subprojects { tasks.withType<Jar>().configureEach { exclude("**/*.bin") } }

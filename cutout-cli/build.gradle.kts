plugins {
    kotlin("jvm")
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":cutout-core"))
    implementation(project(":cutout-engine-heur"))
    implementation("com.drewnoakes:metadata-extractor:2.18.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.inneren.mh.cutout.cli.MainKt")
}
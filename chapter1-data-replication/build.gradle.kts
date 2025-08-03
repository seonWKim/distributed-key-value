plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.22"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    api("io.github.microutils:kotlin-logging:2.0.4")
    api("org.slf4j:slf4j-simple:1.7.29")

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.distributed.keyvalue.chapter1.MainKt")
}

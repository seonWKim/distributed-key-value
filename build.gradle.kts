plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "com.distributed.keyvalue"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.microutils:kotlin-logging:2.0.4")

    implementation(project(":chapter1-data-replication"))
    implementation(project(":chapter2-data-partitioning"))
    implementation(project(":chapter3-distributed-time"))
    implementation(project(":chapter4-cluster-management"))
    implementation(project(":chapter5-inter-node-communication"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21) // Use Java 21 for Kotlin
}

tasks.test {
    useJUnitPlatform()
}

// Apply Java 21 compatibility to all subprojects
subprojects {
    repositories {
        mavenCentral()
    }

    // Configure Java toolchain for all subprojects
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }

    // Set Kotlin compilation target to Java 21
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
        }
    }
}

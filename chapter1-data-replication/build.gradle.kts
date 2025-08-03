plugins {
    kotlin("jvm")
    application
}

dependencies {
    api("io.github.microutils:kotlin-logging")

    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.distributed.keyvalue.chapter1.MainKt")
}

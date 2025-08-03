plugins {
    kotlin("jvm")
    application
}

dependencies {
    api("io.github.microutils:kotlin-logging:2.0.4")

    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.distributed.keyvalue.chapter1.MainKt")
}

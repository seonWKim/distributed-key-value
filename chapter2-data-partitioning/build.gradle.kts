plugins {
    kotlin("jvm")
    application
}

dependencies {
    api(project(":chapter1-data-replication"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.distributed.keyvalue.chapter2.MainKt")
}
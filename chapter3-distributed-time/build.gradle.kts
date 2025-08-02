plugins {
    kotlin("jvm")
    application
}

dependencies {
    api(project(":chapter2-data-partitioning"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.distributed.keyvalue.chapter3.MainKt")
}
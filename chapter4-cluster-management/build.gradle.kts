plugins {
    kotlin("jvm")
    application
}

dependencies {
    api(project(":chapter3-distributed-time"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.distributed.keyvalue.chapter4.MainKt")
}
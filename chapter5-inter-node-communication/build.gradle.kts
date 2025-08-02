plugins {
    kotlin("jvm")
    application
}

dependencies {
    api(project(":chapter4-cluster-management"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.distributed.keyvalue.chapter5.MainKt")
}
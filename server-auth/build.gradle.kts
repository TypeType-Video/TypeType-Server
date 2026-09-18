plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":server-db"))
    implementation(project(":server-core"))

    implementation("com.auth0:java-jwt:4.6.1")
    implementation("com.password4j:password4j:1.8.4")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.json:json:20260814")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.ktor:ktor-http-jvm:3.5.2")
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
}

kotlin {
    jvmToolchain(25)
}

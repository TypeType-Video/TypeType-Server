plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":server-core"))
    implementation(project(":server-db"))
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    api("com.auth0:java-jwt:4.6.1")
    implementation("com.password4j:password4j:1.8.4")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.json:json:20260814")
}

kotlin {
    jvmToolchain(25)
}

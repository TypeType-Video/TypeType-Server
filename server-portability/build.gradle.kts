plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":server-core"))
    implementation(project(":server-cache"))
    implementation(project(":server-db"))
    implementation(project(":server-domain"))
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("io.ktor:ktor-utils-jvm:3.5.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("org.jetbrains.exposed:exposed-core:1.5.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.5.0")
}

kotlin {
    jvmToolchain(25)
}

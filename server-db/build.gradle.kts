plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":server-core"))
    api("org.jetbrains.exposed:exposed-core:1.5.0")
    api("org.jetbrains.exposed:exposed-jdbc:1.5.0")
    api("com.zaxxer:HikariCP:7.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    runtimeOnly("org.postgresql:postgresql:42.7.13")
}

kotlin {
    jvmToolchain(25)
}

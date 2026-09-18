plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":server-core"))
    api(project(":server-db"))
    api(project(":server-cache"))
    api("org.testcontainers:testcontainers:2.0.5")
    api("org.testcontainers:testcontainers-postgresql:2.0.5")
    api("io.mockk:mockk:1.14.11")
    api("org.junit.jupiter:junit-jupiter:6.1.3")
    api("io.ktor:ktor-utils-jvm:3.5.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    api("io.ktor:ktor-server-test-host-jvm:3.5.2")
    api("io.ktor:ktor-server-content-negotiation-jvm:3.5.2")
    api("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    api(project(":server-services"))
    api(project(":server-playback"))
    api(project(":server-token-gateway"))
    api(project(":server-sabr"))
}

kotlin {
    jvmToolchain(25)
}

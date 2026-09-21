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
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.2")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    testImplementation(testFixtures(project(":server-db")))
    testImplementation(project(":server-test-support"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

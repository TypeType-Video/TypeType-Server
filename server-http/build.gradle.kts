plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":server-core"))
    api(project(":server-services"))
    api(project(":server-admin"))
    implementation(project(":server-auth"))
    implementation(project(":server-cache"))
    implementation(project(":server-db"))
    implementation(project(":server-domain"))
    implementation(project(":server-downloader"))
    implementation(project(":server-playback"))
    implementation(project(":server-portability"))
    implementation(project(":server-sabr"))
    implementation(project(":server-token-gateway"))
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("io.ktor:ktor-server-websockets-jvm:3.5.2")
    implementation("io.ktor:ktor-server-rate-limit-jvm:3.5.2")
    implementation("io.ktor:ktor-server-status-pages-jvm:3.5.2")
    implementation("io.ktor:ktor-server-compression-jvm:3.5.2")
    testImplementation("io.ktor:ktor-server-compression-jvm:3.5.2")
    implementation("io.ktor:ktor-server-call-logging-jvm:3.5.2")
    implementation("io.ktor:ktor-utils-jvm:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:1d8bf8a6a5dd47d9993b95895dd1be3bb397481a")
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.2")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.2")
    testImplementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.2")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    testImplementation(testFixtures(project(":server-db")))
    testImplementation(testFixtures(project(":server-services")))
    testImplementation(testFixtures(project(":server-core")))
    testImplementation(testFixtures(project(":server-cache")))
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

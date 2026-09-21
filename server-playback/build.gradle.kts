plugins {
    `java-test-fixtures`
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":server-core"))
    api(project(":server-sabr"))
    implementation(project(":server-cache"))
    implementation(project(":server-db"))
    implementation(project(":server-domain"))
    implementation(project(":server-token-gateway"))
    implementation(project(":server-downloader"))
    implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:1d8bf8a6a5dd47d9993b95895dd1be3bb397481a")
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.exposed:exposed-core:1.5.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.5.0")
    implementation("org.slf4j:slf4j-api:2.0.16")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
    testImplementation(testFixtures(project(":server-db")))
    testImplementation(project(":server-test-support"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

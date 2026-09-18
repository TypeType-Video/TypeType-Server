plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":server-sabr"))
    api(project(":server-core"))
    implementation(project(":server-db"))
    implementation(project(":server-downloader"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    compileOnly("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:a395a9ba16ae75987969ed9e7d330c928ad3bc20")
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("io.ktor:ktor-server-websockets-jvm:3.5.2")
    implementation("io.ktor:ktor-client-core-jvm:3.5.2")
    implementation("io.ktor:ktor-client-okhttp-jvm:3.5.2")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.json:json:20260814")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.5.2")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")
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

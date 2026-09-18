plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":server-core"))
    implementation(project(":server-downloader"))
    implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:a395a9ba16ae75987969ed9e7d330c928ad3bc20")
    implementation(project(":server-cache"))
    implementation(project(":server-db"))
    implementation("org.jetbrains.exposed:exposed-core:1.5.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.5.0")
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

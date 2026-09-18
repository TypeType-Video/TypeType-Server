plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":server-core"))
    api(project(":server-services"))
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
    implementation("io.ktor:ktor-utils-jvm:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:a395a9ba16ae75987969ed9e7d330c928ad3bc20")
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.2")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
}

kotlin {
    jvmToolchain(25)
}

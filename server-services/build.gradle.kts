import java.time.Instant
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":server-core"))
    api(project(":server-domain"))
    api(project(":server-playback"))
    api(project(":server-auth"))
    api(project(":server-downloader"))
    implementation(project(":server-auth"))
    implementation(project(":server-cache"))
    implementation(project(":server-db"))
    implementation(project(":server-downloader"))
    implementation(project(":server-portability"))
    implementation(project(":server-sabr"))
    implementation(project(":server-token-gateway"))
    implementation("io.ktor:ktor-server-core-jvm:3.5.2")
    implementation("io.ktor:ktor-server-websockets-jvm:3.5.2")
    implementation("io.ktor:ktor-client-core-jvm:3.5.2")
    implementation("io.ktor:ktor-client-okhttp-jvm:3.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:a395a9ba16ae75987969ed9e7d330c928ad3bc20")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.json:json:20260814")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.jetbrains.exposed:exposed-core:1.5.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.5.0")
    implementation("io.lettuce:lettuce-core:7.7.0.RELEASE")
}

kotlin {
    jvmToolchain(25)
}

val applicationVersion = providers.gradleProperty("appVersion").get()
val buildInfoVersion = applicationVersion.trim().takeUnless { it.isBlank() || it == "unspecified" } ?: "0.0.0-dev"
fun gitRevisionOrUnknown(): String = runCatching {
    providers.exec { commandLine("git", "rev-parse", "HEAD") }
        .standardOutput
        .asText
        .get()
        .trim()
        .ifBlank { "unknown" }
}.getOrElse { "unknown" }
val buildInfoRevision = providers.environmentVariable("GITHUB_SHA")
    .map { it.trim().ifBlank { "unknown" } }
    .getOrElse(gitRevisionOrUnknown())
val buildInfoShortRevision = buildInfoRevision.takeIf { it != "unknown" }?.take(12) ?: "unknown"
val buildInfoBuildTime = providers.environmentVariable("BUILD_TIME")
    .orElse(providers.provider { Instant.now().toString() })
    .get()
val generatedBuildInfoDir = layout.buildDirectory.dir("generated/sources/buildInfo/main")
val generateBuildInfo = tasks.register("generateBuildInfo") {
    inputs.property("version", buildInfoVersion)
    inputs.property("revision", buildInfoRevision)
    inputs.property("shortRevision", buildInfoShortRevision)
    inputs.property("buildTime", buildInfoBuildTime)
    outputs.dir(generatedBuildInfoDir)
    doLast {
        val output = generatedBuildInfoDir.get().file("dev/typetype/server/BuildInfo.kt").asFile
        output.parentFile.mkdirs()
        output.writeText("""
            package dev.typetype.server

            object BuildInfo {
                const val VERSION: String = "${buildInfoVersion.replace("\\", "\\\\").replace("\"", "\\\"")}"
                const val REVISION: String = "${buildInfoRevision.replace("\\", "\\\\").replace("\"", "\\\"")}"
                const val SHORT_REVISION: String = "${buildInfoShortRevision.replace("\\", "\\\\").replace("\"", "\\\"")}"
                const val BUILD_TIME: String = "${buildInfoBuildTime.replace("\\", "\\\\").replace("\"", "\\\"")}"
            }
        """.trimIndent())
    }
}

sourceSets.named("main") { kotlin.srcDir(generatedBuildInfoDir) }

tasks.named("compileKotlin") { dependsOn(generateBuildInfo) }

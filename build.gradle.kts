import java.time.Instant

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("io.ktor.plugin") version "3.5.2"
    id("jacoco")
}

apply(from = "gradle/openapi-validation.gradle.kts")
group = "dev.typetype"
val applicationVersion = providers.gradleProperty("appVersion").get()
require(Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?""").matches(applicationVersion)) {
    "appVersion must be a valid semantic version"
}
version = applicationVersion
application {
    mainClass.set("dev.typetype.server.ApplicationKt")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.2"))
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation(platform("io.netty:netty-bom:4.2.17.Final"))
    constraints {
        implementation("org.jsoup:jsoup:1.23.1") {
            because("CVE-2026-71497 affects PipePipeExtractor's transitive jsoup version")
        }
    }
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")
    implementation("io.ktor:ktor-server-compression-jvm")
    implementation("io.ktor:ktor-server-websockets-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-rate-limit-jvm")
    implementation("ch.qos.logback:logback-classic:1.6.3")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:ca3280f28f3aa0b980b63a2b2d23c362f7616620")
    compileOnly("com.github.TeamNewPipe:nanojson:1d9e1aea9049fc9f85e68b43ba39fe7be1c1f751")
    implementation("org.json:json:20260814")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("io.lettuce:lettuce-core:7.7.0.RELEASE")
    implementation("org.jetbrains.exposed:exposed-core:1.4.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.4.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
    implementation("com.password4j:password4j:1.8.4")
    implementation("com.auth0:java-jwt:4.6.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("io.ktor:ktor-server-content-negotiation-jvm")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
}
val buildInfoVersion = version.toString().trim().takeUnless { it.isBlank() || it == "unspecified" } ?: "0.0.0-dev"
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

tasks.test {
    useJUnitPlatform {
        // network-tagged tests are off by default. Flip on for the live SABR probe:
        //   ./gradlew test --tests SabrProbeTest -Dsabr.probe=true
        if (System.getProperty("sabr.probe") != "true") excludeTags("network")
    }
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    // Forward the gradle-level -Dsabr.probe=true to the test JVM so @EnabledIfSystemProperty can gate
    // the live SABR probe (SabrProbeTest). Default "false" keeps the probe skipped in normal runs.
    systemProperty("sabr.probe", System.getProperty("sabr.probe", "false"))
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.20".toBigDecimal()
            }
        }
    }
}

val verifySabrBoundary = tasks.register("verifySabrBoundary") {
    doLast {
        val adapterRoot = file("src/main/kotlin/dev/typetype/server/sabr")
            .canonicalFile
            .toPath()
        val violations = fileTree("src/main/kotlin")
            .matching { include("**/*.kt") }
            .files
            .filterNot { it.canonicalFile.toPath().startsWith(adapterRoot) }
            .flatMap { source ->
                source.readLines().withIndex()
                    .filter { it.value.contains("org.schabi.newpipe.extractor.services.youtube.sabr") }
                    .map { "${source.path}:${it.index + 1}" }
            }
        check(violations.isEmpty()) {
            "PipePipe SABR imports must stay in the TypeType adapter: ${violations.joinToString()}"
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
    dependsOn(verifySabrBoundary)
}

kotlin {
    jvmToolchain(25)
    sourceSets.named("main") { kotlin.srcDir(generatedBuildInfoDir) }
}

tasks.named("compileKotlin") { dependsOn(generateBuildInfo) }

tasks.withType<AbstractCopyTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

plugins {
    `java-test-fixtures`
    kotlin("jvm")
}

dependencies {
    api(project(":server-core"))
    api("org.jetbrains.exposed:exposed-core:1.5.0")
    api("org.jetbrains.exposed:exposed-jdbc:1.5.0")
    api("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation("org.testcontainers:testcontainers:2.0.5")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation(testFixtures(project(":server-db")))
    "testFixturesImplementation"("org.testcontainers:testcontainers:2.0.5")
    "testFixturesImplementation"("org.testcontainers:testcontainers-postgresql:2.0.5")
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

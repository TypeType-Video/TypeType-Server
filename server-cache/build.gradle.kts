plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    api("io.lettuce:lettuce-core:7.7.0.RELEASE")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

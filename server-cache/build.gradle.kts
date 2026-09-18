plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api("io.lettuce:lettuce-core:7.7.0.RELEASE")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

kotlin {
    jvmToolchain(25)
}

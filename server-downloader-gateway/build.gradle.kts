plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.github.Priveetee.PipePipeExtractor:extractor:a395a9ba16ae75987969ed9e7d330c928ad3bc20")
}

kotlin {
    jvmToolchain(25)
}

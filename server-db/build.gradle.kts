plugins {
    kotlin("jvm")
}

dependencies {
    api(project(":server-core"))
    api("org.jetbrains.exposed:exposed-core:1.5.0")
    api("org.jetbrains.exposed:exposed-jdbc:1.5.0")
    api("com.zaxxer:HikariCP:7.1.0")
    implementation("org.postgresql:postgresql:42.7.13")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
}

kotlin {
    jvmToolchain(25)
}

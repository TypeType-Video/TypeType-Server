rootProject.name = "typetype-server"

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

include(":server-cache")

val localPipePipeExtractor = providers.gradleProperty("pipePipeExtractorPath")
    .orNull
    ?.let { file(it) }

if (localPipePipeExtractor != null) {
    require(localPipePipeExtractor.isDirectory) {
        "pipePipeExtractorPath must point to a PipePipeExtractor checkout"
    }
    includeBuild(localPipePipeExtractor) {
        dependencySubstitution {
            substitute(module("com.github.InfinityLoop1308.PipePipeExtractor:extractor"))
                .using(project(":extractor"))
            substitute(module("com.github.Priveetee.PipePipeExtractor:extractor"))
                .using(project(":extractor"))
        }
    }
}

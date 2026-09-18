rootProject.name = "typetype-server"

include(":server-core")
include(":server-cache")
include(":server-db")
include(":server-downloader-gateway")
include(":server-playback")

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

import java.net.URLClassLoader
import org.gradle.api.GradleException

val openApiValidator = configurations.create("openApiValidator")

dependencies {
    openApiValidator("io.swagger.parser.v3:swagger-parser-v3:2.1.45")
    openApiValidator("org.slf4j:slf4j-nop:2.0.20")
}

tasks.register("validateOpenApi") {
    val specFile = layout.projectDirectory.file("openapi.yaml")
    val specFiles = layout.projectDirectory.asFileTree.matching {
        include("openapi.yaml")
        include("openapi/**/*.yaml")
    }
    inputs.files(specFiles)
    doLast {
        URLClassLoader(openApiValidator.resolve().map { it.toURI().toURL() }.toTypedArray()).use { loader ->
            val optionsClass = loader.loadClass("io.swagger.v3.parser.core.models.ParseOptions")
            val options = optionsClass.getConstructor().newInstance()
            optionsClass.getMethod("setResolve", Boolean::class.javaPrimitiveType).invoke(options, true)
            optionsClass.getMethod("setResolveFully", Boolean::class.javaPrimitiveType).invoke(options, true)

            val parserClass = loader.loadClass("io.swagger.v3.parser.OpenAPIV3Parser")
            val result = parserClass
                .getConstructor()
                .newInstance()
                .let { parser ->
                    parserClass
                        .getMethod("readLocation", String::class.java, MutableList::class.java, optionsClass)
                        .invoke(parser, specFile.asFile.absolutePath, mutableListOf<Any>(), options)
                }
            val messages = result.javaClass.getMethod("getMessages").invoke(result) as List<*>
            val openApi = result.javaClass.getMethod("getOpenAPI").invoke(result)
            if (openApi == null || messages.isNotEmpty()) {
                throw GradleException(messages.joinToString(separator = "\n", prefix = "Invalid OpenAPI spec:\n"))
            }
        }
    }
}

tasks.named("check") {
    dependsOn("validateOpenApi")
}

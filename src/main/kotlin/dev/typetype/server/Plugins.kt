package dev.typetype.server

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.routes.isMultipartSizeLimit
import dev.typetype.server.routes.respondPortabilityError
import dev.typetype.server.services.AuthService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

private const val EXTRACTION_RATE_LIMIT = 60
private const val DEARROW_RATE_LIMIT = 600
private const val STREAMS_RATE_LIMIT = 360
private const val CHANNEL_RATE_LIMIT = 180
private const val PROXY_RATE_LIMIT = 6000
private const val PROXY_STORYBOARD_RATE_LIMIT = 1200
private const val USER_DATA_RATE_LIMIT = 120
private const val MAX_WEBSOCKET_FRAME_BYTES = 64L * 1024L * 1024L
private val RATE_LIMIT_WINDOW = 1.minutes
private val preserveTooManyRequestsBodyAttribute = AttributeKey<Unit>("preserveTooManyRequestsBody")

val EXTRACTION_ZONE = RateLimitName("extraction")
val DEARROW_ZONE = RateLimitName("dearrow")
val STREAMS_ZONE = RateLimitName("streams")
val CHANNEL_ZONE = RateLimitName("channel")
val PROXY_ZONE = RateLimitName("proxy")
val PROXY_STORYBOARD_ZONE = RateLimitName("proxy-storyboard")
val USER_DATA_ZONE = RateLimitName("user-data")

fun Application.configurePlugins(authService: AuthService) {
    installRequestObservability()
    install(CallLogging) {
        format(::requestLogLine)
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 30_000
        maxFrameSize = MAX_WEBSOCKET_FRAME_BYTES
        masking = false
    }
    configureCompression()
    val allowedOrigins = allowedOriginsFromEnv(System.getenv("ALLOWED_ORIGINS"))
    install(CORS) {
        allowOrigins { allowedOrigins.allowsCorsOrigin(it) }
        allowCredentials = true
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
    }
    install(RateLimit) {
        register(EXTRACTION_ZONE) {
            rateLimiter(limit = EXTRACTION_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
            requestKey { call -> call.request.headers["X-Real-IP"] ?: call.request.local.remoteHost }
        }
        register(DEARROW_ZONE) {
            rateLimiter(limit = DEARROW_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
            requestKey { call -> call.request.headers["X-Real-IP"] ?: call.request.local.remoteHost }
        }
        register(STREAMS_ZONE) {
            rateLimiter(limit = STREAMS_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
            requestKey { call -> call.request.headers["X-Real-IP"] ?: call.request.local.remoteHost }
        }
        register(CHANNEL_ZONE) {
            rateLimiter(limit = CHANNEL_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
            requestKey { call -> call.request.headers["X-Real-IP"] ?: call.request.local.remoteHost }
        }
        register(PROXY_ZONE) {
            rateLimiter(limit = PROXY_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
            requestKey { call -> call.request.headers["X-Real-IP"] ?: call.request.local.remoteHost }
        }
        register(PROXY_STORYBOARD_ZONE) {
            rateLimiter(limit = PROXY_STORYBOARD_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
            requestKey { call -> call.request.headers["X-Real-IP"] ?: call.request.local.remoteHost }
        }
        register(USER_DATA_ZONE) {
            rateLimiter(limit = USER_DATA_RATE_LIMIT, refillPeriod = RATE_LIMIT_WINDOW)
            requestKey { call -> userDataRateLimitKey(call, authService) }
        }
    }
    configureStatusPages()
}

internal fun ApplicationCall.preserveTooManyRequestsBody() {
    attributes.put(preserveTooManyRequestsBodyAttribute, Unit)
}

internal fun Application.configureStatusPages() {
    val log = LoggerFactory.getLogger("RequestLogger")
    install(StatusPages) {
        status(HttpStatusCode.TooManyRequests) { call, status ->
            if (call.attributes.contains(preserveTooManyRequestsBodyAttribute)) return@status
            if (!call.response.headers.contains(HttpHeaders.RetryAfter)) call.response.headers.append(HttpHeaders.RetryAfter, "60")
            call.respond(status, ErrorResponse("Too many requests", "rate_limited"))
        }
        exception<IllegalArgumentException> { call, cause ->
            log.warn("Bad request: ${cause.message}")
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request", "bad_request"))
        }
        exception<Throwable> { call, cause ->
            if (cause is io.ktor.utils.io.ClosedWriteChannelException) return@exception
            if (cause is kotlinx.coroutines.CancellationException) throw cause
            // Ktor's multipart producer can fail outside the route's receive block.
            if (call.request.path() == "/portability/imports" && cause.isMultipartSizeLimit()) {
                call.respondPortabilityError(dev.typetype.server.portability.PortabilityUploadTooLargeException())
                return@exception
            }
            log.error("Unhandled exception requestId=${call.requestId()} path=${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error", "internal_error"))
        }
    }
}

import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.features.StatusPages.Configuration.Companion.Default
import io.ktor.features.StatusPages.Status
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class UrlMapping(val shortCode: String, val originalUrl: String)

data class Analytics(val timestamp: String, val ipAddress: String)

val urlMappings = mutableMapOf<String, UrlMapping>()
val analyticsData = mutableMapOf<String, MutableList<Analytics>>()

fun Application.module() {
    install(ContentNegotiation) {
        gson {}
    }

    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, "Internal Server Error: ${cause.localizedMessage}")
        }
    }

    routing {
        static {
            resource("public/index.html")
        }

        route("/") {
            get {
                call.respond("Welcome to the URL Shortener Service")
            }
        }

        route("/shorten") {
            post {
                val request = call.receive<UrlRequest>()
                val originalUrl = request.originalUrl

                if (originalUrl.isNullOrEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request")
                } else {
                    val shortCode = generateUniqueShortCode(originalUrl)
                    urlMappings[shortCode] = UrlMapping(shortCode, originalUrl)

                    val shortUrl = "${call.request.local.scheme}://${call.request.local.host}:${call.request.local.port}/$shortCode"
                    call.respond(mapOf("short_url" to shortUrl))
                }
            }
        }

        route("/{shortCode}") {
            get {
                val shortCode = call.parameters["shortCode"]

                if (shortCode != null) {
                    val mapping = urlMappings[shortCode]

                    if (mapping != null) {
                        // Log analytics data
                        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        val ipAddress = call.request.origin.remoteHost

                        analyticsData.computeIfAbsent(shortCode) { mutableListOf() }
                        analyticsData[shortCode]!!.add(Analytics(timestamp, ipAddress))

                        call.respondRedirect(mapping.originalUrl, permanent = false)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Short URL not found")
                    }
                }
            }
        }

        route("/analytics/{shortCode}") {
            get {
                val shortCode = call.parameters["shortCode"]

                if (shortCode != null) {
                    val analytics = analyticsData[shortCode]

                    if (analytics != null) {
                        call.respond(analytics)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "No analytics data found for the given short code")
                    }
                }
            }
        }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

data class UrlRequest(val originalUrl: String)

fun generateShortCode(originalUrl: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(originalUrl.toByteArray())
    return hash.joinToString("") { "%02x".format(it) }.substring(0, 8)
}

fun checkCollision(shortCode: String): Boolean {
    return urlMappings.containsKey(shortCode)
}

fun generateUniqueShortCode(originalUrl: String): String {
    var tries = 0

    while (tries < 10) {
        val shortCode = generateShortCode(originalUrl + tries)

        if (!checkCollision(shortCode)) {
            return shortCode
        }

        tries++
    }

    throw RuntimeException("Failed to generate a unique short code")
}

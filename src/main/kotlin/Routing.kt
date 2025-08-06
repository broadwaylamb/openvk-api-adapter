package com.broadwaylamb.openvk.api.adapter

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private object TrustAllX509TrustManager : X509TrustManager {
    override fun getAcceptedIssuers(): Array<X509Certificate?> = arrayOfNulls(0)

    override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}

    override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
}

private val ovkClient = HttpClient(Java) {
    defaultRequest {
        url("https://ovk.to")
        headers["Host"] = "ovk.to"
    }
    engine {
        config {
            sslContext(
                SSLContext.getInstance("TLS")
                    .apply {
                        init(null, arrayOf(TrustAllX509TrustManager), SecureRandom())
                    }
            )
        }
    }
}

private suspend inline fun <reified Result : Any> RoutingContext.transform(
    method: HttpMethod,
    uri: String,
    transformation: (HttpResponse) -> Result,
) {
    try {
        val response = ovkClient.request {
            this.method = method
            headers.appendAll(call.request.headers)
            setBody(call.receive<ByteArray>())
            url(uri)
        }
        call.respond(transformation(response))
    } catch (e: Throwable) {
        call.application.log.error(e)
    }
}

private suspend fun RoutingContext.proxy(
    method: HttpMethod,
    buildRequest: HttpRequestBuilder.() -> Unit,
) {
    try {
        val response = ovkClient.request {
            this.method = method
            headers.appendAll(call.request.headers)
            setBody(call.receive<ByteArray>())
            buildRequest()
        }

        val proxiedHeaders = response.headers
        val contentType = proxiedHeaders[HttpHeaders.ContentType]

        val contentLength = proxiedHeaders[HttpHeaders.ContentLength]
        call.respond(object : OutgoingContent.WriteChannelContent() {
            override val contentLength: Long? = contentLength?.toLong()
            override val contentType: ContentType? =
                contentType?.let { ContentType.parse(it) }
            override val headers: Headers = Headers.build {
                appendAll(proxiedHeaders.filter { key, _ ->
                    !key.equals(HttpHeaders.TransferEncoding, ignoreCase = true ) && !key.equals( HttpHeaders.ContentType, ignoreCase = true ) && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                })
            }
            override val status: HttpStatusCode
                get() = response.status

            override suspend fun writeTo(channel: ByteWriteChannel) {
                channel.writeByteArray(response.readRawBytes())
                channel.flushAndClose()
            }
        })
    } catch (e: Throwable) {
        call.application.log.error(e)
    }
}

fun Application.configureRouting() {
    routing {
        get("/token/") {
            proxy(HttpMethod.Get) {
                url {
                    path("token")
                    parameters.appendAll(call.queryParameters)
                }
            }
        }
        get("/nim*") {
            proxy(HttpMethod.Get) {
                url(call.request.uri)
            }
        }
        post("/method/*") {
            proxy(HttpMethod.Post) {
                url(call.request.uri)
            }
        }
    }
}

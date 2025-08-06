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
import io.ktor.utils.io.*


private val ovkClient = HttpClient(Java) {
    defaultRequest {
        url("https://ovk.to")
    }
}

//private fun Routing.proxyGet(path: String) {
//    get(path) {
//        val ovkResponse = ovkClient.request {
//            request
//        }
////        call.respondBytes
//    }
//}

fun Application.configureRouting() {
    routing {
        get("/token/") {
            val response = ovkClient.get("/token") {
                url.parameters.appendAll(call.request.rawQueryParameters)
            }
            call.respondBytes(response.readRawBytes())
        }
        post("/method/*") {
            try {
                val response = ovkClient.post(call.request.uri) {
                    headers.appendAll(call.request.headers)
                    headers["Host"] = "ovk.to"
                    setBody(call.receive<ByteArray>())
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
                println(e)
            }
        }
    }
}

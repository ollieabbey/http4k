package org.http4k.server

import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.http4k.bridge.KtorToHttp4kApplicationPlugin
import org.http4k.bridge.asHttp4k
import org.http4k.bridge.fromHttp4K
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.server.ServerConfig.StopMode.Graceful
import org.http4k.server.ServerConfig.StopMode.Immediate
import java.util.concurrent.TimeUnit.MILLISECONDS

@Suppress("EXPERIMENTAL_API_USAGE")
class KtorNetty(val port: Int = 8000, override val stopMode: ServerConfig.StopMode) : ServerConfig {
    constructor(port: Int = 8000) : this(port, Immediate)

    override fun toServer(http: HttpHandler): Http4kServer = object : Http4kServer {
        private val engine = embeddedServer(Netty, port) {
            install(createApplicationPlugin(name = "http4k") {
                onCall {
                    it.response.fromHttp4K(it.request.asHttp4k()?.let(http) ?: Response(Status.NOT_IMPLEMENTED))
                }
            })
        }

        override fun start() = apply {
            engine.start()
        }

        override fun stop() = apply {
            when (stopMode) {
                is Immediate -> engine.stop(0, 0, MILLISECONDS)
                is Graceful -> engine.stop(stopMode.timeout.toMillis(), stopMode.timeout.toMillis(), MILLISECONDS)
            }
        }

        override fun port() = engine.engineConfig.connectors.first().port
    }
}

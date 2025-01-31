package org.http4k.postbox

import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Uri
import org.http4k.db.InMemoryTransactor
import org.http4k.events.StdOutEvents
import org.http4k.postbox.RequestIdResolvers.fromHeader
import org.http4k.postbox.RequestIdResolvers.fromPath
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.SunHttp
import org.http4k.server.asServer


fun main() {
    ThirdPartySlowSmsService().asServer(SunHttp(8000)).start()

    val transactor = InMemoryTransactor<Postbox>(InMemoryPostbox()).also { transactor ->
        // please notice in-memory transactor locks the postbox so multiple threads can't access it at the same time
        PostboxProcessing(transactor, JavaHttpClient(), events = StdOutEvents).start()
    }

    val transactionalOutbox = PostboxHandlers(transactor,
        { Uri.of("/api/notificationStatus/${it.value}") }
    )

    routes(
        "/api/{orderId}/notify" bind POST to NotificationHandler(transactionalOutbox.intercepting(fromHeader("x-order-id"))),
        "/api/notificationStatus/{orderId}" bind GET to transactionalOutbox.status(fromPath("orderId"))
    ).asServer(SunHttp(9000)).start()
}

fun ThirdPartySlowSmsService() = routes("/sms/send" bind POST to
    { req: Request -> Thread.sleep(10000); Response(OK).body("SMS sent. Message= ${req.bodyString()}") })

private fun NotificationHandler(smsClient: HttpHandler) = { request: Request ->
    val message = request.bodyString()

    // Makes the request to the third party service.
    // If the client is a transactional outbox, rather than the real thing:
    // On first call it'll store the request and return a 202 with a Link header to check the status of the request.
    // On subsequent calls it'll return either 202 again or the response from the third party service.
    smsClient(
        Request(POST, "http://localhost:8000/sms/send")
            .header("x-order-id", request.path("orderId")!!)
            .body("Notified a user with message '$message'")
    )

    Response(OK).body("Notification accepted")
}

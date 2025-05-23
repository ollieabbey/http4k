package org.http4k.mcp.client.jsonrpc

import org.http4k.client.JavaHttpClient
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.ClientFilters
import org.http4k.mcp.client.McpClientContract
import org.http4k.mcp.server.jsonrpc.JsonRpcMcp
import org.http4k.mcp.server.jsonrpc.JsonRpcSessions
import org.http4k.mcp.server.protocol.McpProtocol
import org.http4k.mcp.server.security.BearerAuthMcpSecurity
import org.http4k.routing.poly

class JsonRpcMcpClientTest : McpClientContract<Unit> {

    override val doesNotifications = false

    override fun clientSessions() = JsonRpcSessions()

    override fun clientFor(port: Int) = JsonRpcMcpClient(
        Uri.of("http://localhost:${port}/jsonrpc"),
        ClientFilters.BearerAuth("123").then(JavaHttpClient()),
    )

    override fun toPolyHandler(protocol: McpProtocol<Unit>) = poly(
        JsonRpcMcp(
            protocol,
            BearerAuthMcpSecurity { it == "123" }
        )
    )
}

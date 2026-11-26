package io.legado.app.web.mcp

import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class McpApplicationTest {

    // 超时护栏：testApplication 内构建 MCP SDK Server，后台会话协程若泄漏会拖住测试线程；
    // 10s 上限留出 Ktor 引擎启动余量，超时即判失败，不再拖到分片 45 分钟兜底。
    @Test(timeout = 10_000)
    fun missingOrWrongTokenRejectsEveryMcpMethodBeforeParsing() = testApplication {
        application { testMcpApplication() }

        listOf(HttpMethod.Get, HttpMethod.Post, HttpMethod.Delete).forEach { method ->
            listOf(null, "wrong").forEach { token ->
                val response = client.request(McpAccess.PATH) {
                    this.method = method
                    header(HttpHeaders.Host, "localhost")
                    header(HttpHeaders.Accept, "application/json, text/event-stream")
                    token?.let { header(McpAccess.TOKEN_HEADER, it) }
                    contentType(ContentType.Application.Json)
                    setBody("not-json")
                }

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            }
        }

        val trailingSlash = client.request("${McpAccess.PATH}/") {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "localhost")
        }
        assertEquals(HttpStatusCode.Unauthorized, trailingSlash.status)

        val encodedPath = client.request("/m%63p") {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "localhost")
        }
        assertEquals(HttpStatusCode.Unauthorized, encodedPath.status)
    }

    @Test(timeout = 10_000)
    fun validTokenReachesTheSdkRoute() = testApplication {
        application { testMcpApplication() }

        val response = client.request(McpAccess.PATH) {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header(McpAccess.TOKEN_HEADER, "secret")
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test(timeout = 10_000)
    fun disabledTokenProtectionAllowsMissingTokenButStillChecksHost() = testApplication {
        application { testMcpApplication(tokenRequired = false) }

        val localResponse = client.request(McpAccess.PATH) {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.BadRequest, localResponse.status)

        val hostileResponse = client.request(McpAccess.PATH) {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "example.test")
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.Forbidden, hostileResponse.status)

        val hostileOriginResponse = client.request(McpAccess.PATH) {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://example.test")
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.Forbidden, hostileOriginResponse.status)
    }

    @Test(timeout = 10_000)
    fun validTokenStillRequiresAllowedHostAndOrigin() = testApplication {
        application { testMcpApplication() }

        val hostileHost = client.request(McpAccess.PATH) {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "example.test")
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header(McpAccess.TOKEN_HEADER, "secret")
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.Forbidden, hostileHost.status)

        val hostileOrigin = client.request(McpAccess.PATH) {
            method = HttpMethod.Post
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://example.test")
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            header(McpAccess.TOKEN_HEADER, "secret")
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }
        assertEquals(HttpStatusCode.Forbidden, hostileOrigin.status)
    }

    private fun io.ktor.server.application.Application.testMcpApplication(
        tokenRequired: Boolean = true,
    ) {
        configureMcp(
            tokenRequiredProvider = { tokenRequired },
            tokenProvider = { "secret" },
            unauthorizedMessage = { "unauthorized" },
            allowedHosts = listOf("localhost"),
            allowedOrigins = listOf("http://localhost"),
        ) {
            Server(
                serverInfo = Implementation(name = "test", version = "1"),
                options = ServerOptions(capabilities = ServerCapabilities()),
            )
        }
    }
}

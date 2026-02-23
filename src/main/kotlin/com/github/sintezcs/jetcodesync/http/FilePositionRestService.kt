package com.github.sintezcs.jetcodesync.http

import com.intellij.openapi.diagnostic.thisLogger
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService
import com.github.sintezcs.jetcodesync.services.FilePositionStateService

/**
 * REST service that exposes the current file position via HTTP endpoint.
 *
 * Endpoint URL: http://localhost:63342/api/filePosition
 * Method: GET
 * Response format: JSON
 *
 * Example response:
 * {
 *   "filePath": "/path/to/file.kt",
 *   "line": 42,
 *   "column": 10
 * }
 */
class FilePositionRestService : RestService() {

    init {
        thisLogger().warn("!!! FilePositionRestService INSTANCE CREATED !!!")
    }

    /**
     * The service name that becomes part of the URL path.
     * Accessible at: http://localhost:63342/api/filePosition
     */
    override fun getServiceName(): String = "filePosition"

    override fun isMethodSupported(method: HttpMethod): Boolean {
        return method == HttpMethod.GET
    }

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): String? {
        thisLogger().info("=== HTTP Request Received ===")

        try {
            val stateService = FilePositionStateService.getInstance()
            val state = stateService.getCurrentState()

            val response = mapOf(
                "filePath" to state.filePath,
                "line" to state.line,
                "column" to state.column
            )

            val jsonResponse = gson.toJson(response)
            thisLogger().info("Returning JSON response: $jsonResponse")

            // Send JSON response
            val bytes = jsonResponse.toByteArray(Charsets.UTF_8)
            val httpResponse = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(bytes)
            )
            httpResponse.headers().set("Content-Type", "application/json; charset=utf-8")
            httpResponse.headers().set("Content-Length", bytes.size)
            httpResponse.headers().set("Access-Control-Allow-Origin", "*")
            sendResponse(request, context, httpResponse)
            return null
        } catch (e: Exception) {
            thisLogger().error("Error handling HTTP request", e)
            return e.message ?: "Internal error"
        }
    }

    override fun isHostTrusted(
        request: FullHttpRequest,
        urlDecoder: QueryStringDecoder
    ): Boolean {
        // Allow all localhost connections for VS Code integration
        return true
    }
}

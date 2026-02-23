package com.github.sintezcs.jetcodesync.http

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.ProjectManager
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.QueryStringDecoder
import org.jetbrains.ide.RestService

class IdeStatusRestService : RestService() {

    override fun getServiceName(): String = "status"

    override fun isMethodSupported(method: HttpMethod): Boolean = method == HttpMethod.GET

    override fun execute(
        urlDecoder: QueryStringDecoder,
        request: FullHttpRequest,
        context: ChannelHandlerContext
    ): String? {
        val ideName = ApplicationInfo.getInstance().fullApplicationName
        val projectName = ProjectManager.getInstance().openProjects.firstOrNull()?.name

        val response = mapOf(
            "ideName" to ideName,
            "projectName" to projectName
        )
        val jsonResponse = gson.toJson(response)

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
    }

    override fun isHostTrusted(
        request: FullHttpRequest,
        urlDecoder: QueryStringDecoder
    ): Boolean = true
}

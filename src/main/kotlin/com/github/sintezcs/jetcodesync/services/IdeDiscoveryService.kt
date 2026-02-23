package com.github.sintezcs.jetcodesync.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.jetbrains.ide.BuiltInServerManager
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class SyncTarget(
    val port: Int,
    val ideName: String,
    val projectName: String?
) {
    val displayName: String
        get() = if (projectName != null) "$ideName — $projectName" else ideName

    override fun toString(): String = displayName
}

@Service(Service.Level.APP)
class IdeDiscoveryService {

    companion object {
        val PORT_RANGE = 63340..63359
        fun getInstance(): IdeDiscoveryService = service()
    }

    @Volatile
    var detectedTargets: List<SyncTarget> = emptyList()
        private set

    @Volatile
    var selectedTarget: SyncTarget? = null

    val listeners = mutableListOf<() -> Unit>()

    private val gson = Gson()
    private val executor = Executors.newFixedThreadPool(PORT_RANGE.count()) { r ->
        Thread(r, "JetCodeSync-Discovery").apply { isDaemon = true }
    }

    fun scan() {
        val ownPort = BuiltInServerManager.getInstance().port
        thisLogger().info("Scanning ports ${PORT_RANGE.first}..${PORT_RANGE.last}, own port: $ownPort")

        val futures = PORT_RANGE.filter { it != ownPort }.map { port ->
            executor.submit<SyncTarget?> { probePort(port) }
        }

        val targets = futures.mapNotNull { future ->
            try {
                future.get(2, TimeUnit.SECONDS)
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.port }

        detectedTargets = targets
        thisLogger().info("Discovered ${targets.size} targets: ${targets.map { "${it.ideName}:${it.port}" }}")

        // Restore persisted selection or auto-select first
        val savedPort = SyncSettingsService.getInstance().state.selectedPort
        selectedTarget = targets.find { it.port == savedPort } ?: targets.firstOrNull()

        listeners.forEach { it() }
    }

    fun getEndpointUrl(): String? {
        val target = selectedTarget ?: return null
        return "http://localhost:${target.port}/api/filePosition"
    }

    private fun probePort(port: Int): SyncTarget? {
        return try {
            val json = HttpRequests.request("http://localhost:$port/api/status")
                .connectTimeout(300)
                .readTimeout(500)
                .readString()
            val obj = gson.fromJson(json, JsonObject::class.java)
            val ideName = obj.get("ideName")?.asString ?: return null
            val projectName = obj.get("projectName")?.let { if (it.isJsonNull) null else it.asString }
            SyncTarget(port, ideName, projectName)
        } catch (_: Exception) {
            null
        }
    }
}

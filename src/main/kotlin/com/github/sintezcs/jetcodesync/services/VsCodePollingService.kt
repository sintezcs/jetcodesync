package com.github.sintezcs.jetcodesync.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.io.HttpRequests
import com.google.gson.Gson
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class SyncStatus {
    DISABLED, PAUSED, POLLING, ERROR
}

@Service(Service.Level.APP)
class VsCodePollingService {

    private data class PolledPosition(
        val filePath: String?,
        val line: Int,
        val column: Int
    )

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "JetCodeSync-Poller").apply { isDaemon = true }
    }
    private var pollTask: ScheduledFuture<*>? = null
    private val lastPosition = AtomicReference<PolledPosition?>(null)
    private val gson = Gson()

    @Volatile
    var status: SyncStatus = SyncStatus.PAUSED
        private set

    val statusListeners = mutableListOf<() -> Unit>()

    fun startPolling() {
        val settings = SyncSettingsService.getInstance().state
        if (!settings.syncEnabled) {
            updateStatus(SyncStatus.DISABLED)
            return
        }
        if (pollTask != null) return

        val url = IdeDiscoveryService.getInstance().getEndpointUrl()
        if (url == null) {
            updateStatus(SyncStatus.ERROR)
            thisLogger().info("No sync target available")
            return
        }

        updateStatus(SyncStatus.POLLING)
        pollTask = scheduler.scheduleAtFixedRate(::poll, 0, 1, TimeUnit.SECONDS)
        thisLogger().info("Started polling: $url")
    }

    fun stopPolling() {
        pollTask?.cancel(false)
        pollTask = null
        val settings = SyncSettingsService.getInstance().state
        updateStatus(if (settings.syncEnabled) SyncStatus.PAUSED else SyncStatus.DISABLED)
        thisLogger().info("Stopped polling")
    }

    fun navigateToLastPosition() {
        val position = lastPosition.get() ?: return
        navigateTo(position)
    }

    private fun navigateTo(position: PolledPosition) {
        val filePath = position.filePath ?: return

        ApplicationManager.getApplication().invokeLater {
            val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return@invokeLater
            val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath)
            if (virtualFile == null) {
                thisLogger().warn("File not found: $filePath")
                return@invokeLater
            }

            val editors = FileEditorManager.getInstance(project).openFile(virtualFile, true)
            val textEditor = editors.filterIsInstance<TextEditor>().firstOrNull() ?: return@invokeLater
            val editor = textEditor.editor
            val logicalPosition = LogicalPosition(
                maxOf(position.line - 1, 0),
                maxOf(position.column - 1, 0)
            )
            editor.caretModel.moveToLogicalPosition(logicalPosition)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            thisLogger().info("Navigated to $filePath:${position.line}:${position.column}")
        }
    }

    private fun poll() {
        try {
            val url = IdeDiscoveryService.getInstance().getEndpointUrl() ?: return
            val json = HttpRequests.request(url)
                .connectTimeout(1000)
                .readTimeout(1000)
                .readString()
            val position = gson.fromJson(json, PolledPosition::class.java)
            val previous = lastPosition.getAndSet(position)
            if (status == SyncStatus.ERROR) updateStatus(SyncStatus.POLLING)

            // Navigate immediately on every change so IDE is already at the right position
            if (previous == null || previous.filePath != position.filePath
                || previous.line != position.line || previous.column != position.column) {
                navigateTo(position)
            }
        } catch (e: Exception) {
            updateStatus(SyncStatus.ERROR)
            thisLogger().debug("Poll failed: ${e.message}")
        }
    }

    private fun updateStatus(newStatus: SyncStatus) {
        status = newStatus
        statusListeners.forEach { it() }
    }

    companion object {
        fun getInstance(): VsCodePollingService = service()
    }
}

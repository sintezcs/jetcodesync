package com.github.sintezcs.jetcodesync.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.atomic.AtomicReference

/**
 * Application-level service that maintains the current file position state.
 * Thread-safe using AtomicReference for lock-free updates.
 */
@Service(Service.Level.APP)
class FilePositionStateService {

    init {
        thisLogger().info("FilePositionStateService initialized")
    }

    /**
     * Immutable state holder for file position information.
     * @property filePath Absolute path to the current file, or null if no file is open
     * @property line Current line number (1-based, matching editor display)
     * @property column Current column number (1-based, matching editor display)
     */
    data class FilePositionState(
        val filePath: String?,
        val line: Int,
        val column: Int
    )

    private val currentState = AtomicReference(FilePositionState(null, 1, 1))

    /**
     * Updates the current file position state atomically.
     * Thread-safe and can be called from any thread (EDT, background, etc.)
     *
     * @param filePath Absolute path to the file, or null if no file is open
     * @param line Line number (1-based)
     * @param column Column number (1-based)
     */
    fun updatePosition(filePath: String?, line: Int, column: Int) {
        val newState = FilePositionState(filePath, line, column)
        currentState.set(newState)
        thisLogger().info("Position updated: file='${filePath?.substringAfterLast("/") ?: "null"}', line=$line, column=$column")
        thisLogger().debug("Full path: $filePath")
    }

    /**
     * Retrieves the current file position state.
     * Thread-safe and wait-free.
     *
     * @return Current immutable state snapshot
     */
    fun getCurrentState(): FilePositionState {
        val state = currentState.get()
        thisLogger().debug("State retrieved: file='${state.filePath?.substringAfterLast("/") ?: "null"}', line=${state.line}, column=${state.column}")
        return state
    }

    companion object {
        /**
         * Gets the application-level singleton instance of this service.
         */
        fun getInstance(): FilePositionStateService = service()
    }
}

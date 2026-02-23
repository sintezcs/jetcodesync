package com.github.sintezcs.jetcodesync.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.github.sintezcs.jetcodesync.services.FilePositionStateService

/**
 * Listener that tracks file switch events (tab changes and file opens).
 * Subscribes to the project message bus to receive file editor events.
 */
class FileEditorListener : ProjectActivity {

    override suspend fun execute(project: Project) {
        thisLogger().info("Initializing FileEditorListener for project: ${project.name}")

        // Subscribe to file editor manager events
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    handleFileChange(event)
                }
            }
        )
    }

    /**
     * Handles file selection changes (tab switches, new files opened).
     * Extracts the file path and initial caret position, then updates the state service.
     */
    private fun handleFileChange(event: FileEditorManagerEvent) {
        try {
            thisLogger().info("=== File Selection Changed ===")

            val newFile = event.newFile
            val oldFile = event.oldFile

            thisLogger().info("Old file: ${oldFile?.path ?: "null"}")
            thisLogger().info("New file: ${newFile?.path ?: "null"}")

            if (newFile == null) {
                // No file selected (all files closed)
                thisLogger().info("No file selected - resetting position")
                FilePositionStateService.getInstance().updatePosition(null, 1, 1)
                return
            }

            val filePath = newFile.path
            val editor = event.newEditor

            thisLogger().debug("Editor type: ${editor?.javaClass?.simpleName}")

            // Only process text editors (skip binary files, images, etc.)
            if (editor is TextEditor) {
                val caret = editor.editor.caretModel.primaryCaret
                val line = caret.logicalPosition.line + 1  // Convert 0-based to 1-based
                val column = caret.logicalPosition.column + 1  // Convert 0-based to 1-based

                thisLogger().info("Text editor - updating position to line=$line, column=$column")
                FilePositionStateService.getInstance().updatePosition(filePath, line, column)
            } else {
                // Non-text editor (e.g., image, binary file) - just track file path
                thisLogger().info("Non-text editor - setting default position")
                FilePositionStateService.getInstance().updatePosition(filePath, 1, 1)
            }

            thisLogger().info("=== File Selection Change Handled ===")
        } catch (e: Exception) {
            thisLogger().error("Failed to handle file change event", e)
        }
    }
}

package com.github.sintezcs.jetcodesync.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.project.Project
import com.github.sintezcs.jetcodesync.services.FilePositionStateService
import kotlinx.coroutines.CoroutineScope

/**
 * Listener that tracks real-time cursor position changes within editors.
 * Registers globally with EditorFactory to receive caret events from all editors.
 */
open class CaretPositionListener : CaretListener {

    override fun caretPositionChanged(event: CaretEvent) {
        super.caretPositionChanged(event)
        handleCaretChange(event)
    }

    /**
     * Handles caret position change events.
     * Extracts the file path and new cursor position, then updates the state service.
     * This method is called frequently (on every cursor movement), so it must be lightweight.
     */
    private fun handleCaretChange(event: CaretEvent) {
        try {
            val editor = event.editor
            val document = editor.document

            println("Caret position changed event received")

            // Get the file associated with this editor's document
            val virtualFile = FileDocumentManager.getInstance().getFile(document)
            if (virtualFile == null) {
                // Document not associated with a file (e.g., scratch file, console)
                println("No virtual file for document - skipping")
                return
            }

            val filePath = virtualFile.path
            val oldPosition = event.oldPosition
            val position = event.newPosition
            val line = position.line + 1     // Convert 0-based to 1-based
            val column = position.column + 1  // Convert 0-based to 1-based

            println("Caret moved from (${oldPosition.line},${oldPosition.column}) to ($line,$column) in ${filePath.substringAfterLast("/")}")

            // Update state atomically - this is a lock-free operation
            FilePositionStateService.getInstance().updatePosition(filePath, line, column)
        } catch (e: Exception) {
            // Don't let exceptions propagate - would break IDE
            println("Failed to handle caret position change: ${e.message}")
            thisLogger().error(
                "Failed to handle caret position change",
                e
            )
        }
    }
}

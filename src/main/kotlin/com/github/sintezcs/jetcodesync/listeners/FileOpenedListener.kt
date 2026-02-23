package com.github.sintezcs.jetcodesync.listeners

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.vfs.VirtualFile

class FileOpenedListener : FileOpenedSyncListener {
    override fun fileOpenedSync(
        source: FileEditorManager,
        file: VirtualFile,
        editorsWithProviders: List<FileEditorWithProvider>
    ) {
        super.fileOpenedSync(source, file, editorsWithProviders)

        println("File opened: ${file.path}")

        // Log the message
        thisLogger().info("File opened: ${file.path}");
    }
}
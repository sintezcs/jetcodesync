package com.github.sintezcs.jetcodesync.startup

import com.github.sintezcs.jetcodesync.listeners.CaretPositionListener
import com.github.sintezcs.jetcodesync.listeners.SelectionPositionListener
import com.github.sintezcs.jetcodesync.services.IdeDiscoveryService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class MyProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addCaretListener(CaretPositionListener(), project)
        multicaster.addSelectionListener(SelectionPositionListener(), project)

        // Scan for sync targets after a short delay to let the built-in server start
        ApplicationManager.getApplication().executeOnPooledThread {
            Thread.sleep(2000)
            IdeDiscoveryService.getInstance().scan()
        }
    }

}
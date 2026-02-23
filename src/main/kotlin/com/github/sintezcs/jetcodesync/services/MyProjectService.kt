package com.github.sintezcs.jetcodesync.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.github.sintezcs.jetcodesync.MyBundle
import com.intellij.openapi.fileEditor.FileEditorManagerListener

@Service(Service.Level.PROJECT)
class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
        println("Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.")

        // track event of cursor movement

    }

    fun getRandomNumber() = (1..100).random()
}

package com.github.sintezcs.jetcodesync.listeners

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener

open class SelectionPositionListener: SelectionListener {

    override fun selectionChanged(event: SelectionEvent) {
        super.selectionChanged(event)
        println("Selection position changed event received: ")
        println(event.toString())
    }
}
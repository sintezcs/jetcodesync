package com.github.sintezcs.jetcodesync.listeners

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.IdeFrame
import com.github.sintezcs.jetcodesync.services.VsCodePollingService
import java.util.Timer
import kotlin.concurrent.schedule

class WindowFocusListener : ApplicationActivationListener {

    override fun applicationDeactivated(ideFrame: IdeFrame) {
        thisLogger().info("IDE lost focus — starting polling")
        VsCodePollingService.getInstance().startPolling()
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
        thisLogger().info("IDE gained focus — stopping polling and navigating")
        val pollingService = VsCodePollingService.getInstance()
        pollingService.stopPolling()
        // Navigate immediately
        pollingService.navigateToLastPosition()
        // Navigate again after a short delay to override IntelliJ's focus caret restoration
        Timer().schedule(200) {
            pollingService.navigateToLastPosition()
        }
    }
}

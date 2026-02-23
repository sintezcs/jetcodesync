package com.github.sintezcs.jetcodesync.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.*
import com.github.sintezcs.jetcodesync.services.IdeDiscoveryService
import com.github.sintezcs.jetcodesync.services.SyncSettingsService
import com.github.sintezcs.jetcodesync.services.SyncStatus
import com.github.sintezcs.jetcodesync.services.SyncTarget
import com.github.sintezcs.jetcodesync.services.VsCodePollingService
import org.jetbrains.ide.BuiltInServerManager
import java.awt.Font
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val settings = SyncSettingsService.getInstance()
        val pollingService = VsCodePollingService.getInstance()
        val discoveryService = IdeDiscoveryService.getInstance()
        val port = BuiltInServerManager.getInstance().port

        val statusLabel = JBLabel(formatStatus(pollingService.status)).apply {
            foreground = statusColor(pollingService.status)
            font = font.deriveFont(Font.BOLD)
        }

        pollingService.statusListeners.add {
            statusLabel.text = formatStatus(pollingService.status)
            statusLabel.foreground = statusColor(pollingService.status)
        }

        val comboModel = DefaultComboBoxModel<SyncTarget>()
        val comboBox = JComboBox(comboModel).apply {
            addActionListener {
                val selected = selectedItem as? SyncTarget ?: return@addActionListener
                discoveryService.selectedTarget = selected
                settings.state.selectedPort = selected.port
            }
        }

        // Populate combo from current targets
        fun refreshCombo() {
            ApplicationManager.getApplication().invokeLater {
                comboModel.removeAllElements()
                discoveryService.detectedTargets.forEach { comboModel.addElement(it) }
                val selected = discoveryService.selectedTarget
                if (selected != null && comboModel.getIndexOf(selected) >= 0) {
                    comboModel.selectedItem = selected
                }
            }
        }

        discoveryService.listeners.add(::refreshCombo)
        refreshCombo()

        val content = ContentFactory.getInstance().createContent(
            buildPanel(statusLabel, comboBox, settings, pollingService, discoveryService, port),
            null,
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    private fun buildPanel(
        statusLabel: JBLabel,
        comboBox: JComboBox<SyncTarget>,
        settings: SyncSettingsService,
        pollingService: VsCodePollingService,
        discoveryService: IdeDiscoveryService,
        port: Int
    ): JComponent = panel {
        group("Sync") {
            row {
                cell(statusLabel)
            }
            row {
                checkBox("Enable sync")
                    .applyToComponent {
                        isSelected = settings.state.syncEnabled
                        addActionListener {
                            settings.state.syncEnabled = isSelected
                            if (!isSelected) pollingService.stopPolling()
                        }
                    }
            }
        }
        group("Sync Target") {
            row {
                cell(comboBox)
                    .resizableColumn()
                    .align(Align.FILL)
            }
            row {
                link("Rescan") {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        discoveryService.scan()
                    }
                }
            }
        }
        group("This IDE") {
            row("Port:") {
                label(port.toString())
            }
            row("Endpoint:") {
                label("http://localhost:$port/api/filePosition")
                    .applyToComponent { foreground = JBColor.GRAY }
            }
        }
    }

    override fun shouldBeAvailable(project: Project) = true

    private fun formatStatus(status: SyncStatus): String = when (status) {
        SyncStatus.DISABLED -> "Disabled"
        SyncStatus.PAUSED -> "Idle"
        SyncStatus.POLLING -> "Syncing..."
        SyncStatus.ERROR -> "No target found"
    }

    private fun statusColor(status: SyncStatus): java.awt.Color = when (status) {
        SyncStatus.DISABLED -> JBColor.GRAY
        SyncStatus.PAUSED -> JBColor.foreground()
        SyncStatus.POLLING -> JBColor(0x59A869, 0x499C54)
        SyncStatus.ERROR -> JBColor.RED
    }
}

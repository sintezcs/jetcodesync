package com.github.sintezcs.jetcodesync.services

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(name = "JetCodeSyncSettings", storages = [Storage("JetCodeSyncSettings.xml")])
class SyncSettingsService : PersistentStateComponent<SyncSettingsService.State> {

    data class State(
        var selectedPort: Int = -1,
        var syncEnabled: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): SyncSettingsService = service()
    }
}

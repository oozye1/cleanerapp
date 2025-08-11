package com.d4rk.cleaner.app.clean.memory.domain.actions

import com.d4rk.android.libs.apptoolkit.core.ui.base.handling.UiEvent

sealed class MemoryEvent : UiEvent {
    object LoadMemoryData : MemoryEvent()
    object ToggleListExpanded : MemoryEvent()
}
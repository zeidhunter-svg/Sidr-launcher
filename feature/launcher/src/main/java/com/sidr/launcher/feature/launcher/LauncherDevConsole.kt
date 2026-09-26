package com.sidr.launcher.feature.launcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Этап 4 / A0, preparatory split. The Developer Command console — session-only and in-memory, with no
 * persisted key, exactly as it was inside `LauncherViewModel`. Extracted unchanged: this class is a
 * move, not a redesign, and `LauncherViewModelTest` must not need one character of edit.
 *
 * [ConsoleLine] stays a top-level type in `HomeInputResults.kt` (same package) — it was never declared
 * inside the ViewModel, so it is imported implicitly via the shared package, not moved.
 *
 * Task 4 / A0, Ruling R7: the original `scope: CoroutineScope` constructor parameter had no consumer
 * in this class's body (nothing here launches a coroutine) and no external caller read it either —
 * confirmed by grepping `feature/` and `app/` for a use before removing it.
 */
internal class LauncherDevConsole {

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _consoleOn = MutableStateFlow(false)
    val consoleOn: StateFlow<Boolean> = _consoleOn.asStateFlow()

    private val _lines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val lines: StateFlow<List<ConsoleLine>> = _lines.asStateFlow()

    fun arm() {
        _armed.value = true
    }

    fun toggle(on: Boolean) {
        _consoleOn.value = on
    }

    fun append(command: String, summary: String) {
        _lines.value = _lines.value + ConsoleLine(command, summary)
    }
}

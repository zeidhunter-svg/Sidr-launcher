package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.alarm.NextAlarm
import com.sidr.launcher.domain.alarm.NextAlarmSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake [NextAlarmSource] (A4′ acceptance tail, task 8). No alarm by default, so every ViewModel built over it
 * shows no chip unless a test sets [alarm].
 */
class FakeNextAlarmSource(initial: NextAlarm? = null) : NextAlarmSource {

    private val state = MutableStateFlow(initial)

    var alarm: NextAlarm?
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun observe(): Flow<NextAlarm?> = state.asStateFlow()
}

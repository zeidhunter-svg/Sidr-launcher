package com.sidr.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.work.SuggestionsWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Best-effort boot warmup for Phase-7 background suggestions work.
 *
 * Re-enqueues the unique periodic jobs after reboot; the scheduler itself re-applies the
 * aiSuggestionsEnabled / LOW_END gate and cancels the precompute work when closed.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var suggestionsWorkScheduler: SuggestionsWorkScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                suggestionsWorkScheduler.ensureScheduled()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.ai.router.ModelAskedSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The launcher's end of [ModelAskedSignal] (A4′ acceptance tail, task 6; controller ruling K9): the route raises it
 * right before it asks the model, and the command session puts its wait up on it ([events];
 * `LauncherCommandSession.submit`). One per process — `:app` binds the domain port to this very instance
 * (`BridgeProvidesModule.provideModelAskedSignal`), and the ViewModel injects it — so the route and the launcher meet
 * in the graph and in neither's code: `:domain` holds only the port, `:feature:launcher` depends on no data module and
 * on no other feature.
 *
 * **It keeps nothing.** No replay: a signal raised while no command is listening is gone, and a listener that
 * subscribes later — the NEXT command — never sees it. One slot of buffer, the oldest dropped, so [modelAsked] never
 * suspends or fails on the route's path; what a pending command's listener has not read yet when a second signal comes
 * is the same «the model is being asked» either way.
 */
@Singleton
class ModelAskedEvents @Inject constructor() : ModelAskedSignal {

    private val asked = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each time the route is about to ask the model. Hot, with nothing replayed. */
    val events: SharedFlow<Unit> = asked.asSharedFlow()

    override fun modelAsked() {
        asked.tryEmit(Unit)
    }
}

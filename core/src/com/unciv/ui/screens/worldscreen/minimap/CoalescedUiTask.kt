package com.unciv.ui.screens.worldscreen.minimap

/**
 * Coalesces requests until the posted action starts executing.
 *
 * [schedule] and the posted callback must run on the same serialized thread.
 * The minimap uses this to keep Scene2D mutations on the GL thread.
 */
class CoalescedUiTask(
    private val post: (() -> Unit) -> Unit,
    private val action: () -> Unit
) {
    private var isScheduled = false

    fun schedule() {
        if (isScheduled) return
        isScheduled = true
        try {
            post {
                isScheduled = false
                action()
            }
        } catch (ex: Throwable) {
            isScheduled = false
            throw ex
        }
    }
}

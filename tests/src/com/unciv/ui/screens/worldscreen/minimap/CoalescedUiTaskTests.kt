package com.unciv.ui.screens.worldscreen.minimap

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.ArrayDeque

class CoalescedUiTaskTests {

    @Test
    fun coalescesQueuedRequestsAndReadsLatestState() {
        val queuedActions = ArrayDeque<() -> Unit>()
        var requestedSize = 0
        var rebuiltSize = 0
        val task = CoalescedUiTask(
            post = { queuedActions.addLast(it) },
            action = { rebuiltSize = requestedSize }
        )

        requestedSize = 4
        task.schedule()
        requestedSize = 16
        task.schedule()
        requestedSize = 30
        task.schedule()

        assertEquals(1, queuedActions.size)
        queuedActions.removeFirst().invoke()
        assertEquals(30, rebuiltSize)

        task.schedule()
        assertEquals(1, queuedActions.size)
    }

    @Test
    fun canBeScheduledAgainWhenPostingFails() {
        var shouldFailPosting = true
        var actionCount = 0
        val task = CoalescedUiTask(
            post = { action ->
                if (shouldFailPosting) {
                    shouldFailPosting = false
                    throw IllegalStateException("dispatch failed")
                }
                action()
            },
            action = { actionCount++ }
        )

        try {
            task.schedule()
            fail("Expected the first post to fail")
        } catch (_: IllegalStateException) {
            // The scheduler must release its queued state when dispatch fails.
        }

        task.schedule()
        assertEquals(1, actionCount)
    }
}

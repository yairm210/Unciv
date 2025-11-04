package com.unciv.ui.screens.basescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.Viewport
import com.unciv.logic.event.Event
import com.unciv.logic.event.EventBus
import com.unciv.ui.crashhandling.wrapCrashHandling
import com.unciv.ui.crashhandling.wrapCrashHandlingUnit
import com.unciv.utils.Log


/** Main stage for the game. Catches all exceptions or errors thrown by event handlers, calling [com.unciv.UncivGame.handleUncaughtThrowable] with the thrown exception or error. */
class UncivStage(viewport: Viewport) : Stage(viewport, getBatch()) {

    companion object {
        fun getBatch(size: Int=1000): Batch = SpriteBatch(size)
    }

    /**
     * Enables/disables sending pointer enter/exit events to actors on this stage.
     * Checking for the enter/exit bounds is a relatively expensive operation and may thus be disabled temporarily.
     */
    var performPointerEnterExitEvents: Boolean = true

    var lastKnownVisibleArea: Rectangle
        private set

    var mouseOverDebug: Boolean
        get() = mouseOverDebugImpl != null
        set(value) {
            mouseOverDebugImpl = if (value) StageMouseOverDebug() else null
        }
    private var mouseOverDebugImpl: StageMouseOverDebug? = null

    private val events = EventBus.EventReceiver()

    init {
        lastKnownVisibleArea = Rectangle(0f, 0f, width, height)
        events.receive(VisibleAreaChanged::class) {
            Log.debug("Visible stage area changed: %s", it.visibleArea)
            lastKnownVisibleArea = it.visibleArea
        }
    }

    override fun dispose() {
        events.stopReceiving()
        super.dispose()

        /** [Stage.dispose] is supposed to clear all references it holds. But it forgets the mouse over properties:
         the [Stage.mouseOverActor] and [Stage.pointerOverActors]. [Stage.act] updates those properties,
         and since there aren't any children left, sets all those properties to `null`. */
        super.act()
    }

    override fun draw() {
        { super.draw() }.wrapCrashHandlingUnit()()
        mouseOverDebugImpl?.draw(this)
    }

    /** libGDX has no built-in way to disable/enable pointer enter/exit events. It is simply being done in [Stage.act]. So to disable this, we have
     * to replicate the [Stage.act] method without the code for pointer enter/exit events. This is of course inherently brittle, but the only way. */
    override fun act() = {
        /** We're replicating [Stage.act], so this value is simply taken from there */
        val delta = Gdx.graphics.deltaTime.coerceAtMost(1 / 30f)

        if (performPointerEnterExitEvents) {
            super.act(delta)
        } else {
            root.act(delta)
        }
    }.wrapCrashHandlingUnit()()

    override fun act(delta: Float) =
            { super.act(delta) }.wrapCrashHandlingUnit()()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        mouseOverDebugImpl?.touchDown(this, screenX, screenY, pointer, button)
        return { super.touchDown(screenX, screenY, pointer, button) }.wrapCrashHandling()() ?: true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int) =
            { super.touchDragged(screenX, screenY, pointer) }.wrapCrashHandling()() ?: true

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int) =
            { super.touchUp(screenX, screenY, pointer, button) }.wrapCrashHandling()() ?: true

    override fun mouseMoved(screenX: Int, screenY: Int) =
            { super.mouseMoved(screenX, screenY) }.wrapCrashHandling()() ?: true

    override fun scrolled(amountX: Float, amountY: Float) =
            { super.scrolled(amountX, amountY) }.wrapCrashHandling()() ?: true

    override fun keyDown(keyCode: Int) =
            { super.keyDown(keyCode) }.wrapCrashHandling()() ?: true

    override fun keyUp(keyCode: Int) =
            { super.keyUp(keyCode) }.wrapCrashHandling()() ?: true

    override fun keyTyped(character: Char) =
            { super.keyTyped(character) }.wrapCrashHandling()() ?: true

    class VisibleAreaChanged(
        val visibleArea: Rectangle
    ) : Event
}

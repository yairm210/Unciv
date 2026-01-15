package com.unciv.ui.screens.basescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.Log


private typealias AddToStringBuilderFactory = (sb: StringBuilder) -> Unit

/**
 *   A debug helper drawing mouse-over info and world coordinate axes onto a Stage.
 *
 *   Usage: save an instance, and in your `Stage.draw` override, call [draw] (yourStage) *after* `super.draw()`.
 *
 *   Implementation notes:
 *   * Uses the stage's [Batch], but its own [ShapeRenderer]
 *   * Tries to avoid any memory allocation in [draw], hence for building nice Actor names,
 *       the reusable StringBuilder is filled using those lambdas, and those are built trying
 *       to use as few closures as possible.
 */
internal class StageMouseOverDebug {
    private val label: Label
    private val mouseCoords = Vector2()
    private lateinit var shapeRenderer: ShapeRenderer
    private val axisColor = Color.RED.cpy().apply { a = overlayAlpha }
    private val sb = StringBuilder(160)

    companion object {
        private const val padding = 3f
        private const val overlayAlpha = 0.8f

        private const val axisInterval = 20
        private const val axisTickLength = 6f
        private const val axisTickWidth = 1.5f

        private const val maxChildScan = 10
        private const val maxTextLength = 20
    }

    init {
        val style = Label.LabelStyle(Fonts.font, Color.WHITE)
        style.background = ImageGetter.getWhiteDotDrawable().tint(Color.DARK_GRAY).apply {
            leftWidth = padding
            rightWidth = padding
            topHeight = padding
            bottomHeight = padding
        }
        style.fontColor = Color.GOLDENROD
        label = Label("", style)
        label.setAlignment(Align.center)
    }

    fun draw(stage: Stage) {
        mouseCoords.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        stage.screenToStageCoordinates(mouseCoords)

        sb.clear()
        sb.append(mouseCoords.x.toInt().tr())
        sb.append(" / ")
        sb.append(mouseCoords.y.toInt().tr())
        sb.append(" (")
        sb.append(Gdx.graphics.framesPerSecond.tr())
        sb.append(")\n")
        addActorLabel(stage.hit(mouseCoords.x, mouseCoords.y, false))

        label.setText(sb)
        layoutLabel(stage)

        val batch = stage.batch
        batch.projectionMatrix = stage.camera.combined
        batch.begin()
        label.draw(batch, overlayAlpha)
        batch.end()

        stage.drawAxes()
    }

    fun touchDown(stage: Stage, screenX: Int, screenY: Int, pointer: Int, button: Int) {
        mouseCoords.set(screenX.toFloat(), screenY.toFloat())
        stage.screenToStageCoordinates(mouseCoords)
        sb.clear()
        val actor = stage.hit(mouseCoords.x, mouseCoords.y, true)
        addActorLabel(actor)
        Log.debug("touchDown %d/%d, %d, %d hitting %s", screenX, screenY, pointer, button, sb)
    }

    private fun addActorLabel(actor: Actor?) {
        if (actor == null) return

        // For this actor, see if it has a descriptive name
        val actorBuilder = getActorDescriptiveName(actor)
        var parentBuilder: AddToStringBuilderFactory? = null
        var childBuilder: AddToStringBuilderFactory? = null

        // If there's no descriptive name for this actor, look for parent or children
        if (actorBuilder == null) {
            // Try to get a descriptive name from parent
            if (actor.parent != null)
                parentBuilder = getActorDescriptiveName(actor.parent)

            // If that failed, try to get a descriptive name from first few children
            if (parentBuilder == null && actor is Group)
                childBuilder = actor.children.asSequence()
                    .take(maxChildScan)
                    .map { getActorDescriptiveName(it) }
                    .firstOrNull { it != null }
        }

        // assemble name parts with fallback to plain class names for parent and actor
        if (parentBuilder != null) {
            parentBuilder(sb)
            sb.append('.')
        } else if (actor.parent != null) {
            sb.append((actor.parent)::class.java.simpleName)
            sb.append('.')
        }

        if (actorBuilder != null)
            actorBuilder(sb)
        else
            sb.append(actor::class.java.simpleName)

        if (childBuilder != null) {
            sb.append('(')
            childBuilder(sb)
            sb.append(')')
        }
    }

    private fun getActorDescriptiveName(actor: Actor): AddToStringBuilderFactory? {
        if (actor.name != null) {
            val className = actor::class.java.simpleName
            if (actor.name.startsWith(className))
                return { sb -> sb.append(actor.name) }
            return { sb ->
                sb.append(className)
                sb.append(':')
                sb.append(actor.name)
            }
        }
        if (actor is Label && actor.text.isNotBlank()) return { sb ->
            sb.append("Label\"")
            sb.appendLimited(actor.text)
            sb.append('\"')
        }
        if (actor is TextButton && actor.text.isNotBlank()) return { sb ->
            sb.append("TextButton\"")
            sb.appendLimited(actor.text)
            sb.append('\"')
        }
        return null
    }

    private fun StringBuilder.appendLimited(text: CharSequence) {
        val lf = text.indexOf('\n') + 1
        val len = (if (lf == 0) text.length else lf).coerceAtMost(maxTextLength)
        if (len == text.length) {
            append(text)
            return
        }
        append(text, 0, len)
        append('‥')  // '…' is taken
    }

    private fun layoutLabel(stage: Stage) {
        if (!label.needsLayout()) return
        val width = label.prefWidth + 2 * padding
        label.setSize(width, label.prefHeight + 2 * padding)
        label.setPosition(stage.width - width, 0f)
        label.validate()
    }

    private fun Stage.drawAxes() {
        if (!::shapeRenderer.isInitialized) {
            shapeRenderer = ShapeRenderer()
            shapeRenderer.setAutoShapeType(true)
        }
        val sr = shapeRenderer

        Gdx.gl.glEnable(GL20.GL_BLEND)
        sr.projectionMatrix = viewport.camera.combined
        sr.begin()
        sr.set(ShapeRenderer.ShapeType.Filled)

        val y2 = height
        val y1 = y2 - axisTickLength
        for (x in 0..width.toInt() step axisInterval) {
            val xf = x.toFloat()
            sr.rectLine(xf, 0f, xf, axisTickLength, axisTickWidth, axisColor, axisColor)
            sr.rectLine(xf, y1, xf, y2, axisTickWidth, axisColor, axisColor)
        }

        val x2 = width
        val x1 = x2 - axisTickLength
        for (y in 0..height.toInt() step axisInterval) {
            val yf = y.toFloat()
            sr.rectLine(0f, yf, axisTickLength, yf, axisTickWidth, axisColor, axisColor)
            sr.rectLine(x1, yf, x2, yf, axisTickWidth, axisColor, axisColor)
        }

        sr.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }
}

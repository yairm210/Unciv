package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.HolidayDates
import com.unciv.models.UncivSound
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.ColorMarkupLabel
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.components.extensions.stageBoundingBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random


//To maybe Do - Civilopedia - not cleanly doable at the moment since tutorials bypass the mod/ruleset model

object XEyes {
    // the following are all relative to size
    const val eyeBallInnerSize = 0.92f
    const val irisSize = 0.6f
    const val irisInnerSize = 0.52f
    const val pupilSize = 0.3f
    const val highlightSize = 0.1f
    // squinting is empirically tuned using these
    const val squintStep = 0.0025f
    const val maxSquintDegree = 0.25f
    const val baseSquintFactor = 0.05f
    // third gimmick
    const val alarmZonePad = 15f
    const val maxFleeCount = 13

    val defaultColor: Color = Color.valueOf("A9FCB4")  // HSV 128,33,98 a light desaturated green

    private val rng = Random(System.nanoTime())

    /** Builds one Eye from circles, with follow-the-mouse ability.
     *
     *  Normally the iris+pupil follow the mouse distance-scaled, when the mouse is too close it would
     *  look straight at you - however, when [squintTest] returns true, the scaling limit is gradually
     *  lifted so the eye starts to "squint"...
     */
    class Eye(
        private val size: Float = 42f,
        irisColor: Color = defaultColor,
        private val squintTest: () -> Boolean = { false }
    ) : Group() {
        private val iris = Group()
        private var nextEventTime = 0L  // to limit follow update rate
        private val mousePos = Vector2()  // one copy to avoid allocations
        private val xyToCenterIris = (1f - irisSize) / 2  // relative to size
        private val maxFollowDistance = (eyeBallInnerSize - irisSize) / 2  // relative to size
        private var squintDegree: Float = 0f

        init {
            isTransform = false
            touchable = Touchable.disabled
            setSize(size, size)

            addCircle(size, Color.DARK_GRAY)
            addCircle(size * eyeBallInnerSize, Color.WHITE)
            iris.setSize(size * irisSize, size * irisSize)
            iris.addCircle(size * irisSize, Color.DARK_GRAY)
            iris.addCircle(size * irisInnerSize, irisColor)
            iris.addCircle(size * pupilSize, Color.BLACK)
            val highlightCircle = iris.addCircle(size * highlightSize, Color.LIGHT_GRAY)
            val pupilRimXY = sin(PI / 8).toFloat() * size * pupilSize
            highlightCircle.moveBy(pupilRimXY, pupilRimXY)
            iris.setPosition(xyToCenterIris * size, xyToCenterIris * size)
            addActor(iris)

            addAction(
                Actions.forever(
                    Actions.run {
                        followMouse()
                    }
                )
            )
        }

        fun isMaxSquint() = squintDegree == maxSquintDegree

        private fun Group.addCircle(size: Float, color: Color) = ImageGetter.getCircle().also {
            it.setSize(size, size)
            it.color = color
            it.center(this)
            addActor(it)
        }

        private fun followMouse() {
            val eventTime = System.currentTimeMillis()
            if (eventTime < nextEventTime) return
            nextEventTime = eventTime + 20

            squintDegree = if (squintTest()) {
                (squintDegree + squintStep).coerceAtMost(maxSquintDegree)
            } else {
                (squintDegree - squintStep).coerceAtLeast(0f)
            }
            val squintFactor = baseSquintFactor + squintDegree

            mousePos.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            screenToLocalCoordinates(mousePos)
            mousePos.sub(size / 2, size / 2) // Now mousePos is in world coords relative to eye center
            val angle = atan2(mousePos.y, mousePos.x)
            val length = hypot(mousePos.x, mousePos.y)
            val distance = (length / size * squintFactor).coerceAtMost(maxFollowDistance)
            val deltaX = distance * cos(angle)
            val deltaY = distance * sin(angle)
            iris.setPosition((xyToCenterIris + deltaX) * size, (xyToCenterIris + deltaY) * size)
        }
    }

    /** Builds a pair of [Eye]s
     *
     *  Extra ability: [peekOutFrom] Animate like a jack-in-the-box from behind one of a list of actors
     *      (must have been placed on a stage with lower Z-order than these), with "fleeing" to another
     *      one when the mouse gets too close.
     */
    class GetAPair(
        private val size: Float = 42f,
        irisColor: Color = defaultColor
    ) : HorizontalGroup() {
        private val leftEye = Eye(size, irisColor, this::squintTest)
        private val rightEye = Eye(size, irisColor, this::squintTest)
        private val mousePos = Vector2()
        private var potentialRoostingCliffs = emptyList<Table>()
        private var currentRoostingCliff: Table? = null
        private val alarmZone = Rectangle()
        private var shynessListener: ShynessListener? = null
        private var shyness = maxFleeCount
        private var isFleeing = false

        init {
            isTransform = false
            touchable = Touchable.enabled
            color = color.cpy()
            color.a = 0f
            space(size / 4)
            addActor(leftEye)
            addActor(rightEye)
            packIfNeeded()
        }

        private fun squintTest(): Boolean {
            mousePos.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            screenToLocalCoordinates(mousePos)
            return (mousePos.x in 0f..width && mousePos.y in 0f..height)
            // faster than this.hit(mousePos.x, mousePos.y, false)
        }

        private fun isSquinting() = leftEye.isMaxSquint() || rightEye.isMaxSquint()

        fun peekOutFrom(roostingCliffs: List<Table>) {
            potentialRoostingCliffs = roostingCliffs
            chooseNewPlace()
            if (shynessListener != null) return
            shynessListener = ShynessListener(alarmZone, this::flee, this::poked)
            stage.addListener(shynessListener)
        }

        private fun chooseNewPlace() {
            val newCliff = potentialRoostingCliffs
                .filterNot { it == currentRoostingCliff }
                .randomOrNull(rng)
                ?: return
            currentRoostingCliff = newCliff
            Log.debug("chooseNewPlace: %s", { (newCliff.children[1] as? Label)?.text })
            clearActions()
            addAction(Actions.sequence(
                Actions.delay(0.02f),
                Actions.run {
                    // Measuring anything while inside the screen initializer is hopeless in Gdx
                    val box = newCliff.stageBoundingBox
                    setPosition(box.x + box.width * 0.666f, box.y + box.height, Align.top)
                    alarmZone.set(
                        x - alarmZonePad,
                        y - alarmZonePad + size * 0.8f,
                        width + 2 * alarmZonePad,
                        height + 2 * alarmZonePad
                    )
                    isFleeing = false
                },
                Actions.parallel(
                    Actions.fadeIn(0.2f),
                    Actions.moveBy(0f, size * 0.8f, 1.5f, Interpolation.bounceOut)
                )
            ))
        }

        private fun flee() {
            if (shyness == 0 || isFleeing) return
            isFleeing = true
            if (--shyness == 0) Log.debug("flee a last time")
            else Log.debug("flee")
            clearActions()
            addAction(Actions.sequence(
                Actions.parallel(
                    Actions.moveBy(0f, -size * 0.8f, 0.4f, Interpolation.bounceIn),
                    Actions.fadeOut(0.5f)
                ),
                Actions.run {
                    chooseNewPlace()
                }
            ))
        }

        private fun poked() {
            Concurrency.run("Sound") { SoundPlayer.play(UncivSound("elephant")) }
            if (!isSquinting()) return
            if (shynessListener != null) {
                stage.removeListener(shynessListener)
                shynessListener = null
            }
            val popup = Popup(stage, false)
            popup.add("Achievement unlocked!".toLabel(Color.FIREBRICK, 42, Align.center)).fillX().row()
            popup.addSeparator()
            val text = "You are now officially a «VIOLET»Super«» «YELLOW»Squint«» «CYAN»Click«» «CHARTREUSE»Master«»! «GOLD»Congratulations!«» Now please leave us alone, that poke right in the «SKY»cornea«» hurt like «SCARLET»HELL«»!!!"
            val label = ColorMarkupLabel(text, Constants.headingFontSize)
            label.setAlignment(Align.center)
            label.wrap = true
            popup.add(label).maxWidth(stage.width / 2).row()
            popup.addCloseButton()
            popup.open(true)
        }
    }

    private class ShynessListener(
        private val alarmZone: Rectangle,
        private val onMouseMove: () -> Unit,
        private val onClick: () -> Unit
    ) : ClickListener() {
        override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
            if (alarmZone.contains(x, y)) onMouseMove()
            return super.mouseMoved(event, x, y)
        }
        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            if (alarmZone.contains(x, y)) onClick()
            else super.clicked(event, x, y)
        }
    }

    fun shouldShowEasterEgg(): Boolean {
        if (System.getProperty("easterEgg") == "Eyes") return true
        if (HolidayDates.getHolidayByDate() != null) return false  // Don't mix with map eggs
        val date = LocalDate.now()
        val luck = if (date.dayOfWeek == DayOfWeek.FRIDAY && date.dayOfMonth == 13) 0.75f else 0.001f
        return rng.nextFloat() <= luck
    }

    fun randomColor(): Color {
        val result = Color().fromHsv(rng.nextFloat() * 360f, 0.63f, 0.99f)
        result.a = 1f
        return result
    }
}

package com.unciv.ui.utils

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.badlogic.gdx.scenes.scene2d.utils.Cullable
import kotlin.math.sqrt


open class ZoomableScrollPane(
    val extraCullingX: Float = 0f,
    val extraCullingY: Float = 0f,
    var minZoom: Float = 0.5f,
    var maxZoom: Float = 1 / minZoom // if we can halve the size, then by default also allow to double it
) : ScrollPane(null) {
    var continuousScrollingX = false

    var onViewportChangedListener: ((width: Float, height: Float, viewport: Rectangle) -> Unit)? = null
    var onPanStopListener: (() -> Unit)? = null
    var onPanStartListener: (() -> Unit)? = null

    /**
     * Exists so that we are always able to set the center to the edge of the contained actor.
     * Otherwise, the [ScrollPane] would always stop at the actor's edge, keeping the center always ([width or height]/2) away from the edge.
     * This is lateinit because unfortunately [ScrollPane] uses [setActor] in its constructor, and we override [setActor], so paddingGroup has not been
     * constructed at that moment, throwing a NPE.
     */
    @Suppress("UNNECESSARY_LATEINIT")
    private lateinit var paddingGroup: Group

    private val horizontalPadding get() = width / 2
    private val verticalPadding get() = height / 2

    init {
        paddingGroup = Group()
        super.setActor(paddingGroup)

        addZoomListeners()
    }

    override fun setActor(actor: Actor?) {
        if (!this::paddingGroup.isInitialized) return
        paddingGroup.clearChildren()
        paddingGroup.addActor(actor)
    }

    override fun getActor(): Actor? {
        if (!this::paddingGroup.isInitialized || !paddingGroup.hasChildren()) return null
        return paddingGroup.children[0]
    }

    override fun scrollX(pixelsX: Float) {
        super.scrollX(pixelsX)
        updateCulling()
        onViewportChanged()
    }

    override fun scrollY(pixelsY: Float) {
        super.scrollY(pixelsY)
        updateCulling()
        onViewportChanged()
    }

    override fun sizeChanged() {
        updatePadding()
        super.sizeChanged()
        updateCulling()
    }

    private fun updatePadding() {
        val content = actor
        if (content == null) return
        // Padding is always [dimension / 2] because we want to be able to have the center of the scrollPane at the very edge of the content
        content.x = horizontalPadding
        paddingGroup.width = content.width + horizontalPadding * 2
        content.y = verticalPadding
        paddingGroup.height = content.height + verticalPadding * 2
    }

    fun updateCulling() {
        val content = actor
        if (content !is Cullable) return

        fun Rectangle.addInAllDirections(xDirectionIncrease: Float, yDirectionIncrease: Float): Rectangle {
            x -= xDirectionIncrease
            y -= yDirectionIncrease
            width += xDirectionIncrease * 2
            height += yDirectionIncrease * 2
            return this
        }

        content.setCullingArea(
            getViewport().addInAllDirections(extraCullingX, extraCullingY)
        )
    }

    open fun zoom(zoomScale: Float) {
        if (zoomScale < minZoom || zoomScale > maxZoom) return

        val previousScaleX = scaleX
        val previousScaleY = scaleY

        setScale(zoomScale)

        // When we scale, the width & height values stay the same. However, after scaling up/down, the width will be rendered wider/narrower than before.
        // But we want to keep the size of the pane the same, so we do need to adjust the width & height: smaller if the scale increased, larger if it decreased.
        val newWidth = width * previousScaleX / zoomScale
        val newHeight = height * previousScaleY / zoomScale
        setSize(newWidth, newHeight)

        onViewportChanged()
        // The size increase/decrease kept scrollX and scrollY (i.e. the top edge and left edge) the same - but changing the scale & size should have changed
        // where the right and bottom edges are. This would mean our visual center moved. To correct this, we theoretically need to update the scroll position
        // by half (i.e. middle) of what our size changed.
        // However, we also changed the padding, which is exactly equal to half of our size change, so we actually don't need to move our center at all.
    }
    fun zoomIn() {
        zoom(scaleX / 0.8f)
    }
    fun zoomOut() {
        zoom(scaleX * 0.8f)
    }

    private fun addZoomListeners() {
        // At first, Remove the existing inputListener
        // which defines that mouse scroll = vertical movement
        val zoomListener = listeners.last { it is InputListener && it !in captureListeners }
        removeListener(zoomListener)
        addListener(object : InputListener() {
            override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                if (amountX > 0 || amountY > 0) zoomOut()
                else zoomIn()
                return false
            }
        })

        addListener(object : ActorGestureListener() {
            var lastScale = 1f
            var lastInitialDistance = 0f

            override fun zoom(event: InputEvent?, initialDistance: Float, distance: Float) {
                if (lastInitialDistance != initialDistance) {
                    lastInitialDistance = initialDistance
                    lastScale = scaleX
                }
                val scale: Float = sqrt((distance / initialDistance).toDouble()).toFloat() * lastScale
                zoom(scale)
            }
        })
    }

    override fun getFlickScrollListener(): ActorGestureListener {
        //This is mostly just Java code from the ScrollPane class reimplemented as Kotlin code
        //Had to change a few things to bypass private access modifiers
        return object : ActorGestureListener() {
            private var wasPanning = false
            override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
                if (!wasPanning) {
                    wasPanning = true
                    onPanStartListener?.invoke()
                }
                setScrollbarsVisible(true)
                scrollX -= deltaX
                scrollY += deltaY

                //this is the new feature to fake an infinite scroll
                when {
                    continuousScrollingX && scrollPercentX >= 1 && deltaX < 0 -> {
                        scrollPercentX = 0f
                    }
                    continuousScrollingX && scrollPercentX <= 0 && deltaX > 0-> {
                        scrollPercentX = 1f
                    }
                }

                //clamp() call is missing here but it doesn't seem to make any big difference in this case

                if ((isScrollX && deltaX != 0f || isScrollY && deltaY != 0f)) cancelTouchFocus()
            }

            override fun panStop(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                wasPanning = false
                onPanStopListener?.invoke()
            }
        }
    }

    private var scrollingTo: Vector2? = null
    private var scrollingAction: Action? = null

    fun isScrolling(): Boolean = scrollingAction != null && actions.any { it == scrollingAction }

    /** Get the scrolling destination if currently scrolling, else the current scroll position. */
    fun scrollingDestination(): Vector2 {
        if (isScrolling())
            return scrollingTo!!
        else
            return Vector2(scrollX, scrollY)
    }

    /** Scroll the pane to specified coordinates.
     * @return `true` if scroll position got changed or started being changed, `false`
     *   if already centered there or already scrolling there
     */
    fun scrollTo(x: Float, y: Float, immediately: Boolean = false): Boolean {
        if (scrollingDestination() == Vector2(x, y)) return false

        removeAction(scrollingAction)

        if (immediately) {
            scrollX = x
            scrollY = y
            updateVisualScroll()
        } else {
            val originalScrollX = scrollX
            val originalScrollY = scrollY
            scrollingTo = Vector2(x, y)
            val action = object : FloatAction(0f, 1f, 0.4f) {
                override fun update(percent: Float) {
                    scrollX = scrollingTo!!.x * percent + originalScrollX * (1 - percent)
                    scrollY = scrollingTo!!.y * percent + originalScrollY * (1 - percent)
                    updateVisualScroll()
                }
            }
            action.interpolation = Interpolation.sine
            addAction(action)
            scrollingAction = action
        }

        return true
    }

    /** @return the currently scrolled-to viewport of the whole scrollable area */
    fun getViewport(): Rectangle {
        val viewportFromLeft = scrollX
        /** In the default coordinate system, the y origin is at the bottom, but scrollY is from the top, so we need to invert. */
        val viewportFromBottom = maxY - scrollY
        return Rectangle(
            viewportFromLeft - horizontalPadding,
            viewportFromBottom - verticalPadding,
            width,
            height)
    }

    private fun onViewportChanged() {
        onViewportChangedListener?.invoke(maxX, maxY, getViewport())
    }
}

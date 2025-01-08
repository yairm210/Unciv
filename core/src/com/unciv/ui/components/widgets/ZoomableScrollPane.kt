package com.unciv.ui.components.widgets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.badlogic.gdx.scenes.scene2d.utils.Cullable
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.ZoomGestureListener
import com.unciv.ui.components.input.KeyboardPanningListener


open class ZoomableScrollPane(
    private val extraCullingX: Float = 0f,
    private val extraCullingY: Float = 0f,
    var minZoom: Float = 0.5f,
    var maxZoom: Float = 1 / minZoom // if we can halve the size, then by default also allow to double it
) : ScrollPane(Group()) {
    var continuousScrollingX = false

    var onViewportChangedListener: ((width: Float, height: Float, viewport: Rectangle) -> Unit)? = null
    var onPanStopListener: (() -> Unit)? = null
    var onPanStartListener: (() -> Unit)? = null
    var onZoomStopListener: (() -> Unit)? = null
    var onZoomStartListener: (() -> Unit)? = null
    private val zoomListener = ZoomListener()

    private val horizontalPadding get() = width / 2
    private val verticalPadding get() = height / 2

    /** Will be set from [GameSettings.mapAutoScroll] */
    var isAutoScrollEnabled = false
    /** Will be set from [GameSettings.mapPanningSpeed] */
    var mapPanningSpeed: Float = 6f

    init {
        this.addListener(zoomListener)
    }

    open fun reloadMaxZoom() {

        maxZoom = UncivGame.Current.settings.maxWorldZoomOut
        minZoom = 1f / maxZoom

        // Since normally min isn't reached exactly, only powers of 0.8
        if (scaleX < minZoom)
            zoom(1f)
    }

    // We don't want default scroll listener
    // which defines that mouse scroll = vertical movement
    override fun addScrollListener() {}

    override fun getActor() : Actor? {
        val group: Group = super.getActor() as Group
        return if (group.hasChildren()) group.children[0] else null
    }

    // This override will be called from ScrollPane's constructor to store the empty Group() we're passing,
    // therefore super.getActor() _can_ return null.
    override fun setActor(content: Actor?) {
        val group: Group? = super.getActor() as Group?
        if (group != null) {
            group.clearChildren()
            group.addActor(content)
        } else {
            super.setActor(content)
        }
    }

    override fun scrollX(pixelsX: Float) {
        super.scrollX(pixelsX)
        updateCulling()
        onViewportChanged()
    }

    override fun setScrollX(pixels: Float) {
        var result = pixels

        if (continuousScrollingX) {
            if (result < 0f) result += maxX
            else if (result > maxX) result -= maxX
        }

        super.setScrollX(result)
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
        val content = actor ?: return
        // Padding is always [dimension / 2] because we want to be able to have the center of the scrollPane at the very edge of the content
        content.x = horizontalPadding
        content.y = verticalPadding
        super.getActor().width = content.width + horizontalPadding * 2
        super.getActor().height = content.height + verticalPadding * 2
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
        val newZoom = zoomScale.coerceIn(minZoom, maxZoom)
        val oldZoomX = scaleX
        val oldZoomY = scaleY

        if (newZoom == oldZoomX)
            return

        val newWidth = width * oldZoomX / newZoom
        val newHeight = height * oldZoomY / newZoom

        // When we scale, the width & height values stay the same. However, after scaling up/down, the width will be rendered wider/narrower than before.
        // But we want to keep the size of the pane the same, so we do need to adjust the width & height: smaller if the scale increased, larger if it decreased.
        setScale(newZoom)
        setSize(newWidth, newHeight)

        onViewportChanged()
        // The size increase/decrease kept scrollX and scrollY (i.e. the top edge and left edge) the same - but changing the scale & size should have changed
        // where the right and bottom edges are. This would mean our visual center moved. To correct this, we theoretically need to update the scroll position
        // by half (i.e. middle) of what our size changed.
        // However, we also changed the padding, which is exactly equal to half of our size change, so we actually don't need to move our center at all.
    }
    fun zoomIn(immediate: Boolean = false) {
        if (immediate)
            zoom(scaleX / 0.8f)
        else
            zoomListener.zoomIn(0.8f)
    }
    fun zoomOut(immediate: Boolean = false) {
        if (immediate)
            zoom(scaleX * 0.8f)
        else
            zoomListener.zoomOut(0.8f)
    }

    fun isZooming(): Boolean {
        return zoomListener.isZooming
    }

    inner class ZoomListener : ZoomGestureListener() {

        inner class ZoomAction : TemporalAction() {

            var startingZoom: Float = 1f
            var finishingZoom: Float = 1f
            var currentZoom: Float = 1f

            init {
                duration = 0.3f
                interpolation = Interpolation.fastSlow
            }

            override fun begin() {
                isZooming = true
            }

            override fun end() {
                zoomAction = null
                isZooming = false
            }

            override fun update(percent: Float) {
                currentZoom = MathUtils.lerp(startingZoom, finishingZoom, percent)
                zoom(currentZoom)
            }

        }

        private var zoomAction: ZoomAction? = null
        var isZooming = false

        fun zoomOut(zoomMultiplier: Float = 0.82f) {
            if (scaleX <= minZoom) {
                if (zoomAction != null)
                    zoomAction!!.finish()
                return
            }

            if (zoomAction != null) {
                zoomAction!!.startingZoom = zoomAction!!.currentZoom
                zoomAction!!.finishingZoom *= zoomMultiplier
                zoomAction!!.restart()
            } else {
                zoomAction = ZoomAction()
                zoomAction!!.startingZoom = scaleX
                zoomAction!!.finishingZoom = scaleX * zoomMultiplier
                addAction(zoomAction)
            }
        }

        fun zoomIn(zoomMultiplier: Float = 0.82f) {
            if (scaleX >= maxZoom) {
                if (zoomAction != null)
                    zoomAction!!.finish()
                return
            }

            if (zoomAction != null) {
                zoomAction!!.startingZoom = zoomAction!!.currentZoom
                zoomAction!!.finishingZoom /= zoomMultiplier
                zoomAction!!.restart()
            } else {
                zoomAction = ZoomAction()
                zoomAction!!.startingZoom = scaleX
                zoomAction!!.finishingZoom = scaleX / zoomMultiplier
                addAction(zoomAction)
            }
        }

        override fun pinch(delta: Vector2) {
            if (!isZooming) {
                isZooming = true
                onZoomStartListener?.invoke()
            }
            scrollTo(
                scrollX - delta.x / scaleX,
                scrollY + delta.y / scaleY,
                true
            )
        }

        override fun pinchStop() {
            isZooming = false
            onZoomStopListener?.invoke()
        }

        override fun zoom(delta: Float, focus: Vector2) {
            // correct map position to zoom in to the focus point
            val dx = (stage.width / 2 - focus.x) * (delta - 1) / scaleX
            val dy = (stage.height / 2 - focus.y) * (delta - 1) / scaleY
            scrollTo(
                scrollX - dx,
                scrollY + dy,
                true
            )
            zoom(scaleX * delta)
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            if (amountX > 0 || amountY > 0)
                zoomOut()
            else
                zoomIn()
            return true
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (isAutoScrollEnabled && !Gdx.input.isTouched) {

            val posX = Gdx.input.x
            val posY = Gdx.input.y  // Viewport coord: goes down, unlike world coordinates

            val deltaX = when {
                posX <= 2 -> 1
                posX >= stage.viewport.screenWidth - 2 -> -1
                else -> 0
            }
            val deltaY = when {
                posY <= 6 -> -1
                posY >= stage.viewport.screenHeight - 6 -> 1
                else -> 0
            }

            if (deltaX != 0 || deltaY != 0) {
                // if Gdx deltaTime is > KeyboardPanningListener.deltaTime, then mouse auto scroll would be slower
                // (Gdx deltaTime is measured, not a constant, depends on framerate)
                // The extra factor is empirical to make mouse and WASD keyboard feel the same
                val relativeSpeed = Gdx.graphics.deltaTime / KeyboardPanningListener.deltaTime * 0.3f
                doKeyOrMousePanning(deltaX * relativeSpeed, deltaY * relativeSpeed)
            }
        }
        super.draw(batch, parentAlpha)
    }

    inner class FlickScrollListener : ActorGestureListener() {
        private var isPanning = false
        override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
            if (!isPanning) {
                isPanning = true
                onPanStartListener?.invoke()
            }
            setScrollbarsVisible(true)
            scrollX = restrictX(deltaX)
            scrollY = restrictY(deltaY)

            //clamp() call is missing here but it doesn't seem to make any big difference in this case

        }

        override fun panStop(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            if (zoomListener.isZooming)
                zoomListener.isZooming = false
            isPanning = false
            onPanStopListener?.invoke()
        }
    }

    open fun restrictX(deltaX: Float): Float = scrollX - deltaX
    open fun restrictY(deltaY: Float): Float = scrollY + deltaY

    /**
     * Perform keyboard WASD or mouse-at-edge panning.
     * Called from [KeyboardPanningListener] and [draw] if [isAutoScrollEnabled] is on.
     *
     * Positive [deltaX] = Left, Positive [deltaY] = DOWN
     */
    fun doKeyOrMousePanning(deltaX: Float, deltaY: Float) {
        if (deltaX == 0f && deltaY == 0f) return
        val amountToMove = mapPanningSpeed / scaleX
        scrollX = restrictX(deltaX * amountToMove)
        scrollY = restrictY(deltaY * amountToMove)
        updateVisualScroll()
    }

    override fun getFlickScrollListener(): ActorGestureListener {
        return FlickScrollListener()
    }

    private var scrollingTo: Vector2? = null
    private var scrollingAction: Action? = null

    fun isScrolling(): Boolean = scrollingAction != null && actions.any { it == scrollingAction }

    /** Get the scrolling destination if currently scrolling, else the current scroll position. */
    fun scrollingDestination(): Vector2 {
        return if (isScrolling()) scrollingTo!! else Vector2(scrollX, scrollY)
    }

    class ScrollToAction(private val zoomableScrollPane: ZoomableScrollPane) : FloatAction(0f, 1f, 0.4f) {

        private val originalScrollX = zoomableScrollPane.scrollX
        private val originalScrollY = zoomableScrollPane.scrollY

        override fun update(percent: Float) {
            zoomableScrollPane.scrollX = zoomableScrollPane.scrollingTo!!.x * percent + originalScrollX * (1 - percent)
            zoomableScrollPane.scrollY = zoomableScrollPane.scrollingTo!!.y * percent + originalScrollY * (1 - percent)
            zoomableScrollPane.updateVisualScroll()
        }
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
            scrollingTo = Vector2(x, y)
            val action = ScrollToAction(this)
            action.interpolation = Interpolation.sine
            addAction(action)
            scrollingAction = action
        }

        return true
    }

    /** Overwrite [rect] with the currently scrolled-to viewport of the whole scrollable area */
    fun getViewport(rect: Rectangle) {
        val viewportFromLeft = scrollX
        /** In the default coordinate system, the y origin is at the bottom, but scrollY is from the top, so we need to invert. */
        val viewportFromBottom = maxY - scrollY
        rect.x = viewportFromLeft - horizontalPadding
        rect.y = viewportFromBottom - verticalPadding
        rect.width = width
        rect.height = height
    }

    /** @return the currently scrolled-to viewport of the whole scrollable area */
    private fun getViewport() = Rectangle().also { getViewport(it) }

    private fun onViewportChanged() {
        onViewportChangedListener?.invoke(maxX, maxY, getViewport())
    }
}

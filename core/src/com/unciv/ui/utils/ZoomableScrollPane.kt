package com.unciv.ui.utils

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
import java.lang.Float.max
import java.lang.Float.min
import kotlin.math.sqrt


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

    init {
        this.addListener(zoomListener)
    }

    fun reloadMaxZoom() {

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

    override fun scrollY(pixelsY: Float) {
        super.scrollY(pixelsY)
        updateCulling()
        onViewportChanged()
    }

    override fun sizeChanged() {
        updatePadding()
        super.sizeChanged()
        updateCulling()

        if (continuousScrollingX) {
            // For world-wrap we do not allow viewport to become bigger than the map size,
            // because we don't want to render the same tiles multiple times (they will be
            // flickering because of movement).
            // Hence we limit minimal possible zoom to content width + some extra offset.
            val content = actor
            if (content != null)
                minZoom = max((width + 80f) * scaleX / content.width, 1f / UncivGame.Current.settings.maxWorldZoomOut)// add some extra padding offset
        }

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
        val newZoom = min(max(zoomScale, minZoom), maxZoom)
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
        private var lastInitialDistance = 0f
        var lastScale = 1f
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

        override fun pinch() {
            if (!isZooming) {
                isZooming = true
                onZoomStartListener?.invoke()
            }
        }

        override fun pinchStop() {
            isZooming = false
            onZoomStopListener?.invoke()
        }

        override fun zoom(initialDistance: Float, distance: Float) {
            if (lastInitialDistance != initialDistance) {
                lastInitialDistance = initialDistance
                lastScale = scaleX
            }
            val scale: Float = sqrt((distance / initialDistance).toDouble()).toFloat() * lastScale
            zoom(scale)
        }

        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            if (amountX > 0 || amountY > 0)
                zoomOut()
            else
                zoomIn()
            return true
        }
    }

    inner class FlickScrollListener : ActorGestureListener() {
        private var isPanning = false
        override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
            if (!isPanning) {
                isPanning = true
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

            if ((isScrollX && deltaX != 0f || isScrollY && deltaY != 0f))
                cancelTouchFocus()
        }

        override fun panStop(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
            isPanning = false
            onPanStopListener?.invoke()
        }
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

    class ScrollToAction(private val zoomableScrollPane: ZoomableScrollPane):FloatAction(0f, 1f, 0.4f) {

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

    /** @return the currently scrolled-to viewport of the whole scrollable area */
    private fun getViewport(): Rectangle {
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

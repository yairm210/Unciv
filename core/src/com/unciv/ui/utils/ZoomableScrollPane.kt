package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt


open class ZoomableScrollPane(
    private val minScale: Float = 0.5f,
    private val maxScale: Float = 2f,
    private val dynamicOrigin: Boolean = false,
    private val notifyZoom: (()->Unit)? = null
): ScrollPane(null) {
    var continuousScrollingX = false

    init{
        addZoomListeners()
    }

    open fun zoom(zoomScale: Float) {
        if (zoomScale < minScale || zoomScale > maxScale) return
        setScale(zoomScale)
        notifyZoom?.invoke()
    }
    fun zoomIn() {
        zoom(scaleX / 0.8f)
    }
    fun zoomOut() {
        zoom(scaleX * 0.8f)
    }
    private fun zoomCenteredOn(x: Float, y: Float, out: Boolean) {
        //todo this is not pixel-accurate, but better than nothing
        val xOld = x / scaleX + scrollX
        val yOld = y / scaleY + scrollY
        if (out) zoomOut() else zoomIn()
        scrollX = xOld - x / scaleX
        scrollY = yOld - y / scaleY
        updateVisualScroll()
    }

    private fun addZoomListeners() {
        // At first, Remove the existing inputListener
        // which defines that mouse scroll = vertical movement
        val zoomListener = listeners.last { it is InputListener && it !in captureListeners }
        removeListener(zoomListener)
        addListener(object : InputListener() {
            override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                val amount = if (amountX + amountY > 0f) max(amountX, amountY) else min(amountX, amountY)
                when {
                    !dynamicOrigin && amount > 0f ->
                        zoomOut()
                    !dynamicOrigin ->
                        zoomIn()
                    Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT) ->
                        zoomCenteredOn(x, y, amount > 0f)
                    Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT) ->
                        scrollX += mouseWheelX * amount
                    else ->
                        scrollY += mouseWheelY * amount
                }
                return true
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
            override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
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
        }
    }
}

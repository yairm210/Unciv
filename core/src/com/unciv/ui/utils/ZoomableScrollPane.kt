package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import kotlin.math.abs
import kotlin.math.sqrt


open class ZoomableScrollPane: ScrollPane(null) {
    var continousScrollingX = false

    init{
        // Remove the existing inputListener
        // which defines that mouse scroll = vertical movement
        val zoomListener = listeners.last { it is InputListener && it !in captureListeners }
        removeListener(zoomListener)
        addZoomListeners()
    }

    open fun zoom(zoomScale: Float) {
        if (zoomScale < 0.5f || zoomScale > 2) return
        setScale(zoomScale)
    }

    private fun addZoomListeners() {

        addListener(object : InputListener() {
            override fun scrolled(event: InputEvent?, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                if (amountX > 0 || amountY > 0) zoom(scaleX * 0.8f)
                else zoom(scaleX / 0.8f)
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
            override fun pan(event: InputEvent, x: Float, y: Float, deltaX: Float, deltaY: Float) {
                setScrollbarsVisible(true)
                scrollX -= deltaX
                scrollY += deltaY

                //this is the new feature to fake an infinite scroll
                when {
                    continousScrollingX && scrollPercentX >= 1 -> {
                        //don't go to zero to not trigger the scrollPercentX <= 0
                        scrollPercentX = 0.01f
                    }
                    continousScrollingX && scrollPercentX <= 0 -> {
                        //same here
                        scrollPercentX = 0.99f
                    }
                }

                //clamp() call is missing here but it doesn't seem to make any big difference in this case

                if ((isScrollX && deltaX != 0f || isScrollY && deltaY != 0f)) cancelTouchFocus()
            }

            //This is the default behavior of the fling implementation
            override fun fling(event: InputEvent, x: Float, y: Float, button: Int) {
                if (abs(x) > 150 && isScrollX) {
                    //1f is the default value of flingTimer
                    setFlingTime(1f)
                    velocityX = x
                    cancelTouchFocus()
                }
                if (abs(y) > 150 && isScrollY) {
                    setFlingTime(1f)
                    velocityY = -y
                    cancelTouchFocus()
                }
            }

            //Nothing changed here either
            override fun handle(event: Event): Boolean {
                if (super.handle(event)) {
                    if ((event as InputEvent).type == InputEvent.Type.touchDown) setFlingTime(0f)
                    return true
                } else if (event is InputEvent && event.isTouchFocusCancel) //
                    cancel()
                return false
            }
        }
    }
}

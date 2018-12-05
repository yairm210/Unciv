package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group

class IconCircleGroup(size:Float, val image: Actor): Group(){
    val circle = ImageGetter.getCircle().apply { setSize(size, size) }
    init {
        setSize(size, size)
        addActor(circle)
        image.setSize(size * 0.75f, size * 0.75f)
        image.center(this)
        addActor(image)
    }
}
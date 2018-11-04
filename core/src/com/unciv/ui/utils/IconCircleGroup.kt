package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image

class IconCircleGroup(size:Float, val image: Image): Group(){
    val circle = ImageGetter.getImage("OtherIcons/Circle").apply { setSize(size, size) }
    init {
        setSize(size, size)
        addActor(circle)
        image.setSize(size * 0.75f, size * 0.75f)
        image.center(this)
        addActor(image)
    }
}
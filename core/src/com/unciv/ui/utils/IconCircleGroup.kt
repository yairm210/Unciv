package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group

class IconCircleGroup(size:Float, val actor: Actor, resizeActor:Boolean=true): Group(){
    val circle = ImageGetter.getCircle().apply { setSize(size, size) }
    init {
        isTransform=false // performance helper - nothing here is rotated or scaled
        setSize(size, size)
        addActor(circle)
        if(resizeActor) actor.setSize(size * 0.75f, size * 0.75f)
        actor.center(this)
        addActor(actor)
    }
}
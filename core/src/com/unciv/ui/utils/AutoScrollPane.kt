package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener

open class AutoScrollPane(initActor: Actor?, initStyle: ScrollPaneStyle = ScrollPaneStyle()): ScrollPane(initActor,initStyle) {
    constructor(initActor: Actor, initSkin: Skin) : this(initActor,initSkin.get(ScrollPaneStyle::class.java))
    constructor(initActor: Actor, initSkin: Skin, initName: String) : this(initActor,initSkin.get(initName,ScrollPaneStyle::class.java))

    private var savedFocus: Actor? = null

    init {
        this.addListener (object : ClickListener() {
            override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
                if (savedFocus == null) savedFocus = stage.scrollFocus
                stage.scrollFocus = this@AutoScrollPane
            }
            override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
                if (stage.scrollFocus == this@AutoScrollPane) stage.scrollFocus = savedFocus
                savedFocus = null
            }
        })
    }
}
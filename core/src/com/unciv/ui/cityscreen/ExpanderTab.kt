package com.unciv.ui.cityscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontSize

class ExpanderTab(private val title:String,skin: Skin): Table(skin){
    private val toggle = Table(skin) // the show/hide toggler
    private val tab = Table() // what holds the information to be shown/hidden
    val innerTable= Table() // the information itself
    var isOpen=true

    init{
        toggle.defaults().pad(10f)
        toggle.touchable= Touchable.enabled
        toggle.background(ImageGetter.getBackground(ImageGetter.getBlue()))
        toggle.add("+ $title").apply { actor.setFontSize(24) }
        toggle.onClick {
            if(isOpen) close()
            else open()
        }
        add(toggle).fill().row()
        tab.add(innerTable).pad(10f)
        add(tab)
    }

    fun close(){
        if(!isOpen) return
        toggle.clearChildren()
        toggle.add("- $title").apply { actor.setFontSize(24) }
        tab.clear()
        isOpen=false
    }

    fun open(){
        if(isOpen) return
        toggle.clearChildren()
        toggle.add("+ $title").apply { actor.setFontSize(24) }
        tab.add(innerTable)
        isOpen=true
    }
}
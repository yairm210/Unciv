package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addClickListener

open class OptionsTable: Table(){
    init {
        val tileTableBackground = ImageGetter.getDrawable("skin/whiteDot.png")
                .tint(Color(0x004085bf))
        background = tileTableBackground

        this.pad(20f)
        this.defaults().pad(5f)
    }

    fun addButton(text:String, action:()->Unit){
        val button = TextButton(text, CameraStageBaseScreen.skin).apply { color= ImageGetter.getBlue() }
        button.addClickListener(action)
        add(button).row()
    }
}
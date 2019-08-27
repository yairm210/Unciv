package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*

open class PopupTable(val screen: CameraStageBaseScreen): Table(CameraStageBaseScreen.skin){
    init {
        val tileTableBackground = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        background = tileTableBackground

        this.pad(20f)
        this.defaults().pad(5f)
    }

    fun open(){
        pack()
        center(screen.stage)
        screen.stage.addActor(this)
    }

    open fun close(){ remove() }

    fun addGoodSizedLabel(text: String): Cell<Label> {
        val label = text.toLabel()
        label.setWrap(true)
        label.setAlignment(Align.center)
        return add(label).width(screen.stage.width/2)
    }

    fun addButton(text:String, action:()->Unit): Cell<TextButton> {
        val button = TextButton(text.tr(), skin).apply { color= ImageGetter.getBlue() }
        button.onClick(action)
        return add(button).apply { row() }
    }
}


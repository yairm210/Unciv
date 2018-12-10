package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.onClick

open class PopupTable: Table(CameraStageBaseScreen.skin){
    init {
        val tileTableBackground = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        background = tileTableBackground

        this.pad(20f)
        this.defaults().pad(5f)
    }

    fun addButton(text:String, action:()->Unit){
        val button = TextButton(text.tr(), skin).apply { color= ImageGetter.getBlue() }
        button.onClick(action)
        add(button).row()
    }
}

class YesNoPopupTable(question:String, action:()->Unit,
                      screen: CameraStageBaseScreen = UnCivGame.Current.worldScreen) : PopupTable(){
    init{
        if(!isOpen) {
            isOpen=true
            add(Label(question, skin)).colspan(2).row()

            add(TextButton("No".tr(), skin).apply { onClick { close() } })
            add(TextButton("Yes".tr(), skin).apply { onClick { close(); action() } })
            pack()
            center(screen.stage)
            screen.stage.addActor(this)
        }
    }

    fun close(){
        remove()
        isOpen=false
    }

    companion object {
        var isOpen=false
    }
}
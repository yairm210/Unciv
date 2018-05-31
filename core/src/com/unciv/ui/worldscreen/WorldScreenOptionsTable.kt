package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.ui.LoadScreen
import com.unciv.ui.NewGameScreen
import com.unciv.ui.SaveScreen
import com.unciv.ui.VictoryScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center

class WorldScreenOptionsTable internal constructor() : OptionsTable() {

    init {
        addButton("Load game"){
            UnCivGame.Current.screen = LoadScreen()
            remove()
        }

        addButton("Save game") {
            UnCivGame.Current.screen = SaveScreen()
            remove()
        }

        addButton("Start new game"){ UnCivGame.Current.screen = NewGameScreen() }

        addButton("Victory status") { UnCivGame.Current.screen = VictoryScreen() }

        addButton("Social Policies"){
            UnCivGame.Current.screen = PolicyPickerScreen(UnCivGame.Current.gameInfo.getPlayerCivilization())
        }


        addButton("Display options"){
            UnCivGame.Current.worldScreen.stage.addActor(WorldScreenDisplayOptionsTable())
            remove()
        }

        addButton("Close"){ remove() }

        pack() // Needed to show the background.
        center(UnCivGame.Current.worldScreen.stage)
    }
}

open class OptionsTable:Table(){
    init {
        val tileTableBackground = ImageGetter.getDrawable("skin/whiteDot.png")
                .tint(Color(0x004085bf))
        background = tileTableBackground

        this.pad(20f)
        this.defaults().pad(5f)
    }

    fun addButton(text:String, action:()->Unit){
        val button = TextButton(text, CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue() }
        button.addClickListener(action)
        add(button).row()
    }
}

class WorldScreenDisplayOptionsTable() : OptionsTable(){
    init {
        update()
    }

    fun update(){
        clear()
        val tileMapHolder=UnCivGame.Current.worldScreen.tileMapHolder
        val settings = UnCivGame.Current.settings
        if(settings.showWorkedTiles) addButton("Hide worked tiles") {settings.showWorkedTiles=false; update()}
        else addButton("Show worked tiles") {settings.showWorkedTiles=true; update()}

        if(settings.showResourcesAndImprovements)
            addButton("Hide resources and improvements") {settings.showResourcesAndImprovements=false; update()}
        else addButton("Show resources and improvements") {settings.showResourcesAndImprovements=true; update()}

        addButton("Close"){ remove() }

        pack() // Needed to show the background.
        center(UnCivGame.Current.worldScreen.stage)
        tileMapHolder.updateTiles()
    }
}
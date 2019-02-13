package com.unciv.ui.saves

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Json
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.getRandom
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel


class SaveScreen : PickerScreen() {
    val textField = TextField("", skin)

    init {
        setDefaultCloseAction()
        val currentSaves = Table()

        currentSaves.add("Current saves".toLabel()).row()
        val saves = GameSaver().getSaves()
        saves.forEach {
            val textButton = TextButton(it, skin)
            textButton.onClick {
                textField.text = it
            }
            currentSaves.add(textButton).pad(5f).row()

        }
        topTable.add(currentSaves)


        val newSave = Table()
        val adjectives = listOf("Prancing","Obese","Junior","Senior","Abstract","Discombobulating","Simple","Awkward","Holy",
                "Dangerous","Greasy","Stinky","Purple","Majestic","Incomprehensible","Cardboard","Chocolate","Robot","Ninja",
                "Fluffy","Magical","Invisible")
        val nouns = listOf("Moose","Pigeon","Weasel","Ferret","Onion","Marshmallow","Crocodile","Unicorn",
                "Sandwich","Elephant","Kangaroo","Marmot","Beagle","Dolphin","Fish","Tomato","Duck","Dinosaur")
        val defaultSaveName = adjectives.getRandom()+" "+nouns.getRandom()
        textField.text = defaultSaveName

        newSave.add("Saved game name".toLabel()).row()
        newSave.add(textField).width(300f).pad(10f).row()

        val copyJsonButton = TextButton("Copy game info".tr(),skin)
        copyJsonButton.onClick {
            val json = Json().toJson(game.gameInfo)
            val base64Gzip = Gzip.zip(json)
            Gdx.app.clipboard.contents =  base64Gzip
        }
        newSave.add(copyJsonButton)

        topTable.add(newSave)
        topTable.pack()

        rightSideButton.setText("Save game".tr())
        rightSideButton.onClick {
            GameSaver().saveGame(UnCivGame.Current.gameInfo, textField.text)
            UnCivGame.Current.setWorldScreen()
        }
        rightSideButton.enable()
    }

}



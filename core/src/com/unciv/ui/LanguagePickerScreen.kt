package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.tr


class LanguageTable(val language:String,skin: Skin):Table(skin){
    private val blue = ImageGetter.getBlue()
    private val darkBlue = blue.cpy().lerp(Color.BLACK,0.5f)!!

    init{
        pad(10f)
        defaults().pad(10f)
        add(ImageGetter.getImage("Flags/$language.png"))
        add(language)
        pack()
    }
    fun update(chosenLanguage:String){
        background = ImageGetter.getBackground( if(chosenLanguage==language) blue else darkBlue)
    }
}

class LanguagePickerScreen: PickerScreen(){
    var chosenLanguage = "English"

    private val languageTables = ArrayList<LanguageTable>()

    fun update(){
        languageTables.forEach { it.update(chosenLanguage) }
    }

    init {
        closeButton.isVisible = false
        GameBasics.Translations.getLanguages().forEach {
            val languageTable = LanguageTable(it, skin)
            languageTable.addClickListener {
                chosenLanguage = languageTable.language
                rightSideButton.enable()
                update()
            }
            topTable.add(languageTable).row()
            languageTables.add(languageTable)
        }

        rightSideButton.setText("Pick language".tr())
        rightSideButton.addClickListener {
            UnCivGame.Current.settings.language = chosenLanguage
            UnCivGame.Current.settings.save()
            UnCivGame.Current.startNewGame()
            dispose()
        }
    }

}
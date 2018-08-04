package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UnCivGame
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
        add(ImageGetter.getImage("FlagIcons/$language.png")).size(40f)
        val availableTranslations = GameBasics.Translations.filter { it.value.containsKey(language) }
        val percentComplete: Int
        if(language=="English") percentComplete = 100
        else percentComplete = (availableTranslations.size*100 / GameBasics.Translations.size)
        add("$language ($percentComplete%)")
        update("")
        touchable = Touchable.enabled // so click listener is activated when any part is clicked, not only children
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
        topTable.add(Label(
                "Please note that translations are a " +
                    "community-based work in progress and are incomplete! \n" +
                    "If you want to help translating the game " +
                    "into your language, contact me!",skin)).pad(10f).row()
        GameBasics.Translations.getLanguages().forEach {
            val languageTable = LanguageTable(it, skin)
            languageTable.addClickListener {
                chosenLanguage = languageTable.language
                rightSideButton.enable()
                update()
            }
            topTable.add(languageTable).pad(10f).row()
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
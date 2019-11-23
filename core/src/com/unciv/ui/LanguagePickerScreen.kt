package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick


class LanguageTable(val language:String,skin: Skin):Table(skin){
    private val blue = ImageGetter.getBlue()
    private val darkBlue = blue.cpy().lerp(Color.BLACK,0.5f)!!
    val percentComplete: Int

    init{
        pad(10f)
        defaults().pad(10f)
        if(ImageGetter.imageExists("FlagIcons/$language"))
            add(ImageGetter.getImage("FlagIcons/$language")).size(40f)
        val availableTranslations = GameBasics.Translations.filter { it.value.containsKey(language) }

        if(language=="English") percentComplete = 100
        else percentComplete = (availableTranslations.size*100 / GameBasics.Translations.size) - 5

        val spaceSplitLang = language.replace("_"," ")
        add("$spaceSplitLang ($percentComplete%)")
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
                    "community-based work in progress and are INCOMPLETE! \n" +
                    "The percentage shown is how much of the language is translated in-game.\n" +
                    "If you want to help translating the game into your language, \n"+
                    "  instructions are in the Github readme! (Menu > Community > Github)",skin)).pad(10f).row()

        languageTables.addAll(GameBasics.Translations.getLanguages().map { LanguageTable(it,skin) }
                .sortedByDescending { it.percentComplete } )

        languageTables.forEach {
            it.onClick {
                chosenLanguage = it.language
                rightSideButton.enable()
                update()
            }
            topTable.add(it).pad(10f).row()
        }

        rightSideButton.setText("Pick language".tr())


        rightSideButton.onClick {
            pickLanguage()
        }
    }

    fun pickLanguage(){
        UnCivGame.Current.settings.language = chosenLanguage
        UnCivGame.Current.settings.save()
        resetFonts()
        UnCivGame.Current.startNewGame()
        dispose()
    }
}
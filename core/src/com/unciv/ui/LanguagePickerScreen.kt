package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel


class LanguageTable(val language:String, val percentComplete: Int):Table(){
    private val blue = ImageGetter.getBlue()
    private val darkBlue = blue.cpy().lerp(Color.BLACK,0.5f)!!

    init{
        pad(10f)
        defaults().pad(10f)
        if(ImageGetter.imageExists("FlagIcons/$language"))
            add(ImageGetter.getImage("FlagIcons/$language")).size(40f)

        val spaceSplitLang = language.replace("_"," ")
        add("$spaceSplitLang ($percentComplete%)".toLabel())
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
        val translationDisclaimer = "Please note that translations are a " +
                "community-based work in progress and are INCOMPLETE! \n" +
                "The percentage shown is how much of the language is translated in-game.\n" +
                "If you want to help translating the game into your language, \n"+
                "  instructions are in the Github readme! (Menu > Community > Github)"
        topTable.add(translationDisclaimer.toLabel()).pad(10f).row()

        val languageCompletionPercentage = UncivGame.Current.translations
                .percentCompleteOfLanguages
        languageTables.addAll(languageCompletionPercentage
                .map { LanguageTable(it.key,if(it.key=="English") 100 else it.value) }
                .sortedByDescending { it.percentComplete} )

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
        UncivGame.Current.settings.language = chosenLanguage
        UncivGame.Current.settings.save()

        UncivGame.Current.translations.tryReadTranslationForCurrentLanguage()
        resetFonts()
        UncivGame.Current.startNewGame()
        dispose()
    }
}
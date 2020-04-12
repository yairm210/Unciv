package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
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

    fun update(chosenLanguage:String, scrollPane: ScrollPane? = null){
        background = ImageGetter.getBackground( if(chosenLanguage==language) blue else darkBlue)
        if (chosenLanguage==language) {
            scrollPane?.scrollTo (x, y, width, height)
        }
    }

}

class LanguagePickerScreen: PickerScreen() {
    var chosenLanguage = "English"

    private val languageTables = ArrayList<LanguageTable>()

    fun update(){
        languageTables.forEach { it.update(chosenLanguage, scrollPane) }
    }

    init {
        closeButton.isVisible = false
        /// trimMargin is overhead, but easier to maintain and see when it might get trimmed without wrap:
        val translationDisclaimer = """
            |Please note that translations are a community-based work in progress and are INCOMPLETE!
            |The percentage shown is how much of the language is translated in-game.
            |If you want to help translating the game into your language,
            |  instructions are in the Github readme! (Menu > Community > Github)
            """.trimMargin()
        topTable.add(translationDisclaimer.toLabel()).pad(10f).row()

        val languageCompletionPercentage = UncivGame.Current.translations
                .percentCompleteOfLanguages
        languageTables.addAll(languageCompletionPercentage
                .map { LanguageTable(it.key,if(it.key=="English") 100 else it.value) }
                .sortedByDescending { it.percentComplete} )

        languageTables.forEach {
            val action = {
                chosenLanguage = it.language
                rightSideButton.enable()
                update()
            }
            it.onClick (action)
            registerKeyHandler (it.language, action)
            topTable.add(it).pad(10f).row()
        }

        setAcceptButtonAction ("Pick language") {
            pickLanguage()
        }
    }

    fun pickLanguage(){
        UncivGame.Current.settings.language = chosenLanguage
        UncivGame.Current.settings.isFreshlyCreated = false     // mark so the picker isn't called next launch
        UncivGame.Current.settings.save()

        UncivGame.Current.translations.tryReadTranslationForCurrentLanguage()
        resetFonts()
        UncivGame.Current.startNewGame()
        dispose()
    }
}
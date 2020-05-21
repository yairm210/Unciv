package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*


class LanguageTable(val language:String, val percentComplete: Int):Table(){
    private val blue = ImageGetter.getBlue()
    private val darkBlue = blue.cpy().lerp(Color.BLACK,0.5f)!!

    init{
        pad(10f)
        defaults().pad(10f)
        left()
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

class LanguagePickerScreen(): PickerScreen(){
    var chosenLanguage = "English"

    private val languageTables = ArrayList<LanguageTable>()

    fun update(){
        languageTables.forEach { it.update(chosenLanguage) }
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
        val tableLanguages = Table();
        tableLanguages.defaults().uniformX();
        tableLanguages.defaults().pad(10.0f);
        tableLanguages.defaults().fillX();
        topTable.add(tableLanguages).row();

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
            tableLanguages.add(it).row()
        }

        rightSideButton.setText("Pick language".tr())
        rightSideButton.onClick {
            pickLanguage()
        }
    }

    fun pickLanguage(){
        game.settings.language = chosenLanguage
        game.settings.isFreshlyCreated = false     // mark so the picker isn't called next launch
        game.settings.save()

        game.translations.tryReadTranslationForCurrentLanguage()
        game.setScreen(MainMenuScreen())
        dispose()
    }
}
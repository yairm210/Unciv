package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.toLabel

/** Represents a row in the Language picker, used both in OptionsPopup and in LanguagePickerScreen */
internal class LanguageTable(val language:String, val percentComplete: Int): Table(){
    private val blue = ImageGetter.getBlue()
    private val darkBlue = blue.darken(0.5f)

    init{
        pad(10f)
        defaults().pad(10f)
        left()
        if(ImageGetter.imageExists("FlagIcons/$language"))
            add(ImageGetter.getImage("FlagIcons/$language")).size(40f)

        val spaceSplitLang = language.replace("_"," ")
        add("$spaceSplitLang ($percentComplete%)".toLabel())
        update("")
        touchable =
            Touchable.enabled // so click listener is activated when any part is clicked, not only children
        pack()
    }

    fun update(chosenLanguage:String){
        background = ImageGetter.getBackground(if (chosenLanguage == language) blue else darkBlue)
    }

    companion object {
        /** Extension to add the Language boxes to a Table, used both in OptionsPopup and in LanguagePickerScreen */
        internal fun Table.addLanguageTables(expectedWidth: Float): ArrayList<LanguageTable> {
            val languageTables = ArrayList<LanguageTable>()

            val translationDisclaimer = FormattedLine(
                text = "Please note that translations are a community-based work in progress and are" +
                    " INCOMPLETE! The percentage shown is how much of the language is translated in-game." +
                    " If you want to help translating the game into your language, click here.",
                link = "https://yairm210.github.io/Unciv/Other/Translating/",
                size = 15
            )
            add(MarkupRenderer.render(listOf(translationDisclaimer),expectedWidth)).pad(5f).row()

            val tableLanguages = Table()
            tableLanguages.defaults().uniformX()
            tableLanguages.defaults().pad(10.0f)
            tableLanguages.defaults().fillX()

            val languageCompletionPercentage = UncivGame.Current.translations
                .percentCompleteOfLanguages
            languageTables.addAll(languageCompletionPercentage
                .map { LanguageTable(it.key, if (it.key == "English") 100 else it.value) }
                .sortedByDescending { it.percentComplete} )

            languageTables.forEach {
                tableLanguages.add(it).row()
            }
            add(tableLanguages).row()

            return languageTables
        }
    }
}

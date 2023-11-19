package com.unciv.ui.components.widgets

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import java.util.Locale

/** Represents a row in the Language picker, used both in OptionsPopup and in LanguagePickerScreen */
internal class LanguageTable(val language:String, val percentComplete: Int) : Table() {
    private val baseColor = BaseScreen.skinStrings.skinConfig.baseColor
    private val darkBaseColor = baseColor.darken(0.5f)

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

    fun update(chosenLanguage:String) {
        background = BaseScreen.skinStrings.getUiBackground(
            "LanguagePickerScreen/LanguageTable",
            tintColor = if (chosenLanguage == language) baseColor else darkBaseColor
        )
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

            val systemLanguage = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)

            val languageCompletionPercentage = UncivGame.Current.translations
                .percentCompleteOfLanguages
            languageTables.addAll(languageCompletionPercentage
                .map { LanguageTable(it.key, if (it.key == Constants.english) 100 else it.value) }
                .sortedWith { p0, p1 ->
                    when {
                        p0.language == Constants.english -> -1
                        p1.language == Constants.english -> 1
                        p0.language == systemLanguage -> -1
                        p1.language == systemLanguage -> 1
                        p0.percentComplete > p1.percentComplete -> -1
                        p0.percentComplete == p1.percentComplete -> 0
                        else -> 1
                    }
                })

            languageTables.forEach {
                tableLanguages.add(it).row()
            }
            add(tableLanguages).row()

            return languageTables
        }
    }
}

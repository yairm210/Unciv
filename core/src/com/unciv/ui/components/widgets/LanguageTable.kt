package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.models.metadata.LocaleCode
import com.unciv.models.translations.CompletionPercentage
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.DiacriticSupport
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyShortcutDispatcher
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.options.OptionsPopup
import com.unciv.ui.screens.LanguagePickerScreen
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer


/** Represents a row in the Language picker, used both in [OptionsPopup] and in [LanguagePickerScreen]
 *  @see addLanguageTables
 */
internal class LanguageTable private constructor(
    val language: String,
    val percentComplete: CompletionPercentage,
    diacriticSupport: DiacriticSupport
) : Table() {
    private val baseColor = BaseScreen.skinStrings.skinConfig.baseColor
    private val darkBaseColor = baseColor.darken(0.5f)

    init {
        pad(10f)
        defaults().pad(10f)
        left()
        if (ImageGetter.imageExists("FlagIcons/$language"))
            add(ImageGetter.getImage("FlagIcons/$language")).size(40f).padBottom(10f + Fonts.getDescenderHeight(Constants.defaultFontSize))
        else
            add(Actor()).minWidth(40f)

        val englishExonym = language.replace("_"," ")
        fun String.withPercentLabel() = "$this ({${percentComplete.toDisplayString()}}%)".toLabel()
        fun String.secondaryLabel() = "$this{}".toLabel(Color.LIGHT_GRAY)
        val endonym = LocaleCode.find(language)?.endonym?.takeIf { it != language }
        if (endonym == null) {
            add(englishExonym.withPercentLabel())
        } else {
            val endonymTranslated = diacriticSupport.remapDiacritics(endonym)
            add(Table().apply {
                add(endonymTranslated.withPercentLabel()).growX().row()
                add(englishExonym.secondaryLabel()).growX()
            })
        }
        update("")
        touchable =
            Touchable.enabled // so click listener is activated when any part is clicked, not only children
        pack()
    }

    fun update(chosenLanguage: String) {
        background = BaseScreen.skinStrings.getUiBackground(
            "LanguagePickerScreen/LanguageTable",
            tintColor = if (chosenLanguage == language) baseColor else darkBaseColor
        )
    }

    companion object {
        /** Extension to add the Language boxes to a Table, used both in OptionsPopup and in LanguagePickerScreen */
        fun Table.addLanguageTables(expectedWidth: Float): ArrayList<LanguageTable> {
            val languageTables = ArrayList<LanguageTable>()

            val diacriticSupport = DiacriticSupport(enabled = true, CharRange.EMPTY, "", "", "") // Covers everything!

            val translationDisclaimer = FormattedLine(
                text = "Please note that translations are a community-based work in progress and are" +
                    " INCOMPLETE! The percentage shown is how much of the language is translated in-game." +
                    " If you want to help translating the game into your language, click here.",
                link = "${Constants.wikiURL}Translating/Translating/",
                size = 15
            )
            add(MarkupRenderer.render(listOf(translationDisclaimer),expectedWidth)).pad(5f).row()

            val tableLanguages = Table()
            tableLanguages.defaults().uniformX().fillX().pad(10.0f)

            val systemLanguage = LocaleCode.getSystemLanguage()

            val languageCompletionPercentage = UncivGame.Current.translations
                .percentCompleteOfLanguages
            languageTables.addAll(
                languageCompletionPercentage
                    .map { LanguageTable(it.key, if (it.key == Constants.english) CompletionPercentage.COMPLETE else it.value, diacriticSupport) }
                    .sortedWith(
                        compareBy<LanguageTable> { it.language != Constants.english }
                            .thenBy { it.language != systemLanguage }
                            .thenByDescending { it.percentComplete }
                    )
            )

            languageTables.forEach {
                tableLanguages.add(it).row()
            }
            add(tableLanguages).row()

            return languageTables
        }

        /** Create round-robin letter key handling, such that repeatedly pressing 'R' will cycle through all languages starting with 'R'
         *
         *  Note this is using only the English exonyms - including endonym initial if it's a Latin letter, accent-stripped,
         *  is possible but potentially confusing. Actual user awareness of the feature might be low, too.
         */
        fun Actor.addLanguageKeyShortcuts(languageTables: ArrayList<LanguageTable>, getSelection: ()->String, action: (String)->Unit) {
            // Yes this is too complicated. Trying to preserve existing architecture choices.
            // One - extending KeyShortcut to allow another type filtering by a lambda,
            //       then teach KeyShortcutDispatcher.Resolver to recognize that - and pass on the actual key to its activation - could help.
            // Two - Changing addLanguageTables above to an actual container class holding the LanguageTables - could help.
            fun activation(letter: Char) {
                val candidates = languageTables.filter { it.language.first() == letter }
                if (candidates.isEmpty()) return
                if (candidates.size == 1) return action(candidates.first().language)
                val currentIndex = candidates.indexOfFirst { it.language == getSelection() }
                val newSelection = candidates[(currentIndex + 1) % candidates.size]
                action(newSelection.language)
            }

            val letters = languageTables.map { it.language.first() }.toSet()
            for (letter in letters) {
                keyShortcuts.add(KeyShortcutDispatcher.KeyShortcut(KeyboardBinding.None, KeyCharAndCode(letter), 0)) {
                    activation(letter)
                }
            }
        }
    }
}

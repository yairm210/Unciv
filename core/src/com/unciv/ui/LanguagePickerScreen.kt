package com.unciv.ui

import com.unciv.MainMenuScreen
import com.unciv.models.translations.tr
import com.unciv.ui.options.OptionsPopup
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.LanguageTable
import com.unciv.ui.utils.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.utils.extensions.enable
import com.unciv.ui.utils.extensions.onClick

/** A [PickerScreen] to select a language, used once on the initial run after a fresh install.
 *  After that, [OptionsPopup] provides the functionality.
 *  Reusable code is in [LanguageTable] and [addLanguageTables].
 */
class LanguagePickerScreen : PickerScreen() {
    var chosenLanguage = "English"

    private val languageTables: ArrayList<LanguageTable>

    fun update() {
        languageTables.forEach { it.update(chosenLanguage) }
    }

    init {
        closeButton.isVisible = false

        languageTables = topTable.addLanguageTables(stage.width - 60f)

        languageTables.forEach {
            it.onClick {
                chosenLanguage = it.language
                rightSideButton.enable()
                update()
            }
        }

        rightSideButton.setText("Pick language".tr())
        rightSideButton.onClick {
            pickLanguage()
        }
    }

    fun pickLanguage(){
        game.settings.language = chosenLanguage
        game.settings.updateLocaleFromLanguage()
        game.settings.isFreshlyCreated = false     // mark so the picker isn't called next launch
        game.settings.save()

        game.translations.tryReadTranslationForCurrentLanguage()
        game.replaceCurrentScreen(MainMenuScreen())
    }
}

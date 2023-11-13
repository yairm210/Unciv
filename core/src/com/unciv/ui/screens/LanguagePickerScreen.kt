package com.unciv.ui.screens

import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.popups.options.OptionsPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.components.widgets.LanguageTable
import com.unciv.ui.components.widgets.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen

/** A [PickerScreen] to select a language, used once on the initial run after a fresh install.
 *  After that, [OptionsPopup] provides the functionality.
 *  Reusable code is in [LanguageTable] and [addLanguageTables].
 */
class LanguagePickerScreen : PickerScreen() {
    var chosenLanguage = Constants.english

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

    fun pickLanguage() {
        game.settings.language = chosenLanguage
        game.settings.updateLocaleFromLanguage()
        game.settings.isFreshlyCreated = false     // mark so the picker isn't called next launch
        game.settings.save()

        game.translations.tryReadTranslationForCurrentLanguage()
        game.replaceCurrentScreen(MainMenuScreen())
    }
}

package com.unciv.ui.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.utils.extensions.onClick

fun languageTab(
    optionsPopup: OptionsPopup,
    onLanguageSelected: () -> Unit
): Table = Table(BaseScreen.skin).apply {
    val settings = optionsPopup.settings

    val languageTables = this.addLanguageTables(optionsPopup.tabs.prefWidth * 0.9f - 10f)

    var chosenLanguage = settings.language
    fun selectLanguage() {
        settings.language = chosenLanguage
        settings.updateLocaleFromLanguage()
        UncivGame.Current.translations.tryReadTranslationForCurrentLanguage()
        onLanguageSelected()
    }

    fun updateSelection() {
        languageTables.forEach { it.update(chosenLanguage) }
        if (chosenLanguage != settings.language)
            selectLanguage()
    }
    updateSelection()

    languageTables.forEach {
        it.onClick {
            chosenLanguage = it.language
            updateSelection()
        }
    }
}

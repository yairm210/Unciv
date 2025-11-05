package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.getAscendant
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LanguageTable.Companion.addLanguageKeyShortcuts
import com.unciv.ui.components.widgets.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.components.widgets.TabbedPager

internal class LanguageTab(
    optionsPopup: OptionsPopup,
    private val onLanguageSelected: () -> Unit
): Table(), TabbedPager.IPageExtensions {
    private val languageTables = this.addLanguageTables(optionsPopup.tabs.prefWidth * 0.9f - 10f)
    private val settings = optionsPopup.settings
    private var chosenLanguage = settings.language

    private fun selectLanguage() {
        settings.language = chosenLanguage
        settings.updateLocaleFromLanguage()
        UncivGame.Current.translations.tryReadTranslationForCurrentLanguage()
        onLanguageSelected()
    }

    private fun updateSelection() {
        languageTables.forEach { it.update(chosenLanguage) }
        if (chosenLanguage != settings.language)
            selectLanguage()
    }

    init {
        for (langTable in languageTables) {
            langTable.onClick {
                chosenLanguage = langTable.language
                updateSelection()
            }
        }
        addLanguageKeyShortcuts(languageTables, getSelection = { chosenLanguage }) {
            chosenLanguage = it
            val pager = this.getAscendant<TabbedPager>()
                ?: return@addLanguageKeyShortcuts
            activated(pager.activePage, "", pager)
        }
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        updateSelection()
        val selectedTable = languageTables.firstOrNull { it.language == chosenLanguage }
            ?: return
        pager.pageScrollTo(selectedTable, true)
    }
}

package com.unciv.ui.popups.options

import com.unciv.ui.components.extensions.getAscendant
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LanguageTable.Companion.addLanguageKeyShortcuts
import com.unciv.ui.components.widgets.LanguageTable.Companion.addLanguageTables
import com.unciv.ui.components.widgets.TabbedPager

internal class LanguageTab(
    optionsPopup: OptionsPopup
): OptionsPopupTab(optionsPopup) {
    private val languageTables by lazy { this.addLanguageTables(optionsPopup.tabs.prefWidth * 0.9f - 10f) }
    private var chosenLanguage = settings.language

    init {
        pad(0f)
        defaults().pad(0f)
    }

    override fun lateInitialize() {
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
        super.lateInitialize()
    }

    private fun selectLanguage() {
        settings.language = chosenLanguage
        settings.updateLocaleFromLanguage()
        game.translations.tryReadTranslationForCurrentLanguage()
        reloadWorldAndOptions()
    }

    private fun updateSelection() {
        languageTables.forEach { it.update(chosenLanguage) }
        if (chosenLanguage != settings.language)
            selectLanguage()
    }

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        super.activated(index, caption, pager)
        updateSelection()
        val selectedTable = languageTables.firstOrNull { it.language == chosenLanguage }
            ?: return
        pager.pageScrollTo(selectedTable, true)
    }
}

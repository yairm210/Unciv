package com.unciv.ui.components.widgets

import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.setItems
import com.unciv.ui.screens.basescreen.BaseScreen

class TranslatedSelectBox(values: Collection<String>, default: String) : SelectBox<TranslatedSelectBox.TranslatedString>(BaseScreen.skin) {
    class TranslatedString(val value: String) {
        val translation = value.tr(hideIcons = true)
        override fun toString() = translation
        // Equality contract needs to be implemented else TranslatedSelectBox.setSelected won't work properly
        override fun equals(other: Any?): Boolean = other is TranslatedString && value == other.value
        override fun hashCode() = value.hashCode()
    }

    init {
        setItems(values.map { TranslatedString(it) })
        selected = items.firstOrNull { it.value == default } ?: items.first()
    }

    fun setSelected(newValue: String) {
        selected = items.firstOrNull { it == TranslatedString(newValue) } ?: return
    }
}

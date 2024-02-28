package com.unciv.ui.components.widgets

import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Array
import com.unciv.models.translations.tr

class TranslatedSelectBox(values: Collection<String>, default: String, skin: Skin) : SelectBox<TranslatedSelectBox.TranslatedString>(skin) {
    class TranslatedString(val value: String) {
        val translation = value.tr(hideIcons = true)
        override fun toString() = translation
        // Equality contract needs to be implemented else TranslatedSelectBox.setSelected won't work properly
        override fun equals(other: Any?): Boolean = other is TranslatedString && value == other.value
        override fun hashCode() = value.hashCode()
    }

    init {
        val array = Array<TranslatedString>()
        values.forEach { array.add(TranslatedString(it)) }
        items = array
        selected = array.firstOrNull { it.value == default } ?: array.first()
    }

    fun setSelected(newValue: String) {
        selected = items.firstOrNull { it == TranslatedString(newValue) } ?: return
    }
}

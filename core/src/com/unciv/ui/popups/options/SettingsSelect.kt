package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Array
import com.unciv.models.UncivSound
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.GameSetting
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.toGdxArray
import kotlin.reflect.KMutableProperty0


/**
 * For creating a SelectBox that is automatically backed by a [GameSettings] property.
 *
 * **Warning:** [T] has to be the same type as the [GameSetting.kClass] of the [GameSetting] argument.
 *
 * This will also automatically send [SettingsPropertyChanged] events.
 */
open class SettingsSelect<T>(
    labelText: String,
    items: Iterable<SelectItem<T>>,
    setting: GameSetting,
    settings: GameSettings
) {
    class SelectItem<T>(val label: String, val value: T) {
        override fun toString(): String = label.tr()
        override fun equals(other: Any?): Boolean = other is SelectItem<*> && value == other.value
        override fun hashCode(): Int = value.hashCode()
    }

    private val settingsProperty: KMutableProperty0<T> = setting.getProperty(settings)
    private val label = createLabel(labelText)
    private val refreshSelectBox = createSelectBox(items.toGdxArray())
    @Suppress("HasPlatformType")  // Compiler problem
    // Explicit type Array<SelectItem<T>> as suggested crashes the compiler, except if one
    // replaces `by x::` with `get() = x.` which according to docs should be entirely equivalent
    val items by refreshSelectBox::items

    private fun createLabel(labelText: String): Label {
        val selectLabel = labelText.toLabel()
        selectLabel.wrap = true
        return selectLabel
    }

    private fun createSelectBox(initialItems: Array<SelectItem<T>>): SelectBox<SelectItem<T>> {
        val selectBox = SelectBox<SelectItem<T>>(BaseScreen.skin)
        selectBox.items = initialItems

        selectBox.selected = initialItems.firstOrNull { it.value == settingsProperty.get() } ?: initialItems.first()
        selectBox.onChange {
            val newValue = selectBox.selected.value
            settingsProperty.set(newValue)
            if (newValue is UncivSound) SoundPlayer.play(newValue)
        }

        return selectBox
    }

    fun onChange(listener: (event: ChangeListener.ChangeEvent?) -> Unit) {
        refreshSelectBox.onChange(listener)
    }

    fun addTo(table: Table) {
        table.add(label).growX().left()
        table.add(refreshSelectBox).row()
    }

    /** Maintains the currently selected item if possible, otherwise resets to the first item */
    fun replaceItems(options: Array<SelectItem<T>>) {
        val prev = refreshSelectBox.selected
        refreshSelectBox.items = options
        refreshSelectBox.selected = prev
    }
}

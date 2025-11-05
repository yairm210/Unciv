package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.format
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onChange
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.toGdxArray
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.reflect.KMutableProperty0


abstract class SettingsSelect<T>(
    private val text: String,
    property: KMutableProperty0<T>,
    items: Iterable<SelectItem<T>>,
    changeCallback: (T) -> Unit = {}
) : SelectBox<SettingsSelect.SelectItem<T>>(BaseScreen.skin) {

    open class SelectItem<T>(val label: String, val value: T) {
        override fun toString(): String = label.tr()
        override fun equals(other: Any?): Boolean = other is SelectItem<T> && value == other.value
        override fun hashCode(): Int = value.hashCode()
    }

    init {
        val initialItems = items.toGdxArray()
        this.items = initialItems
        val initialValue = property.get()
        selected = initialItems.firstOrNull { it.value == initialValue } ?: initialItems.first()
        onChange {
            val newValue = selected.value
            property.set(newValue)
            changeCallback(newValue)
        }
    }

    fun addTo(table: Table) {
        table.add(text.toLabel().apply { wrap = true }).growX().left()
        table.add(this).right().row()
    }

    /** Maintains the currently selected item if possible, otherwise resets to the first item */
    fun replaceItems(newItems: Array<SelectItem<T>>) {
        val prev = selected
        items = newItems
        selected = prev
    }
}

internal class RefreshSelect(
    text: String,
    property: KMutableProperty0<Duration>,
    extraCustomServerOptions: Iterable<DurationItem>,
    dropboxOptions: Iterable<DurationItem>
) : SettingsSelect<Duration>(text, property, chooseInitialItems(extraCustomServerOptions, dropboxOptions)) {
    class DurationItem(label: String, value: Duration) : SelectItem<Duration>(label, value) {
        constructor(duration: Duration) : this(duration.format(), duration)
    }

    private val customServerItems: Array<SelectItem<Duration>> = (extraCustomServerOptions + dropboxOptions).toGdxArray()
    private val dropboxItems: Array<SelectItem<Duration>> = dropboxOptions.toGdxArray()

    fun update(isCustomServer: Boolean) {
        if (isCustomServer && items.size != customServerItems.size) {
            replaceItems(customServerItems)
        } else if (!isCustomServer && items.size != dropboxItems.size) {
            replaceItems(dropboxItems)
        }
    }

    companion object {
        fun createRefreshOptions(unit: ChronoUnit, vararg options: Long) =
            options.map { DurationItem(Duration.of(it, unit)) }

        private fun chooseInitialItems(
            extraCustomServerOptions: Iterable<DurationItem>,
            dropboxOptions: Iterable<DurationItem>
        ): Iterable<DurationItem> =
            if (Multiplayer.usesCustomServer()) extraCustomServerOptions + dropboxOptions else dropboxOptions
    }
}

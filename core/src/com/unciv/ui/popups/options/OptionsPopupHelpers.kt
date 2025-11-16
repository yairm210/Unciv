package com.unciv.ui.popups.options

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.TranslatedSelectBox
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.popups.activePopup
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import com.unciv.utils.toGdxArray
import com.unciv.utils.withGLContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KMutableProperty0

/**
 *  Helper library for [OptionsPopup] and its tabs.
 *
 *  Interface used as namespace and to share variables, included in [OptionsPopupTab].
 *
 *  Content Helpers: [addCheckbox], [addHeader], [addSelectBox], [addSlider], [addWrapped]
 *  Variants: [addEnumAsStringSelectBox], [addTranslatedSelectBox], [addAsyncSelectBox]
 *  Reload helpers: [reloadWorldAndOptions], [reopenOptions]
 *
 *  Usage without `OptionsPopupTab`:
 *  ```
 *  internal class NewOptionsPage(
 *     optionsPopup: OptionsPopup
 * ) : Table(BaseScreen.skin), OptionsPopupHelpers {
 *     override val rightWidgetMinWidth by optionsPopup::rightWidgetMinWidth
 *     override val activePage get() = optionsPopup.tabs.activePage
 *     ...
 *  ```
 *
 *  TODO
 *    * Performance - what is taking so long? Use tabs doing their heavy lifting only on activation, like ModCheckTab?
 */
internal interface OptionsPopupHelpers {
    /** Suggested minimum width for a right-hand cell in a typical 2-column label/widget row.
     *  Calculated by [OptionsPopup] constructor from stage size.
     */
    val rightWidgetMinWidth: Float

    /** Access the active page number of the TabbedPager in [OptionsPopup] */
    val activePage: Int


    /**
     *  Adds a header row (larger font, centered, colspan 2).
     *
     *  Unless the receiver is empty, a separator is added before the header.
     *  @return the [Label] cell for chaining
     */
    fun Table.addHeader(text: String): Cell<Label> {
        if (!children.isEmpty) // doesn't count empty cells
            addSeparator()
        val cell = add(text.toLabel(fontSize = Constants.headingFontSize))
        cell.colspan(2).spaceTop(10f).row()
        return cell
    }

    /**
     *  Adds a [Label] and [CheckBox] as two Cells into a table row or cell (without using a reference).
     *
     *  It's your responsibility to provide the inital value and to write it back in [action].
     *  There's an overload using a reference instead (recommended).
     *
     *  @param updateWorld If `true` and the active screen is a [WorldScreen], the [WorldScreen.shouldUpdate] flag is set when the value changes.
     *  @param newRow `true`: The cell gets `colspan(2)` and `row()`.
     *  @param action Mandatory - you'll need to write back the new value here.
     */
    fun Table.addCheckbox(text: String, initialState: Boolean, updateWorld: Boolean = false, newRow: Boolean = true, action: ((Boolean) -> Unit)) {
        val checkbox = text.toCheckBox(initialState) {
            action(it)
            val worldScreen = GUI.getWorldScreenIfActive()
            if (updateWorld && worldScreen != null) worldScreen.shouldUpdate = true
        }
        if (newRow) add(checkbox).colspan(2).left().row()
        else add(checkbox).left()
    }

    /**
     *  Adds a [Label] and [CheckBox] as two Cells into a table row (using a reference).
     *
     *  Retrieving the value and writing it back is handled for you.
     *  There's an overload using manual value passing instead.
     *
     *  @param settingsProperty A `::` reference to a class field (references to local variables aren't yet supported)
     *  @param updateWorld If `true` and the active screen is a [WorldScreen], the [WorldScreen.shouldUpdate] flag is set when the value changes.
     *  @param action Optional if you need to take further action when the value is set.
     */
    fun Table.addCheckbox(text: String, settingsProperty: KMutableProperty0<Boolean>, updateWorld: Boolean = false, action: (Boolean) -> Unit = {}) {
        addCheckbox(text, settingsProperty.get(), updateWorld) {
            settingsProperty.set(it)
            action(it)
        }
    }

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row.
     *
     *  **Entries will only be translated when the item class provides a toString() doing so.**
     *
     *  @param action Optional callback, after the value is set, arguments are the new value and the old value
     *  @see addTranslatedSelectBox
     *  @see addEnumAsStringSelectBox
     */
    fun <T> Table.addSelectBox(text: String, property: KMutableProperty0<T>, items: Iterable<T>, action: ((T, T) -> Unit)? = null) {
        add(text.toLabel()).left().fillX()

        val select = SelectBox<T>(BaseScreen.skin)
        select.setItems(items.toGdxArray())
        select.selected = property.get()
        add(select).pad(10f).minWidth(rightWidgetMinWidth).right().row()

        select.onChange {
            val oldValue = property.get()
            val newValue = select.selected
            property.set(newValue)
            action?.invoke(newValue, oldValue)
        }
    }

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row.
     *
     *  **Entries will be translated for you.**
     *
     *  @param action Optional callback, after the value is set, argument is the new value
     *  @see addSelectBox
     *  @see addEnumAsStringSelectBox
     */
    fun Table.addTranslatedSelectBox(text: String, property: KMutableProperty0<String>, items: Iterable<String>, action: ((String) -> Unit)? = null) {
        add(text.toLabel()).left().fillX()
        val select = TranslatedSelectBox(items as? Collection ?: items.toList(), property.get())
        add(select).pad(10f).minWidth(rightWidgetMinWidth).right().row()

        select.onChange {
            val newValue = select.selected.value
            property.set(newValue)
            action?.invoke(newValue)
        }
    }

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row.
     *
     *  **This is for the case an enum is actually stored as string in GameSettings.**
     *  **Entries will be translated for you.**
     *
     *  @param action Optional callback, after the value is set, argument is the new value
     *  @see addSelectBox
     *  @see addTranslatedSelectBox
     */
    fun <T : Enum<T>> Table.addEnumAsStringSelectBox(
        text: String,
        property: KMutableProperty0<String>,
        items: Iterable<Enum<T>>,
        action: ((String) -> Unit)? = null
    ) = addTranslatedSelectBox(text, property, items.map { it.name }, action)

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row, filling the items **asynchronously**.
     *
     *  The Label will be gray and the SelectBox disabled until the items arrive.
     *  **Entries will only be translated when the item class provides a toString() doing so.**
     *
     *  @param action Optional callback, after the value is set, argument is the new value
     *  @see addSelectBox
     */
    fun <T> Table.addAsyncSelectBox(
        text: String,
        property: KMutableProperty0<T>,
        provider: suspend () -> Flow<T>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        action: ((T) -> Unit)? = null
    ) {
        val label = text.toLabel(Color.GRAY)
        label.wrap = true
        add(label).left().fillX()
        val select = SelectBox<T>(BaseScreen.skin)
        select.isDisabled = true
        add(select).pad(10f).minWidth(rightWidgetMinWidth).right().row()

        Concurrency.run("Async SelectBox", CoroutineScope(dispatcher)) {
            val items = Array<T>(false, 20)
            provider().collect { items.add(it) }
            launchOnGLThread {
                select.setItems(items)
                val toSelect = property.get()
                // make sure the instance we pass to select is actually contained in the items array, because Gdx' Selection.set uses identity comparisons
                select.selected = items.firstOrNull { it == toSelect } // will default to first entry if `null` is passed
                select.isDisabled = false
                label.setFontColor(Color.WHITE)
                select.onChange {
                    val value = select.selected
                    property.set(value)
                    action?.invoke(value)
                }
                select.invalidateHierarchy()
            }
        }
    }

    /**
     *  Adds a [Label] and [UncivSlider] as two Cells into a table row, generic version.
     *
     *  Requires the caller to retrieve and write back the value.
     *
     *  @param initial Setting value as its original type, will be converted to Float
     *  @param min, [max], [step] - passed to UncivSlider as Float
     *  @param action Callback where you should convert and write back the value, and optionally take further action.
     *         Arguments are the value and the state of isDragging - don't do heavy lifting while it is true.
     */
    fun <T: Number> Table.addSlider(
        text: String, initial: T, min: T, max: T, step: T,
        getTipText: ((Float) -> String)? = null,
        action: ((Float, Boolean) -> Unit)
    ): Cell<UncivSlider> {
        add(text.toLabel()).left().fillX().padTop(5f)

        // lateinit, otherwise the callback can't capture it, and by the time the callback fires this will be initialized
        lateinit var slider: UncivSlider
        slider = UncivSlider(
            min.toFloat(), max.toFloat(), step.toFloat(),
            initial = initial.toFloat(),
            getTipText = getTipText
        ) {
            action(it, slider.isDragging)
        }
        val cell = add(slider).minWidth(rightWidgetMinWidth).right().pad(5f).padTop(10f)
        cell.row()
        return cell
    }

    /**
     *  Adds a [Label] and [UncivSlider] as two Cells into a table row, Float property.
     *
     *  Handles value retrieval and writeback for you.
     *
     *  @param property A reference to a Float field to link to the slider value
     *  @param min, [max], [step] - passed to UncivSlider
     *  @param action Callback only called when not dragging, optional (the value will be written automatically)
     */
    fun Table.addSlider(
        text: String, property: KMutableProperty0<Float>,
        min: Float, max: Float, step: Float = 1f,
        getTipText: ((Float) -> String)? = null,
        action: (Float) -> Unit = {}
    ): Cell<UncivSlider> {
        return addSlider(text, property.get(), min, max, step, getTipText) { value, dragging ->
            if (!dragging)
                action(value)
            property.set(value)
        }
    }

    /** Wrap something in a [Table] and add it to the receiver as single-cell row.
     *
     *  @receiver Destination [Table]
     *  @param newRow If set, the new Cell will get `.fillX().colspan(2).row()`
     *  @param block Has the wrapper table as receiver, so you simply build with add(*) or any of the [OptionsPopupHelpers] methods.
     *  @return The new [Cell] containing the wrapped result
     */
    fun Table.addWrapped(newRow: Boolean = true, block: Table.() -> Unit): Cell<Table> {
        val cell = add(Table().apply {
            block()
        })
        if (newRow) cell.fillX().colspan(2).row()
        return cell
    }

    /** Reload the [OptionsPopup] after major changes (resolution, tileset, language, font) */
    fun reloadWorldAndOptions() {
        Concurrency.run("Reload from options") {
            withGLContext {
                // We have to run setSkin before the screen is rebuild else changing skins
                // would only load the new SkinConfig after the next rebuild
                BaseScreen.setSkin()
            }
            val screen = UncivGame.Current.screen
            if (screen is WorldScreen) {
                UncivGame.Current.reloadWorldscreen()
            } else if (screen is MainMenuScreen) {
                withGLContext {
                    UncivGame.Current.replaceCurrentScreen(MainMenuScreen())
                }
            }
            withGLContext {
                UncivGame.Current.screen?.openOptionsPopup(activePage)
            }
        }
    }

    /** Reload the [OptionsPopup] after changes that require a fresh layout.
     *
     *  If [force] is `true`, it will always close and reopen the OptionsPopup.
     *  If [force]`false`, it does nothing if any Popup (which can only be this one) is still open after a short delay and context yield.
     *  Reason: A resize _might_ relaunch the parent screen ([MainMenuScreen] is [RecreateOnResize]) and thus close this Popup - or not.
     */
    fun reopenOptions(force: Boolean = false) {
        Concurrency.run("Reload from options") {
            delay(if (force) 0 else 100)
            withGLContext {
                val screen = UncivGame.Current.screen ?: return@withGLContext
                if (force && screen.activePopup is OptionsPopup) screen.activePopup!!.close()
                if (screen.hasOpenPopups()) return@withGLContext // e.g. Orientation auto to fixed while auto is already the new orientation
                screen.openOptionsPopup(activePage)
            }
        }
    }
}

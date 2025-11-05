package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.widgets.TranslatedSelectBox
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
 *  Helper library for OptionsPopup and its tabs.
 *
 *  Interface used as namespace and to share variables.
 *
 *  Usage:
 *  ```
 *  internal class NewOptionsPage(
 *     optionsPopup: OptionsPopup
 * ) : Table(BaseScreen.skin), OptionsPopupHelpers {
 *     override val selectBoxMinWidth by optionsPopup::selectBoxMinWidth
 *     ...
 *  ```
 */
internal interface OptionsPopupHelpers {
    /** Suggested minimum width for a right-hand cell in a typical 2-column label/widget row.
     *  Calculated by [OptionsPopup] constructor from stage size.
     */
    val selectBoxMinWidth: Float

    /**
     *  Adds a [Label] and [CheckBox] as two Cells into a table row or cell (without using a reference).
     *
     *  It's your responsibility to provide the inital value and to write it back in [action].
     *  There's an overload using a reference instead.
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
            action(it)
            settingsProperty.set(it)
        }
    }

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row.
     *
     *  **Entries will only be translated when the item class provides a toString() doing so.**
     *
     *  @param action Optional callback, before the value is set, argument is the new value - you have a chance to compare, but not to modify
     *  @see addTranslatedSelectBox
     *  @see addEnumAsStringSelectBox
     */
    fun <T> Table.addSelectBox(text: String, property: KMutableProperty0<T>, items: Iterable<T>, action: (T) -> Unit = {}) {
        add(text.toLabel()).left().fillX()

        val select = SelectBox<T>(BaseScreen.skin)
        select.setItems(items.toGdxArray())
        select.selected = property.get()
        add(select).pad(10f).row()

        select.onChange {
            val value = select.selected
            action(value)
            property.set(value)
        }
    }

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row.
     *
     *  **Entries will be translated for you.**
     *
     *  @param action Optional callback, before the value is set, argument is the new value - you have a chance to compare, but not to modify
     *  @see addSelectBox
     *  @see addEnumAsStringSelectBox
     */
    fun Table.addTranslatedSelectBox(text: String, property: KMutableProperty0<String>, items: Iterable<String>, action: (String) -> Unit = {}) {
        add(text.toLabel()).left().fillX()
        val select = TranslatedSelectBox(items as? Collection ?: items.toList(), property.get())
        add(select).pad(10f).minWidth(selectBoxMinWidth).row()

        select.onChange {
            val value = select.selected.value
            action(value)
            property.set(value)
        }
    }

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row.
     *
     *  **This is for the case an enum is actually stored as string in GameSettings.**
     *  **Entries will be translated for you.**
     *
     *  @param action Optional callback, before the value is set, argument is the new value - you have a chance to compare, but not to modify
     *  @see addSelectBox
     *  @see addTranslatedSelectBox
     */
    fun <T : Enum<T>> Table.addEnumAsStringSelectBox(text: String, property: KMutableProperty0<String>, items: Iterable<Enum<T>>, action: (String) -> Unit = {}) =
        addTranslatedSelectBox(text, property, items.map { it.name }, action)

    /**
     *  Adds a [Label] and [SelectBox] as two Cells into a table row, filling the items **asynchronously**.
     *
     *  **Entries will only be translated when the item class provides a toString() doing so.**
     *
     *  @see addTranslatedSelectBox
     */
    fun <T> Table.addAsyncSelectBox(
        text: String,
        property: KMutableProperty0<T>,
        provider: suspend () -> Flow<T>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        action: (T) -> Unit = {}
    ) {
        add(text.toLabel()).left().fillX()
        val select = SelectBox<T>(BaseScreen.skin)
        add(select).pad(10f).row()
        select.isDisabled = true

        Concurrency.run("Deferred SelectBox", CoroutineScope(dispatcher)) {
            val items = Array<T>(false, 20)
            provider().collect { items.add(it) }
            launchOnGLThread {
                select.setItems(items)
                val toSelect = property.get()
                // make sure the instance we pass to select is actually contained in the items array, because Gdx' Selection.set uses identity comparisons
                select.selected = items.firstOrNull { it == toSelect } // will default to first entry if `null` is passed
                select.isDisabled = false
                select.onChange {
                    val value = select.selected
                    action(value)
                    property.set(value)
                }
            }
        }
    }

    /** Reload the [OptionsPopup] after major changes (resolution, tileset, language, font) */
    fun reloadWorldAndOptions(activePage: Int) {
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

    /** Call if an option change might trigger a Screen.resize
     *
     *  Does nothing if any Popup (which can only be this one) is still open after a short delay and context yield.
     *  Reason: A resize might relaunch the parent screen ([MainMenuScreen] is [RecreateOnResize]) and thus close this Popup.
     */
    fun reopenAfterDisplayLayoutChange(activePage: Int) {
        Concurrency.run("Reload from options") {
            delay(100)
            withGLContext {
                val screen = UncivGame.Current.screen ?: return@withGLContext
                if (screen.hasOpenPopups()) return@withGLContext // e.g. Orientation auto to fixed while auto is already the new orientation
                screen.openOptionsPopup(activePage)
            }
        }
    }
}

package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.withGLContext
import kotlinx.coroutines.delay
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

    fun Table.addCheckbox(text: String, initialState: Boolean, updateWorld: Boolean = false, newRow: Boolean = true, action: ((Boolean) -> Unit)) {
        val checkbox = text.toCheckBox(initialState) {
            action(it)
            val worldScreen = GUI.getWorldScreenIfActive()
            if (updateWorld && worldScreen != null) worldScreen.shouldUpdate = true
        }
        if (newRow) add(checkbox).colspan(2).left().row()
        else add(checkbox).left()
    }

    fun Table.addCheckbox(text: String, settingsProperty: KMutableProperty0<Boolean>, updateWorld: Boolean = false, action: (Boolean) -> Unit = {}) {
        addCheckbox(text, settingsProperty.get(), updateWorld) {
            action(it)
            settingsProperty.set(it)
        }
    }

    /** Reload this Popup after major changes (resolution, tileset, language, font) */
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

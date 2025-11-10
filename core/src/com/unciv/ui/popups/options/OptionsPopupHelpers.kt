package com.unciv.ui.popups.options

import com.unciv.UncivGame
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.withGLContext
import kotlinx.coroutines.delay

/**
 *  Helper library for OptionsPopup and its tabs.
 *
 *  Interface used as namespace and to share variables, included in [OptionsPopupTab].
 *
 *  Usage without `OptionsPopupTab`:
 *  ```
 *  internal class NewOptionsPage(
 *     optionsPopup: OptionsPopup
 * ) : Table(BaseScreen.skin), OptionsPopupHelpers {
 *     override val selectBoxMinWidth by optionsPopup::selectBoxMinWidth
 *     ...
 *  ```
 *
 *  TODO
 *    * Move and simplify Checkboxes
 *    * Simplify headers in DisplayTab
 *    * Sliders: 5 occurrences (Advanced, Automation, Debug, Display, Gameplay)
 *    * TextFields: 4 occurrences (Advanced, Debug, ModCheck, Multiplayer)
 *    * SelectBoxes
 *    * SettingsSelect and subclass
 *    * Performance - what is taking so long? Use tabs doing their heavy lifting only on activation, like ModCheckTab?
 */
internal interface OptionsPopupHelpers {
    /** Suggested minimum width for a right-hand cell in a typical 2-column label/widget row.
     *  Calculated by [OptionsPopup] constructor from stage size.
     */
    val selectBoxMinWidth: Float

    /** Access the active page number of the TabbedPager in [OptionsPopup] */
    val activePage: Int

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

    /** Call if an option change might trigger a Screen.resize
     *
     *  Does nothing if any Popup (which can only be this one) is still open after a short delay and context yield.
     *  Reason: A resize might relaunch the parent screen ([MainMenuScreen] is [RecreateOnResize]) and thus close this Popup.
     */
    fun reopenAfterDisplayLayoutChange() {
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

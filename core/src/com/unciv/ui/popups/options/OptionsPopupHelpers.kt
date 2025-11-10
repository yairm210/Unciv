package com.unciv.ui.popups.options

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
 *    * Move reload helpers
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

    // Actual meat to come later
}

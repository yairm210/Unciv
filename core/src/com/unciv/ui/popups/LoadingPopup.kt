package com.unciv.ui.popups

import com.unciv.Constants
import com.unciv.ui.screens.LoadingScreen
import com.unciv.ui.screens.basescreen.BaseScreen


/**
 *  Mini popup just displays "Loading..." and opens itself.
 *
 *  Not to be confused with [LoadingScreen], which tries to preserve background as screenshot.
 *  That screen will use this once the screenshot is on-screen, though.
 */
class LoadingPopup(screen: BaseScreen) : Popup(screen, Scrollability.None) {
    init {
        addGoodSizedLabel(Constants.loading)
        open(true)
    }
}

package com.unciv.app.web

import com.unciv.models.metadata.GameSettings
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode

class WebDisplay : PlatformDisplay {
    override fun setScreenMode(id: Int, settings: GameSettings) {
        // No platform-specific screen modes on web phase-1.
    }

    override fun getScreenModes(): Map<Int, ScreenMode> = emptyMap()

    override fun hasUserSelectableSize(id: Int): Boolean = false
}

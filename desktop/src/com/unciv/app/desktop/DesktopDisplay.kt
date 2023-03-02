package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode


class DesktopScreenMode(
    val modeId: Int,
    val isWindowed: Boolean) : ScreenMode {

    override fun getId(): Int {
        return modeId
    }

    override fun toString(): String {
        return when {
            isWindowed -> "Windowed".tr()
            else -> "Fullscreen".tr()
        }
    }
}

class DesktopDisplay : PlatformDisplay {

    private val windowedMode = DesktopScreenMode(0,true)
    private val fullscreenMode = DesktopScreenMode(1,false)

    override fun getDefaultMode(): ScreenMode {
        return windowedMode
    }

    override fun getScreenModes(): ArrayList<ScreenMode> {
        return arrayListOf(windowedMode, fullscreenMode)
    }

    override fun setScreenMode(mode: ScreenMode, settings: GameSettings) {

        if (mode !is DesktopScreenMode)
            return

        when {

            mode.isWindowed -> {
                Gdx.graphics.setWindowedMode(
                    settings.windowState.width.coerceAtLeast(120),
                    settings.windowState.height.coerceAtLeast(80)
                )
                Gdx.graphics.setUndecorated(false)
            }

            else -> {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
            }

        }
    }
}

package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode

enum class ScreenWindowType {
    Windowed,
    Borderless,
    Fullscreen
}


class DesktopScreenMode(
    private val modeId: Int,
    val windowType: ScreenWindowType) : ScreenMode {

    override fun getId(): Int {
        return modeId
    }

    override fun toString(): String {
        return when (windowType) {
            ScreenWindowType.Windowed -> "Windowed".tr()
            ScreenWindowType.Borderless -> "Borderless".tr()
            ScreenWindowType.Fullscreen -> "Fullscreen".tr()
        }
    }
}

class DesktopDisplay : PlatformDisplay {

    private val modes = HashMap<Int, DesktopScreenMode>()

    init {
        modes[0] = DesktopScreenMode(0, ScreenWindowType.Windowed)
        modes[1] = DesktopScreenMode(1, ScreenWindowType.Fullscreen)
    }

    override fun getDefaultMode(): ScreenMode {
        return modes[0]!!
    }

    override fun getScreenModes(): Map<Int, ScreenMode> {
        return modes
    }

    override fun setScreenMode(id: Int, settings: GameSettings) {

        val mode = modes[id] ?: return

        when (mode.windowType) {

            ScreenWindowType.Fullscreen -> {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
            }

            ScreenWindowType.Windowed -> {
                Gdx.graphics.setUndecorated(false)
                Gdx.graphics.setWindowedMode(
                    settings.windowState.width.coerceAtLeast(120),
                    settings.windowState.height.coerceAtLeast(80)
                )
            }

            ScreenWindowType.Borderless -> {
                Gdx.graphics.setUndecorated(true)
                Gdx.graphics.setWindowedMode(
                    settings.windowState.width.coerceAtLeast(120),
                    settings.windowState.height.coerceAtLeast(80)
                )
            }

        }
    }
}

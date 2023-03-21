package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode


enum class DesktopScreenMode : ScreenMode {
    // Order will be preserved in Options Popup
    Windowed {
        override fun activate(settings: GameSettings) {
            Gdx.graphics.setUndecorated(false)
            setWindowedMode(settings)
        }

        override fun hasUserSelectableSize() = true
    },
    Fullscreen {
        override fun activate(settings: GameSettings) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
        }
        override fun hasUserSelectableSize() = false
    },
    Borderless {
        override fun activate(settings: GameSettings) {
            Gdx.graphics.setUndecorated(true)
            setWindowedMode(settings)
        }
        override fun hasUserSelectableSize() = true
    },
    ;

    override fun getId() = ordinal

    override fun toString() = name.tr()

    abstract fun activate(settings: GameSettings)

    protected fun setWindowedMode(settings: GameSettings) {
        val width = settings.windowState.width.coerceAtLeast(120)
        val height = settings.windowState.height.coerceAtLeast(80)
        // Kludge - see also DesktopLauncher - without, moving the window might revert to the size stored in config
        (Lwjgl3Application::class.java).getDeclaredField("config").run {
            isAccessible = true
            get(Gdx.app) as Lwjgl3ApplicationConfiguration
        }.setWindowedMode(width, height)
        Gdx.graphics.setWindowedMode(width, height)
    }

    companion object {
        operator fun get(id: Int) = values()[id]
    }
}

class DesktopDisplay : PlatformDisplay {

    override fun getScreenModes() =
        DesktopScreenMode.values().associateBy { it.getId() }

    override fun setScreenMode(id: Int, settings: GameSettings) {
        DesktopScreenMode[id].activate(settings)
    }

    override fun hasUserSelectableSize(id: Int) =
        DesktopScreenMode[id].hasUserSelectableSize()
}

package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics.Monitor
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.utils.PlatformDisplay
import com.unciv.utils.ScreenMode
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import kotlin.math.roundToInt


enum class DesktopScreenMode : ScreenMode {
    // Order will be preserved in Options Popup
    Windowed {
        override fun activate(settings: GameSettings) {
            Gdx.graphics.setUndecorated(false)
            val isFillingDesktop = setWindowedMode(settings)
            if (isFillingDesktop)
                getWindow()?.maximizeWindow()
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
            getWindow()?.restoreWindow()
            Gdx.graphics.setUndecorated(true)
            setWindowedMode(settings)
        }
        override fun hasUserSelectableSize() = true
    },
    ;

    override fun getId() = ordinal

    override fun toString() = name.tr()

    abstract fun activate(settings: GameSettings)

    /** @return `true` if window fills entire desktop */
    protected fun setWindowedMode(settings: GameSettings): Boolean {
        // Calling AWT after Gdx is fully initialized seems icky, but seems to have no side effects
        // Found no equivalent in Gdx - available _desktop_ surface without taskbars etc
        // *for the primary monitor* - no saving window sizes that span over several monitors
        val maximumWindowBounds = getMaximumWindowBounds()

        // Make sure an inappropriate saved size doesn't make the window unusable
        val (width, height) = settings.windowState.coerceIn(maximumWindowBounds)

        // Kludge - see also DesktopLauncher - without, moving the window might revert to the size stored in config
        (Lwjgl3Application::class.java).getDeclaredField("config").run {
            isAccessible = true
            get(Gdx.app) as Lwjgl3ApplicationConfiguration
        }.setWindowedMode(width, height)

        Gdx.graphics.setWindowedMode(width, height)

        val widthDiff = maximumWindowBounds.width - width
        val heightDiff = maximumWindowBounds.height - height
        val tolerance = 30
        return widthDiff <= tolerance && heightDiff <= tolerance
    }

    companion object {
        operator fun get(id: Int) = values()[id]

        private fun getWindow() = (Gdx.graphics as? Lwjgl3Graphics)?.window

        /** Replacement for buggy `GraphicsEnvironment.maximumWindowBounds` */
        // Notes: maximumWindowBounds seems to scale by the High DPI setting on Windows,
        // and it always uses the default device. GraphicsConfiguration.getBounds() delivers x/y
        // as true pixels, while width/height are similarly scaled. Toolkit.getScreenInsets
        // delivers scaled values (observed - no documentation found).
        internal fun getMaximumWindowBounds(
            monitor: Monitor = Lwjgl3ApplicationConfiguration.getPrimaryMonitor()
        ): java.awt.Rectangle {
            // Identify AWT equivalent to Gdx monitor
            val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
            for (device in graphicsEnvironment.screenDevices) {
                for (config in device.configurations) {
                    val bounds = config.bounds
                    if (bounds.x == monitor.virtualX && bounds.y == monitor.virtualY)
                        return getMaximumWindowBounds(device, config, bounds)
                }
            }
            // Fallback should that fail (this is without insets)
            val mode = Lwjgl3ApplicationConfiguration.getDisplayMode(monitor)
            return java.awt.Rectangle(monitor.virtualX, monitor.virtualY, mode.width, mode.height)
        }

        private fun getMaximumWindowBounds(
            device: GraphicsDevice,
            config: GraphicsConfiguration,
            bounds: java.awt.Rectangle
        ): java.awt.Rectangle {
            val displayWidth = device.displayMode.width
            val displayHeight = device.displayMode.height
            val scalePercent = (displayWidth.toDouble() / bounds.width * 100).roundToInt() * 0.01
            val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
            val unscaledInsetLeft = (insets.left * scalePercent).roundToInt()
            val unscaledInsetRight = (insets.right * scalePercent).roundToInt()
            val unscaledInsetTop = (insets.top * scalePercent).roundToInt()
            val unscaledInsetBottom = (insets.bottom * scalePercent).roundToInt()
            return java.awt.Rectangle(
                bounds.x + unscaledInsetLeft,
                bounds.y + unscaledInsetTop,
                displayWidth - unscaledInsetLeft - unscaledInsetRight,
                displayHeight - unscaledInsetTop - unscaledInsetBottom
            )
        }
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

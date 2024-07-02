package com.unciv.dev

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.unciv.UncivGame
import com.unciv.dev.FasterUIDevelopment.DevElement
import com.unciv.json.json
import com.unciv.logic.files.UncivFiles
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.FontImplementation
import com.unciv.ui.components.fonts.FontMetricsCommon
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.ImageWithCustomSize
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.UncivStage
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/** Creates a basic GDX application that mimics [UncivGame] as closely as possible,
 *  starts up fast and shows one UI element, to be returned by [DevElement.createDevElement].
 *
 *  - The parent will not size your Widget as the Gdx [Layout] contract promises,
 *    you'll need to do it yourself. E.g, if you're a [WidgetGroup], call [pack()][WidgetGroup.pack].
 *    If you forget, you'll see an orange dot in the window center.
 *  - Resizing the window is not supported. You might lose interactivity.
 *  - However, settings including window size are saved separately from main Unciv, so you **can** test with different sizes.
 *  - Language is default English and there's no UI to change it - edit the settings file by hand, set once in the debugger, or hardcode in createDevElement if needed.
 *  - The middle mouse button toggles Scene2D debug mode, like the full game offers in the Debug Options.
 */
object FasterUIDevelopment {

    class DevElement(
        @Suppress("unused") val screen: UIDevScreen
    ) {
        lateinit var actor: Actor

        fun createDevElement() {
            actor = "This could be your UI element in development!".toLabel()
        }

        @Suppress("EmptyFunctionBlock")
        fun afterAdd() {
        }
    }

    @JvmStatic
    fun main(arg: Array<String>) {
        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true")
        System.setProperty("org.lwjgl.system.stackSize", "384")

        val config = Lwjgl3ApplicationConfiguration()

        val settings = Settings.load()
        if (!settings.isFreshlyCreated) {
            val (width, height) = settings.windowState.coerceIn()
            config.setWindowedMode(width, height)
        }

        Lwjgl3Application(UIDevGame(), config)
    }

    class UIDevGame : Game() {

        private val game = UncivGame()

        override fun create() {
            Fonts.fontImplementation = FontDesktop()
            UncivGame.Current = game
            UncivGame.Current.files = UncivFiles(Gdx.files)
            game.settings = Settings.load()
            ImageGetter.resetAtlases()
            ImageGetter.reloadImages()
            BaseScreen.setSkin()
            game.pushScreen(UIDevScreen())
            Gdx.graphics.requestRendering()
        }

        override fun render() {
            game.render()
        }

        override fun pause() {
            Settings.save(UncivGame.Current.settings)
            super.pause()
        }
    }

    /** Persist window size over invocations, but separately from main Unciv */
    private object Settings {
        const val SETTINGS_FILE_NAME = "FasterUIDevSettings.json"
        val file: FileHandle = FileHandle(".").child(SETTINGS_FILE_NAME)
        fun load(): GameSettings {
            if (!file.exists()) return GameSettings().apply { isFreshlyCreated = true }
            return json().fromJson(GameSettings::class.java, file)
        }
        fun save(settings: GameSettings) {
            settings.isFreshlyCreated = false
            // settings.refreshWindowSize() - No, we don't have the platform-dependent helpers initialized
            settings.windowState = GameSettings.WindowState.current()
            file.writeString(json().toJson(settings), false, Charsets.UTF_8.name())
        }
    }

    class UIDevScreen : BaseScreen() {
        private val devElement = DevElement(this)

        init {
            devElement.createDevElement()
            val actor = devElement.actor
            actor.center(stage)
            addBorder(actor, Color.ORANGE)
            stage.addActor(actor)
            devElement.afterAdd()
            stage.addListener(ToggleDebugListener(stage as UncivStage))
        }

        private fun addBorder(actor: Actor, color: Color) {
            val stageCoords = actor.localToStageCoordinates(Vector2())

            // Z-Order works because we're called _before_ the DevElement is added
            val border = ImageWithCustomSize(skinStrings.getUiBackground("", tintColor = color))
            border.x = stageCoords.x - 1
            border.y = stageCoords.y - 1
            border.width = actor.width + 2
            border.height = actor.height + 2
            stage.addActor(border)

            val background = ImageWithCustomSize(skinStrings.getUiBackground("", tintColor = clearColor))
            background.x = stageCoords.x
            background.y = stageCoords.y
            background.width = actor.width
            background.height = actor.height
            stage.addActor(background)
        }

        private class ToggleDebugListener(private val stage: UncivStage) : ClickListener(2) {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                enableSceneDebug = !enableSceneDebug
                stage.setDebugUnderMouse(enableSceneDebug)
                stage.setDebugTableUnderMouse(enableSceneDebug)
                stage.setDebugParentUnderMouse(enableSceneDebug)
                stage.mouseOverDebug = enableSceneDebug
            }
        }
    }
}


class FontDesktop : FontImplementation {
    private val font by lazy {
        Font(Fonts.DEFAULT_FONT_FAMILY, Font.PLAIN, Fonts.ORIGINAL_FONT_SIZE.toInt())
    }
    private val metric by lazy {
        val bi = BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR)
        val g = bi.createGraphics()
        g.font = font
        val fontMetrics = g.fontMetrics
        g.dispose()
        fontMetrics
    }

    override fun setFontFamily(fontFamilyData: FontFamilyData, size: Int) {
        // Empty
    }

    override fun getFontSize() = Fonts.ORIGINAL_FONT_SIZE.toInt()

    override fun getCharPixmap(symbolString: String): Pixmap {
        var width = metric.stringWidth(symbolString)
        var height = metric.height
        if (width == 0) {
            height = Fonts.ORIGINAL_FONT_SIZE.toInt()
            width = height
        }

        val bi = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
        val g = bi.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = font
        g.color = java.awt.Color.WHITE
        g.drawString(symbolString, 0, metric.leading + metric.ascent)

        val pixmap = Pixmap(bi.width, bi.height, Pixmap.Format.RGBA8888)
        val data = bi.getRGB(0, 0, bi.width, bi.height, null, 0, bi.width)
        for (i in 0 until bi.width) {
            for (j in 0 until bi.height) {
                pixmap.setColor(Integer.reverseBytes(data[i + (j * bi.width)]))
                pixmap.drawPixel(i, j)
            }
        }
        g.dispose()
        return pixmap
    }

    override fun getSystemFonts() = sequenceOf(FontFamilyData(Fonts.DEFAULT_FONT_FAMILY))

    override fun getMetrics() = FontMetricsCommon(
        ascent = metric.ascent.toFloat(),
        descent = metric.descent.toFloat(),
        height = metric.height.toFloat(),
        leading = metric.leading.toFloat()
    )
}

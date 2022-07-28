package com.unciv.dev

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.logic.UncivFiles
import com.unciv.logic.multiplayer.throttle
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.ImageWithCustomSize
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.FontFamilyData
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.NativeFontImplementation
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.utils.concurrency.Concurrency
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Creates a basic GDX application that mimics [UncivGame] as closely as possible, starts up fast and shows one UI element, to be returned by [DevElement.createDevElement] */
object FasterUIDevelopment {

    class DevElement(
        val screen: UIDevScreen
    ) {
        lateinit var actor: Actor
        fun createDevElement() {
            actor = "This could be your UI element in development!".toLabel()
        }

        fun afterAdd() {
        }
    }

    @JvmStatic
    fun main(arg: Array<String>) {
        System.setProperty("org.lwjgl.opengl.Display.allowSoftwareOpenGL", "true")
        System.setProperty("org.lwjgl.system.stackSize", "384")

        val config = Lwjgl3ApplicationConfiguration()

        val settings = UncivFiles.getSettingsForPlatformLaunchers()
        if (!settings.isFreshlyCreated) {
            config.setWindowedMode(settings.windowState.width.coerceAtLeast(120), settings.windowState.height.coerceAtLeast(80))
        }

        Lwjgl3Application(UIDevGame(), config)
    }

    class UIDevGame : Game() {
        val game = UncivGame(UncivGameParameters(
            fontImplementation = NativeFontDesktop()
        ))
        override fun create() {
            UncivGame.Current = game
            UncivGame.Current.files = UncivFiles(Gdx.files)
            game.settings = UncivGame.Current.files.getGeneralSettings()
            ImageGetter.resetAtlases()
            ImageGetter.setNewRuleset(ImageGetter.ruleset)
            BaseScreen.setSkin()
            game.pushScreen(UIDevScreen())
            Gdx.graphics.requestRendering()
        }

        override fun render() {
            game.render()
        }

    }

    class UIDevScreen : BaseScreen() {
        val devElement = DevElement(this)
        init {
            devElement.createDevElement()
            val actor = devElement.actor
            actor.center(stage)
            addBorder(actor, Color.ORANGE)
            actor.zIndex = Int.MAX_VALUE
            stage.addActor(actor)
            devElement.afterAdd()
            stage.addListener(object : InputListener() {
                val lastPrint = AtomicReference<Instant?>()
                override fun mouseMoved(event: InputEvent?, x: Float, y: Float): Boolean {
                    Concurrency.run {
                        throttle(lastPrint, Duration.ofMillis(500), {}) {
                            println(String.format("x: %.1f\ty: %.1f", x, y))
                        }
                    }
                    return false
                }
            })
        }
        private var curBorderZ = 0
        fun addBorder(actor: Actor, color: Color) {
            val border = ImageWithCustomSize(ImageGetter.getBackground(color))
            border.zIndex = curBorderZ++
            val stageCoords = actor.localToStageCoordinates(Vector2(0f, 0f))
            border.x = stageCoords.x - 1
            border.y = stageCoords.y - 1
            border.width = actor.width + 2
            border.height = actor.height + 2
            stage.addActor(border)

            val background = ImageWithCustomSize(ImageGetter.getBackground(clearColor))
            background.zIndex = curBorderZ++
            background.x = stageCoords.x
            background.y = stageCoords.y
            background.width = actor.width
            background.height = actor.height
            stage.addActor(background)
        }
    }
}


class NativeFontDesktop : NativeFontImplementation {
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

    override fun getFontSize(): Int {
        return Fonts.ORIGINAL_FONT_SIZE.toInt()
    }

    override fun getCharPixmap(char: Char): Pixmap {
        var width = metric.charWidth(char)
        var height = metric.ascent + metric.descent
        if (width == 0) {
            height = Fonts.ORIGINAL_FONT_SIZE.toInt()
            width = height
        }
        val bi = BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR)
        val g = bi.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = font
        g.color = java.awt.Color.WHITE
        g.drawString(char.toString(), 0, metric.ascent)
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

    override fun getAvailableFontFamilies(): Sequence<FontFamilyData> {
        return sequenceOf(FontFamilyData(Fonts.DEFAULT_FONT_FAMILY))
    }
}

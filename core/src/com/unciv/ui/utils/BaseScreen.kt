package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.unciv.ui.crashhandling.CrashHandlingStage
import com.unciv.UncivGame
import com.unciv.models.Tutorial
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.hasOpenPopups
import com.unciv.ui.tutorials.TutorialController
import com.unciv.ui.options.OptionsPopup

abstract class BaseScreen : Screen {

    val game: UncivGame = UncivGame.Current
    val stage: Stage

    protected val tutorialController by lazy { TutorialController(this) }

    val keyPressDispatcher = KeyPressDispatcher(this.javaClass.simpleName)

    init {
        val resolutions: List<Float> = game.settings.resolution.split("x").map { it.toInt().toFloat() }
        val height = resolutions[1]

        /** The ExtendViewport sets the _minimum_(!) world size - the actual world size will be larger, fitted to screen/window aspect ratio. */
        stage = CrashHandlingStage(ExtendViewport(height, height), SpriteBatch())

        if (enableSceneDebug) {
            stage.setDebugUnderMouse(true)
            stage.setDebugTableUnderMouse(true)
            stage.setDebugParentUnderMouse(true)
        }

        keyPressDispatcher.install(stage) { hasOpenPopups() }
    }

    override fun show() {}

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    override fun dispose() {
        keyPressDispatcher.uninstall()
    }

    fun displayTutorial(tutorial: Tutorial, test: (() -> Boolean)? = null) {
        if (!game.settings.showTutorials) return
        if (game.settings.tutorialsShown.contains(tutorial.name)) return
        if (test != null && !test()) return
        tutorialController.showTutorial(tutorial)
    }

    companion object {
        var enableSceneDebug = false

        lateinit var skin: Skin
        fun setSkin() {
            Fonts.resetFont()
            skin = Skin().apply {
                add("Nativefont", Fonts.font, BitmapFont::class.java)
                add("RoundedEdgeRectangle", ImageGetter.getRoundedEdgeRectangle(), Drawable::class.java)
                add("Rectangle", ImageGetter.getDrawable(""), Drawable::class.java)
                add("Circle", ImageGetter.getDrawable("OtherIcons/Circle").apply { setMinSize(20f, 20f) }, Drawable::class.java)
                add("Scrollbar", ImageGetter.getDrawable("").apply { setMinSize(10f, 10f) }, Drawable::class.java)
                add("RectangleWithOutline", ImageGetter.getRectangleWithOutline(), Drawable::class.java)
                add("Select-box", ImageGetter.getSelectBox(), Drawable::class.java)
                add("Select-box-pressed", ImageGetter.getSelectBoxPressed(), Drawable::class.java)
                add("Checkbox", ImageGetter.getCheckBox(), Drawable::class.java)
                add("Checkbox-pressed", ImageGetter.getCheckBoxPressed(), Drawable::class.java)
                load(Gdx.files.internal("Skin.json"))
            }
            skin.get(TextButton.TextButtonStyle::class.java).font = Fonts.font
            skin.get(CheckBox.CheckBoxStyle::class.java).apply {
                font = Fonts.font
                fontColor = Color.WHITE
            }
            skin.get(Label.LabelStyle::class.java).apply {
                font = Fonts.font
                fontColor = Color.WHITE
            }
            skin.get(TextField.TextFieldStyle::class.java).font = Fonts.font
            skin.get(SelectBox.SelectBoxStyle::class.java).apply {
                font = Fonts.font
                listStyle.font = Fonts.font
            }
        }
        /** Colour to use for empty sections of the screen. */
        val clearColor = Color(0f, 0f, 0.2f, 1f)
    }

    fun onBackButtonClicked(action: () -> Unit) {
        keyPressDispatcher[KeyCharAndCode.BACK] = action
    }

    /** @return `true` if the screen is higher than it is wide */
    fun isPortrait() = stage.viewport.screenHeight > stage.viewport.screenWidth
    /** @return `true` if the screen is higher than it is wide _and_ resolution is at most 1050x700 */
    fun isCrampedPortrait() = isPortrait() &&
            game.settings.resolution.split("x").map { it.toInt() }.last() <= 700
    /** @return `true` if the screen is narrower than 4:3 landscape */
    fun isNarrowerThan4to3() = stage.viewport.screenHeight * 4 > stage.viewport.screenWidth * 3

    fun openOptionsPopup(startingPage: Int = OptionsPopup.defaultPage, onClose: () -> Unit = {}) {
        OptionsPopup(this, startingPage, onClose).open(force = true)
    }
}

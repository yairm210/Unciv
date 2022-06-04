package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.unciv.UncivGame
import com.unciv.models.TutorialTrigger
import com.unciv.ui.UncivStage
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.activePopup
import com.unciv.ui.tutorials.TutorialController
import com.unciv.ui.options.OptionsPopup
import com.unciv.ui.utils.extensions.installShortcutDispatcher
import com.unciv.ui.utils.extensions.isNarrowerThan4to3
import com.unciv.ui.utils.extensions.DispatcherVetoResult
import com.unciv.ui.utils.extensions.DispatcherVetoer

abstract class BaseScreen : Screen {

    val game: UncivGame = UncivGame.Current
    val stage: Stage

    protected val tutorialController by lazy { TutorialController(this) }

    /**
     * Keyboard shorcuts global to the screen. While this is public and can be modified,
     * you most likely should use [keyShortcuts][Actor.keyShortcuts] on appropriate [Actor] instead.
     */
    val globalShortcuts = KeyShortcutDispatcher()

    init {
        val resolutions: List<Float> = game.settings.resolution.split("x").map { it.toInt().toFloat() }
        val height = resolutions[1]

        /** The ExtendViewport sets the _minimum_(!) world size - the actual world size will be larger, fitted to screen/window aspect ratio. */
        stage = UncivStage(ExtendViewport(height, height))

        if (enableSceneDebug) {
            stage.setDebugUnderMouse(true)
            stage.setDebugTableUnderMouse(true)
            stage.setDebugParentUnderMouse(true)
        }

        stage.installShortcutDispatcher(globalShortcuts, this::createPopupBasedDispatcherVetoer)
    }

    private fun createPopupBasedDispatcherVetoer(): DispatcherVetoer? {
        val activePopup = this.activePopup
        if (activePopup == null)
            return null
        else {
            // When any popup is active, disable all shortcuts on actor outside the popup
            // and also the global shortcuts on the screen itself.
            return { associatedActor: Actor?, _: KeyShortcutDispatcher? ->
                when { associatedActor == null -> DispatcherVetoResult.Skip
                       associatedActor.isDescendantOf(activePopup) -> DispatcherVetoResult.Accept
                       else -> DispatcherVetoResult.SkipWithChildren }
            }
        }
    }

    override fun show() {}

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        stage.act()
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        if (this !is RecreateOnResize) {
            stage.viewport.update(width, height, true)
        } else if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.replaceCurrentScreen(recreate())
        }
    }

    override fun pause() {}

    override fun resume() {}

    override fun hide() {}

    override fun dispose() {
        stage.dispose()
    }

    fun displayTutorial(tutorial: TutorialTrigger, test: (() -> Boolean)? = null) {
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

    /** @return `true` if the screen is higher than it is wide */
    fun isPortrait() = stage.viewport.screenHeight > stage.viewport.screenWidth
    /** @return `true` if the screen is higher than it is wide _and_ resolution is at most 1050x700 */
    fun isCrampedPortrait() = isPortrait() &&
            game.settings.resolution.split("x").map { it.toInt() }.last() <= 700
    /** @return `true` if the screen is narrower than 4:3 landscape */
    fun isNarrowerThan4to3() = stage.isNarrowerThan4to3()

    fun openOptionsPopup(startingPage: Int = OptionsPopup.defaultPage, onClose: () -> Unit = {}) {
        OptionsPopup(this, startingPage, onClose).open(force = true)
    }
}

interface RecreateOnResize {
    fun recreate(): BaseScreen
}

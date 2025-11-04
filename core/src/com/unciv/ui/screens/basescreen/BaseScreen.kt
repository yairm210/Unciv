package com.unciv.ui.screens.basescreen

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
import com.unciv.GameStartScreen
import com.unciv.UncivGame
import com.unciv.models.TutorialTrigger
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.skins.SkinStrings
import com.unciv.ui.components.extensions.isNarrowerThan4to3
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.DispatcherVetoer
import com.unciv.ui.components.input.KeyShortcutDispatcher
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto
import com.unciv.ui.components.input.installShortcutDispatcher
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.crashhandling.CrashScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.activePopup
import com.unciv.ui.popups.options.OptionsPopup
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

// Both `this is CrashScreen` and `this::createPopupBasedDispatcherVetoer` are flagged.
// First - not a leak; second - passes out a pure function
@Suppress("LeakingThis")

abstract class BaseScreen : Screen {

    val game: UncivGame = UncivGame.Current
    val stage: Stage

    protected val tutorialController by lazy { TutorialController(this) }

    /**
     * Keyboard shortcuts global to the screen. While this is public and can be modified,
     * you most likely should use [keyShortcuts] on the appropriate [Actor] instead.
     */
    val globalShortcuts = KeyShortcutDispatcher()

    init {
        val screenSize = game.settings.screenSize
        val height = screenSize.virtualHeight

        /** The ExtendViewport sets the _minimum_(!) world size - the actual world size will be larger, fitted to screen/window aspect ratio. */
        stage = UncivStage(ExtendViewport(height, height))

        if (enableSceneDebug.active && this !is CrashScreen && this !is GameStartScreen)
            stage.setSceneDebugMode()

        @Suppress("LeakingThis")
        stage.installShortcutDispatcher(globalShortcuts, this::createDispatcherVetoer)
    }

    /** Hook allowing derived Screens to supply a key shortcut vetoer that can exclude parts of the
     *  Stage Actor hierarchy from the search. Only called if no [Popup] is active.
     *  @see installShortcutDispatcher
     */
    open fun getShortcutDispatcherVetoer(): DispatcherVetoer? = null

    private fun createDispatcherVetoer(): DispatcherVetoer? {
        val activePopup = this.activePopup
            ?: return getShortcutDispatcherVetoer()
        return KeyShortcutDispatcherVeto.createPopupBasedDispatcherVetoer(activePopup)
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

    /**
     * Called when this screen should release all resources.
     *
     * This is _not_ called automatically by Gdx, but by the [screenStack][UncivGame.screenStack]
     * functions in [UncivGame], e.g. [replaceCurrentScreen][UncivGame.replaceCurrentScreen].
     */
    override fun dispose() {
        // FYI - This is a method of Gdx [Screen], not of Gdx [Disposable], but the one below _is_.
        stage.dispose()
    }

    fun displayTutorial(tutorial: TutorialTrigger, test: (() -> Boolean)? = null) {
        if (!game.settings.showTutorials) return
        if (game.settings.tutorialsShown.contains(tutorial.name)) return
        if (this is WorldScreen && this.autoPlay.isAutoPlaying()) return
        if (test != null && !test()) return
        tutorialController.showTutorial(tutorial)
    }

    companion object {
        var enableSceneDebug = SceneDebugMode.None

        /** Colour to use for empty sections of the screen.
         *  Gets overwritten by SkinConfig.clearColor after starting Unciv */
        var clearColor = Color(0f, 0f, 0.2f, 1f)

        lateinit var skin: Skin
        lateinit var skinStrings: SkinStrings

        fun setSkin() {
            Fonts.resetFont()
            skinStrings = SkinStrings()
            skin = Skin().apply {
                add("default-clear", clearColor, Color::class.java)
                add("Nativefont", Fonts.font, BitmapFont::class.java)
                add("RoundedEdgeRectangle", skinStrings.getUiBackground("", skinStrings.roundedEdgeRectangleShape), Drawable::class.java)
                add("Rectangle", ImageGetter.getDrawable(""), Drawable::class.java)
                add("Circle", ImageGetter.getCircleDrawable().apply { setMinSize(20f, 20f) }, Drawable::class.java)
                add("Scrollbar", ImageGetter.getDrawable("").apply { setMinSize(10f, 10f) }, Drawable::class.java)
                add("RectangleWithOutline",
                    skinStrings.getUiBackground("", skinStrings.rectangleWithOutlineShape), Drawable::class.java)
                add("Select-box", skinStrings.getUiBackground("", skinStrings.selectBoxShape), Drawable::class.java)
                add("Select-box-pressed", skinStrings.getUiBackground("", skinStrings.selectBoxPressedShape), Drawable::class.java)
                add("Checkbox", skinStrings.getUiBackground("", skinStrings.checkboxShape), Drawable::class.java)
                add("Checkbox-pressed", skinStrings.getUiBackground("", skinStrings.checkboxPressedShape), Drawable::class.java)
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
            clearColor = skinStrings.skinConfig.clearColor
        }
    }

    /** @return `true` if the screen is higher than it is wide */
    fun isPortrait() = stage.viewport.screenHeight > stage.viewport.screenWidth
    /** @return `true` if the screen is higher than it is wide _and_ resolution is at most 1050x700 */
    fun isCrampedPortrait() = isPortrait() &&
            game.settings.screenSize.virtualHeight <= 700
    /** @return `true` if the screen is narrower than 4:3 landscape */
    fun isNarrowerThan4to3() = stage.isNarrowerThan4to3()

    open fun openOptionsPopup(startingPage: Int = OptionsPopup.defaultPage, withDebug: Boolean = false, onClose: () -> Unit = {}) {
        OptionsPopup(this, startingPage, withDebug, onClose).open(force = true)
    }

    /**
     *  Determine a Ruleset for Civilopedia to use (remember: it is supposed to work without a running game loaded)
     *
     *  - `open` as some important screens are supposed to provide directly.
     *  - The default implementation searches using the [screenStack][UncivGame.screenStack] for a source of a Ruleset and returns Civ_V_GnK when that fails.
     *  - Care must be taken in [PickerScreen][com.unciv.ui.screens.pickerscreens.PickerScreen] derivates - they will default to the searching implementation, but often could do the task more efficiently.
     */
    open fun getCivilopediaRuleset(): Ruleset {
        if (game.worldScreen != null) return game.worldScreen!!.gameInfo.ruleset
        val mainMenuScreen = game.getScreensOfType(MainMenuScreen::class).firstOrNull()
        if (mainMenuScreen != null) return mainMenuScreen.getCivilopediaRuleset()
        return RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
    }

    /** Opens Civilopedia
     *
     *  It's an open method of BaseScreen because especially MainMenuScreen has cleanup things to do first.
     *  @see getCivilopediaRuleset
     */
    open fun openCivilopedia(link: String = "") = openCivilopedia(getCivilopediaRuleset(), link)

    /** Helper for the [openCivilopedia] (link: String) overload to use
     *  - Note: At the time of wrinting, this was the ***only*** CivilopediaScreen constructor call outside itself
     */
    fun openCivilopedia(ruleset: Ruleset, link: String = "") = game.pushScreen(CivilopediaScreen(ruleset, link = link))
}

interface RecreateOnResize {
    fun recreate(): BaseScreen
}

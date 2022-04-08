package com.unciv

import com.badlogic.gdx.Application
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.Translations
import com.unciv.ui.LanguagePickerScreen
import com.unciv.ui.audio.MusicController
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.PlayerReadyScreen
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.ui.audio.Sounds
import com.unciv.ui.crashhandling.crashHandlingThread
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import java.util.*

class UncivGame(parameters: UncivGameParameters) : Game() {
    // we need this secondary constructor because Java code for iOS can't handle Kotlin lambda parameters
    constructor(version: String) : this(UncivGameParameters(version, null))

    val version = parameters.version
    val crashReportSysInfo = parameters.crashReportSysInfo
    val cancelDiscordEvent = parameters.cancelDiscordEvent
    val fontImplementation = parameters.fontImplementation
    val consoleMode = parameters.consoleMode
    val customSaveLocationHelper = parameters.customSaveLocationHelper
    val limitOrientationsHelper = parameters.limitOrientationsHelper

    var deepLinkedMultiplayerGame: String? = null
    lateinit var gameInfo: GameInfo
    fun isGameInfoInitialized() = this::gameInfo.isInitialized
    lateinit var settings: GameSettings
    lateinit var musicController: MusicController

    /**
     * This exists so that when debugging we can see the entire map.
     * Remember to turn this to false before commit and upload!
     */
    var viewEntireMapForDebug = false
    /** For when you need to test something in an advanced game and don't have time to faff around */
    var superchargedForDebug = false

    /** Simulate until this turn on the first "Next turn" button press.
     *  Does not update World View changes until finished.
     *  Set to 0 to disable.
     */
    val simulateUntilTurnForDebug: Int = 0

    /** Console log battles
     */
    val alertBattle = false

    lateinit var worldScreen: WorldScreen

    var isInitialized = false

    /** A wrapped render() method that crashes to [CrashScreen] on a unhandled exception or error. */
    private val wrappedCrashHandlingRender = { super.render() }.wrapCrashHandlingUnit()
    // Stored here because I imagine that might be slightly faster than allocating for a new lambda every time, and the render loop is possibly one of the only places where that could have a significant impact.


    val translations = Translations()

    override fun create() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        if (Gdx.app.type != Application.ApplicationType.Desktop) {
            viewEntireMapForDebug = false
        }
        Current = this
        GameSaver.customSaveLocationHelper = customSaveLocationHelper

        // If this takes too long players, especially with older phones, get ANR problems.
        // Whatever needs graphics needs to be done on the main thread,
        // So it's basically a long set of deferred actions.

        /** When we recreate the GL context for whatever reason (say - we moved to a split screen on Android),
         * ALL objects that were related to the old context - need to be recreated.
         * So far we have:
         * - All textures (hence the texture atlas)
         * - SpriteBatch (hence BaseScreen uses a new SpriteBatch for each screen)
         * - Skin (hence BaseScreen.setSkin())
         * - Font (hence Fonts.resetFont() inside setSkin())
         */
        settings = GameSaver.getGeneralSettings() // needed for the screen
        screen = LoadingScreen()  // NOT dependent on any atlas or skin
        musicController = MusicController()  // early, but at this point does only copy volume from settings

        ImageGetter.resetAtlases()
        ImageGetter.setNewRuleset(ImageGetter.ruleset)  // This needs to come after the settings, since we may have default visual mods
        if(settings.tileSet !in ImageGetter.getAvailableTilesets()) { // If one of the tilesets is no longer available, default back
            settings.tileSet = "FantasyHex"
        }

        BaseScreen.setSkin() // needs to come AFTER the Texture reset, since the buttons depend on it

        Gdx.graphics.isContinuousRendering = settings.continuousRendering

        crashHandlingThread(name = "LoadJSON") {
            RulesetCache.loadRulesets(printOutput = true)
            translations.tryReadTranslationForCurrentLanguage()
            translations.loadPercentageCompleteOfLanguages()
            TileSetCache.loadTileSetConfigs(printOutput = true)

            if (settings.userId.isEmpty()) { // assign permanent user id
                settings.userId = UUID.randomUUID().toString()
                settings.save()
            }

            // This stuff needs to run on the main thread because it needs the GL context
            postCrashHandlingRunnable {
                musicController.chooseTrack(suffix = MusicMood.Menu)

                ImageGetter.ruleset = RulesetCache.getVanillaRuleset() // so that we can enter the map editor without having to load a game first

                when {
                    settings.isFreshlyCreated -> setScreen(LanguagePickerScreen())
                    deepLinkedMultiplayerGame == null -> setScreen(MainMenuScreen())
                    else -> tryLoadDeepLinkedGame()
                }

                isInitialized = true
            }
        }
    }

    fun loadGame(gameInfo: GameInfo) {
        this.gameInfo = gameInfo
        ImageGetter.setNewRuleset(gameInfo.ruleSet)
        // Clone the mod list and add the base ruleset to it
        val fullModList = gameInfo.gameParameters.getModsAndBaseRuleset()
        musicController.setModList(fullModList)
        Gdx.input.inputProcessor = null // Since we will set the world screen when we're ready,
        if (gameInfo.civilizations.count { it.playerType == PlayerType.Human } > 1 && !gameInfo.gameParameters.isOnlineMultiplayer)
            setScreen(PlayerReadyScreen(gameInfo, gameInfo.getPlayerToViewAs()))
        else {
            worldScreen = WorldScreen(gameInfo, gameInfo.getPlayerToViewAs())
            setWorldScreen()
        }
    }

    fun setScreen(screen: BaseScreen) {
        Gdx.input.inputProcessor = screen.stage
        super.setScreen(screen)
    }

    fun setWorldScreen() {
        if (screen != null && screen != worldScreen) screen.dispose()
        setScreen(worldScreen)
        worldScreen.shouldUpdate = true // This can set the screen to the policy picker or tech picker screen, so the input processor must come before
        Gdx.graphics.requestRendering()
    }
    
    fun tryLoadDeepLinkedGame() {
        if (deepLinkedMultiplayerGame != null) {
            try {
                loadGame(OnlineMultiplayer().tryDownloadGame(deepLinkedMultiplayerGame!!))
            } catch (ex: Exception) {
                setScreen(MainMenuScreen())
            }
        }
    }

    // This is ALWAYS called after create() on Android - google "Android life cycle"
    override fun resume() {
        super.resume()
        musicController.resume()
        if (!isInitialized) return // The stuff from Create() is still happening, so the main screen will load eventually

        // This is also needed in resume to open links and notifications
        // correctly when the app was already running. The handling in onCreate
        // does not seem to be enough
        tryLoadDeepLinkedGame()
    }

    override fun pause() {
        if (isGameInfoInitialized()) GameSaver.autoSave(this.gameInfo)
        musicController.pause()
        super.pause()
    }

    override fun resize(width: Int, height: Int) {
        screen.resize(width, height)
    }

    override fun render() = wrappedCrashHandlingRender()

    override fun dispose() {
        cancelDiscordEvent?.invoke()
        Sounds.clearCache()
        if (::musicController.isInitialized) musicController.gracefulShutdown()  // Do allow fade-out

        // Log still running threads (on desktop that should be only this one and "DestroyJavaVM")
        val numThreads = Thread.activeCount()
        val threadList = Array(numThreads) { _ -> Thread() }
        Thread.enumerate(threadList)

        if (isGameInfoInitialized()) {
            val autoSaveThread = threadList.firstOrNull { it.name == "Autosave" }
            if (autoSaveThread != null && autoSaveThread.isAlive) {
                // auto save is already in progress (e.g. started by onPause() event)
                // let's allow it to finish and do not try to autosave second time
                autoSaveThread.join()
            } else
                GameSaver.autoSaveSingleThreaded(gameInfo)      // NO new thread
        }
        settings.save()

        threadList.filter { it !== Thread.currentThread() && it.name != "DestroyJavaVM"}.forEach {
            println ("    Thread ${it.name} still running in UncivGame.dispose().")
        }
    }

    companion object {
        lateinit var Current: UncivGame
        fun isCurrentInitialized() = this::Current.isInitialized
    }
}

private class LoadingScreen : BaseScreen() {
    init {
        val happinessImage = ImageGetter.getExternalImage("LoadScreen.png")
        happinessImage.center(stage)
        happinessImage.setOrigin(Align.center)
        happinessImage.addAction(Actions.sequence(
                Actions.delay(1f),
                Actions.rotateBy(360f, 0.5f)))
        stage.addActor(happinessImage)
    }
}

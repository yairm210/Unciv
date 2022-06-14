package com.unciv

import com.badlogic.gdx.Application
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.Translations
import com.unciv.ui.LanguagePickerScreen
import com.unciv.ui.audio.GameSounds
import com.unciv.ui.audio.MusicController
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.crashhandling.CrashScreen
import com.unciv.ui.crashhandling.wrapCrashHandlingUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.multiplayer.LoadDeepLinkScreen
import com.unciv.ui.multiplayer.MultiplayerHelpers
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.worldscreen.PlayerReadyScreen
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import com.unciv.utils.debug
import java.util.*

class UncivGame(parameters: UncivGameParameters) : Game() {
    // we need this secondary constructor because Java code for iOS can't handle Kotlin lambda parameters
    constructor(version: String) : this(UncivGameParameters(version, null))

    val version = parameters.version
    val crashReportSysInfo = parameters.crashReportSysInfo
    val cancelDiscordEvent = parameters.cancelDiscordEvent
    var fontImplementation = parameters.fontImplementation
    val consoleMode = parameters.consoleMode
    private val customSaveLocationHelper = parameters.customFileLocationHelper
    val platformSpecificHelper = parameters.platformSpecificHelper
    private val audioExceptionHelper = parameters.audioExceptionHelper

    var deepLinkedMultiplayerGame: String? = null
    var gameInfo: GameInfo? = null
    lateinit var settings: GameSettings
    lateinit var musicController: MusicController
    lateinit var onlineMultiplayer: OnlineMultiplayer
    lateinit var gameSaver: GameSaver

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
    var simulateUntilTurnForDebug: Int = 0

    var worldScreen: WorldScreen? = null
        private set

    var isInitialized = false

    /** A wrapped render() method that crashes to [CrashScreen] on a unhandled exception or error. */
    private val wrappedCrashHandlingRender = { super.render() }.wrapCrashHandlingUnit()
    // Stored here because I imagine that might be slightly faster than allocating for a new lambda every time, and the render loop is possibly one of the only places where that could have a significant impact.


    val translations = Translations()

    override fun create() {
        isInitialized = false // this could be on reload, therefore we need to keep setting this to false
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        if (Gdx.app.type != Application.ApplicationType.Desktop) {
            viewEntireMapForDebug = false
        }
        Current = this
        gameSaver = GameSaver(Gdx.files, customSaveLocationHelper, platformSpecificHelper?.shouldPreferExternalStorage() == true)

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
        settings = gameSaver.getGeneralSettings() // needed for the screen
        setScreen(LoadingScreen())  // NOT dependent on any atlas or skin
        GameSounds.init()
        musicController = MusicController()  // early, but at this point does only copy volume from settings
        audioExceptionHelper?.installHooks(
            musicController.getAudioLoopCallback(),
            musicController.getAudioExceptionHandler()
        )
        onlineMultiplayer = OnlineMultiplayer()

        ImageGetter.resetAtlases()
        ImageGetter.setNewRuleset(ImageGetter.ruleset)  // This needs to come after the settings, since we may have default visual mods
        if (settings.tileSet !in ImageGetter.getAvailableTilesets()) { // If one of the tilesets is no longer available, default back
            settings.tileSet = "FantasyHex"
        }

        BaseScreen.setSkin() // needs to come AFTER the Texture reset, since the buttons depend on it

        Gdx.graphics.isContinuousRendering = settings.continuousRendering

        Concurrency.run("LoadJSON") {
            RulesetCache.loadRulesets()
            translations.tryReadTranslationForCurrentLanguage()
            translations.loadPercentageCompleteOfLanguages()
            TileSetCache.loadTileSetConfigs()

            if (settings.multiplayer.userId.isEmpty()) { // assign permanent user id
                settings.multiplayer.userId = UUID.randomUUID().toString()
                settings.save()
            }

            // This stuff needs to run on the main thread because it needs the GL context
            launchOnGLThread {
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

    fun loadGame(gameInfo: GameInfo): WorldScreen {
        this.gameInfo = gameInfo
        ImageGetter.setNewRuleset(gameInfo.ruleSet)
        // Clone the mod list and add the base ruleset to it
        val fullModList = gameInfo.gameParameters.getModsAndBaseRuleset()
        musicController.setModList(fullModList)
        Gdx.input.inputProcessor = null // Since we will set the world screen when we're ready,
        val worldScreen = WorldScreen(gameInfo, gameInfo.getPlayerToViewAs())
        val newScreen = if (gameInfo.civilizations.count { it.playerType == PlayerType.Human } > 1 && !gameInfo.gameParameters.isOnlineMultiplayer)
            PlayerReadyScreen(worldScreen)
        else {
            worldScreen
        }
        setScreen(newScreen)
        return worldScreen
    }

    /**
     * Sets the screen of the game and automatically disposes the old screen as long as it isn't the world screen.
     *
     * @param screen must be a subclass of [BaseScreen].
     */
    override fun setScreen(screen: Screen) {
        if (screen !is BaseScreen) throw IllegalArgumentException("Call to setScreen with screen that does not inherit BaseScreen: " + screen.javaClass.simpleName)
        setScreen(screen)
    }

    override fun getScreen(): BaseScreen? {
        val curScreen = super.getScreen()
        return if (curScreen == null) { null } else { curScreen as BaseScreen }
    }

    /** Sets the screen of the game and automatically [disposes][Screen.dispose] the old screen as long as it isn't the world screen. */
    fun setScreen(newScreen: BaseScreen) {
        if (newScreen is WorldScreen) {
            debug(
                "Setting new world screen: gameId: %s, turn: %s, curCiv: %s",
                newScreen.gameInfo.gameId, newScreen.gameInfo.turns, newScreen.gameInfo.currentPlayer
            )
            if (newScreen != worldScreen) worldScreen?.dispose()
            worldScreen = newScreen
            newScreen.shouldUpdate = true
            Gdx.graphics.requestRendering()
        } else {
            debug("Setting new screen: %s", newScreen)
        }

        val oldScreen = screen
        Gdx.input.inputProcessor = newScreen.stage
        super.setScreen(newScreen) // This can set the screen to the policy picker or tech picker screen, so the input processor must be set before
        if (oldScreen !is WorldScreen) { // we want to keep the world screen around, because it's expensive to re-create it
            oldScreen?.dispose()
        }
    }

    /**
     * Resets the game to the stored world screen and automatically [disposes][Screen.dispose] the old screen.
     */
    fun resetToWorldScreen() {
        setScreen(worldScreen!!)
    }

    private fun tryLoadDeepLinkedGame() = Concurrency.run("LoadDeepLinkedGame") {
        if (deepLinkedMultiplayerGame == null) return@run

        launchOnGLThread {
            setScreen(LoadDeepLinkScreen())
        }
        try {
            onlineMultiplayer.loadGame(deepLinkedMultiplayerGame!!)
        } catch (ex: Exception) {
            launchOnGLThread {
                val mainMenu = MainMenuScreen()
                setScreen(mainMenu)
                val popup = Popup(mainMenu)
                popup.addGoodSizedLabel(MultiplayerHelpers.getLoadExceptionMessage(ex))
                popup.row()
                popup.addCloseButton()
                popup.open()
            }
        } finally {
            deepLinkedMultiplayerGame = null
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
        val curGameInfo = gameInfo
        if (curGameInfo != null) gameSaver.requestAutoSave(curGameInfo)
        musicController.pause()
        super.pause()
    }

    override fun resize(width: Int, height: Int) {
        screen.resize(width, height)
    }

    override fun render() = wrappedCrashHandlingRender()

    override fun dispose() {
        Gdx.input.inputProcessor = null // don't allow ANRs when shutting down, that's silly

        cancelDiscordEvent?.invoke()
        SoundPlayer.clearCache()
        if (::musicController.isInitialized) musicController.gracefulShutdown()  // Do allow fade-out

        val curGameInfo = gameInfo
        if (curGameInfo != null) {
            val autoSaveJob = gameSaver.autoSaveJob
            if (autoSaveJob != null && autoSaveJob.isActive) {
                // auto save is already in progress (e.g. started by onPause() event)
                // let's allow it to finish and do not try to autosave second time
                Concurrency.runBlocking {
                    autoSaveJob.join()
                }
            } else {
                gameSaver.autoSave(curGameInfo)      // NO new thread
            }
        }
        settings.save()
        Concurrency.stopThreadPools()

        // On desktop this should only be this one and "DestroyJavaVM"
        logRunningThreads()
    }

    private fun logRunningThreads() {
        val numThreads = Thread.activeCount()
        val threadList = Array(numThreads) { _ -> Thread() }
        Thread.enumerate(threadList)
        threadList.filter { it !== Thread.currentThread() && it.name != "DestroyJavaVM" }.forEach {
            debug("Thread %s still running in UncivGame.dispose().", it.name)
        }
    }

    /** Handles an uncaught exception or error. First attempts a platform-specific handler, and if that didn't handle the exception or error, brings the game to a [CrashScreen]. */
    fun handleUncaughtThrowable(ex: Throwable) {
        Log.error("Uncaught throwable", ex)
        if (platformSpecificHelper?.handleUncaughtThrowable(ex) == true) return
        Gdx.app.postRunnable {
            setScreen(CrashScreen(ex))
        }
    }

    /** Returns the [worldScreen] if it is the currently active screen of the game */
    fun getWorldScreenIfActive(): WorldScreen? {
        return if (screen == worldScreen) worldScreen else null
    }

    companion object {
        lateinit var Current: UncivGame
        fun isCurrentInitialized() = this::Current.isInitialized
        fun isCurrentGame(gameId: String): Boolean = isCurrentInitialized() && Current.gameInfo != null && Current.gameInfo!!.gameId == gameId
        fun isDeepLinkedGameLoading() = isCurrentInitialized() && Current.deepLinkedMultiplayerGame != null
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

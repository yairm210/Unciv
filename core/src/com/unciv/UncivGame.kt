package com.unciv

import com.badlogic.gdx.Application
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.UncivGame.Companion.Current
import com.unciv.UncivGame.Companion.isCurrentInitialized
import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.UncivShowableException
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.skins.SkinCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicController
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.crashhandling.CrashScreen
import com.unciv.ui.crashhandling.wrapCrashHandlingUnit
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.LanguagePickerScreen
import com.unciv.ui.screens.LoadingScreen
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.worldscreen.PlayerReadyScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.unit.AutoPlay
import com.unciv.utils.Concurrency
import com.unciv.utils.DebugUtils
import com.unciv.utils.Display
import com.unciv.utils.Log
import com.unciv.utils.PlatformSpecific
import com.unciv.utils.debug
import com.unciv.utils.launchOnGLThread
import com.unciv.utils.withGLContext
import com.unciv.utils.withThreadPoolContext
import kotlinx.coroutines.CancellationException
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import java.io.PrintWriter
import java.util.EnumSet
import java.util.UUID
import kotlin.reflect.KClass

/** Represents the Unciv app itself:
 *  - implements the [Game] interface Gdx requires.
 *  - marshals [platform-specific stuff][PlatformSpecific].
 *  - contains references to [the game being played][gameInfo], and high-level UI elements.
 */
open class UncivGame(val isConsoleMode: Boolean = false) : Game(), PlatformSpecific {

    var deepLinkedMultiplayerGame: String? = null
    override var customDataDirectory: String? = null

    /** The game currently in progress */
    var gameInfo: GameInfo? = null

    lateinit var settings: GameSettings
    lateinit var musicController: MusicController
    lateinit var onlineMultiplayer: Multiplayer
    lateinit var files: UncivFiles

    var isTutorialTaskCollapsed = false

    var worldScreen: WorldScreen? = null
        private set

    /** Flag used only during initialization until the end of [create] */
    protected var isInitialized = false
        private set

    /** A wrapped render() method that crashes to [CrashScreen] on a unhandled exception or error. */
    private val wrappedCrashHandlingRender = { super.render() }.wrapCrashHandlingUnit()
    // Stored here because I imagine that might be slightly faster than allocating for a new lambda every time, and the render loop is possibly one of the only places where that could have a significant impact.

    val translations = Translations()

    private val screenStack = ArrayDeque<BaseScreen>()

    override fun create() {
        isInitialized = false // this could be on reload, therefore we need to keep setting this to false
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        if (Gdx.app.type != Application.ApplicationType.Desktop) {
            DebugUtils.VISIBLE_MAP = false
        }
        Current = this
        files = UncivFiles(Gdx.files, customDataDirectory)
        Concurrency.run {
            // Delete temporary files created when downloading mods
            val tempFiles = files.getLocalFile("mods").list().filter { !it.isDirectory && it.name().startsWith("temp-") }
            for (file in tempFiles) file.delete()
        }

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
        settings = files.getGeneralSettings() // needed for the screen
        Display.setScreenMode(settings.screenMode, settings)
        setAsRootScreen(GameStartScreen())  // NOT dependent on any atlas or skin

        musicController = MusicController()  // early, but at this point does only copy volume from settings
        installAudioHooks()

        onlineMultiplayer = Multiplayer()

        Concurrency.run {
            // Check if the server is available in case the feature set has changed
            try {
                onlineMultiplayer.multiplayerServer.checkServerStatus()
            } catch (ex: Exception) {
                debug("Couldn't connect to server: " + ex.message)
            }
        }

        ImageGetter.resetAtlases()
        ImageGetter.reloadImages()  // This needs to come after the settings, since we may have default visual mods
        val imageGetterTilesets = ImageGetter.getAvailableTilesets()
        val availableTileSets = TileSetCache.getAvailableTilesets(imageGetterTilesets)
        if (settings.tileSet !in availableTileSets) { // If the configured tileset is no longer available, default back
            settings.tileSet = Constants.defaultTileset
        }

        Gdx.graphics.isContinuousRendering = settings.continuousRendering

        Concurrency.run("LoadJSON") {
            RulesetCache.loadRulesets()
            translations.tryReadTranslationForCurrentLanguage()
            translations.loadPercentageCompleteOfLanguages()
            TileSetCache.loadTileSetConfigs()
            SkinCache.loadSkinConfigs()

            val vanillaRuleset = RulesetCache.getVanillaRuleset()

            if (settings.multiplayer.getUserId().isEmpty()) { // assign permanent user id
                settings.multiplayer.setUserId(UUID.randomUUID().toString())
                settings.save()
            }

            // Loading available fonts can take a long time on Android phones.
            // Therefore we initialize the lazy parameters in the font implementation, while we're in another thread, to avoid ANRs on main thread
            Fonts.fontImplementation.setFontFamily(settings.fontFamilyData, settings.getFontSize())

            // This stuff needs to run on the main thread because it needs the GL context
            launchOnGLThread {
                BaseScreen.setSkin() // needs to come AFTER the Texture reset, since the buttons depend on it and after loadSkinConfigs to be able to use the SkinConfig

                musicController.chooseTrack(suffixes = listOf(MusicMood.Menu, MusicMood.Ambient),
                    flags = EnumSet.of(MusicTrackChooserFlags.SuffixMustMatch))

                ImageGetter.ruleset = vanillaRuleset // so that we can enter the map editor without having to load a game first

                when {
                    settings.isFreshlyCreated -> setAsRootScreen(LanguagePickerScreen())
                    deepLinkedMultiplayerGame == null -> setAsRootScreen(MainMenuScreen())
                    else -> tryLoadDeepLinkedGame()
                }

                isInitialized = true
            }
        }
    }

    /**
     * Loads a game, [disposing][BaseScreen.dispose] all screens.
     *
     * Initializes the state of all important modules.
     *
     * Automatically runs on the appropriate thread.
     *
     * Sets the returned `WorldScreen` as the only active screen.
     * @param autoPlay pass in the old WorldScreen AutoPlay to retain the state throughout turns. Otherwise leave it is the default.
     */
    suspend fun loadGame(newGameInfo: GameInfo, autoPlay: AutoPlay = AutoPlay(settings.autoPlay), callFromLoadScreen: Boolean = false): WorldScreen = withThreadPoolContext toplevel@{
        val prevGameInfo = gameInfo
        gameInfo = newGameInfo


        if (gameInfo?.gameParameters?.isOnlineMultiplayer == true
                && gameInfo?.gameParameters?.anyoneCanSpectate == false
                && gameInfo!!.civilizations.none { it.playerId == settings.multiplayer.getUserId() }) {
            throw UncivShowableException("You are not allowed to spectate!")
        }

        initializeResources(newGameInfo)

        val isLoadingSameGame = worldScreen != null && prevGameInfo != null && prevGameInfo.gameId == newGameInfo.gameId
        val worldScreenRestoreState = if (!callFromLoadScreen && isLoadingSameGame) worldScreen!!.getRestoreState() else null

        lateinit var loadingScreen: LoadingScreen

        withGLContext {
            // this is not merged with the below GL context block so that our loading screen gets a chance to show - otherwise
            // we do it all in one swoop on the same thread and the application just "freezes" without loading screen for the duration.
            loadingScreen = LoadingScreen(getScreen())
            setScreen(loadingScreen)
            Gdx.input.inputProcessor = null // It's just been set by setScreen, so unset it to avoid ANRs while loading
        }

        return@toplevel withGLContext {
            for (screen in screenStack) screen.dispose()
            screenStack.clear()

            worldScreen = null // This allows the GC to collect our old WorldScreen, otherwise we keep two WorldScreens in memory.
            val newWorldScreen = WorldScreen(newGameInfo, autoPlay, newGameInfo.getPlayerToViewAs(), worldScreenRestoreState)
            worldScreen = newWorldScreen

            val moreThanOnePlayer = newGameInfo.civilizations.count { it.playerType == PlayerType.Human } > 1
            val isSingleplayer = !newGameInfo.gameParameters.isOnlineMultiplayer
            val screenToShow = if (moreThanOnePlayer && isSingleplayer) {
                PlayerReadyScreen(newWorldScreen)
            } else {
                newWorldScreen
            }

            screenStack.addLast(screenToShow)
            setScreen(screenToShow) // Only here do we set the inputProcessor again 
            loadingScreen.dispose()

            return@withGLContext newWorldScreen
        }
    }

    /** The new game info may have different mods or rulesets, which may use different resources that need to be loaded. */
    private suspend fun initializeResources(newGameInfo: GameInfo) {
        withGLContext {
            ImageGetter.setNewRuleset(newGameInfo.ruleset, true)
        }
        val fullModList = newGameInfo.gameParameters.getModsAndBaseRuleset()
        musicController.setModList(fullModList)
    }

    /** Re-creates the current [worldScreen], if there is any. */
    suspend fun reloadWorldscreen() {
        val curWorldScreen = worldScreen
        val curGameInfo = gameInfo
        if (curWorldScreen == null || curGameInfo == null) return

        loadGame(curGameInfo)
    }

    /**
     * @throws UnsupportedOperationException Use pushScreen or replaceCurrentScreen instead
     */
    @Deprecated("Never use this, it's only here because it's part of the gdx.Game interface.", ReplaceWith("pushScreen"))
    override fun setScreen(screen: Screen) {
        throw UnsupportedOperationException("Use pushScreen or replaceCurrentScreen instead")
    }

    override fun getScreen(): BaseScreen? = super.getScreen() as? BaseScreen

    private fun setScreen(newScreen: BaseScreen) {
        debug("Setting new screen: %s, screenStack: %s", newScreen, screenStack)
        Gdx.input.inputProcessor = newScreen.stage
        super.setScreen(newScreen) // This can set the screen to the policy picker or tech picker screen, so the input processor must be set before
        if (newScreen is WorldScreen) {
            newScreen.shouldUpdate = true
        }
        Gdx.graphics.requestRendering()
    }

    /** Removes & [disposes][BaseScreen.dispose] all currently active screens in the [screenStack] and sets the given screen as the only screen. */
    private fun setAsRootScreen(root: BaseScreen) {
        for (screen in screenStack) screen.dispose()
        screenStack.clear()
        screenStack.addLast(root)
        setScreen(root)
    }
    /** Adds a screen to be displayed instead of the current screen, with an option to go back to the previous screen by calling [popScreen] */
    fun pushScreen(newScreen: BaseScreen) {
        screenStack.addLast(newScreen)
        setScreen(newScreen)
    }

    /**
     * Pops the currently displayed screen off the screen stack and shows the previous screen.
     *
     * If there is no other screen than the current, will ask the user to quit the game and return null.
     *
     * Automatically [disposes][BaseScreen.dispose] the old screen.
     *
     * @return the new screen
     */
    fun popScreen(): BaseScreen? {
        if (screenStack.size == 1) {
            musicController.pause()
            worldScreen?.autoPlay?.stopAutoPlay()
            ConfirmPopup(
                screen = screenStack.last(),
                question = "Do you want to exit the game?",
                confirmText = "Exit",
                restoreDefault = { musicController.resumeFromShutdown() },
                action = { Gdx.app.exit() }
            ).open(force = true)
            return null
        }
        val oldScreen = screenStack.removeLast()
        val newScreen = screenStack.last()
        setScreen(newScreen)
        newScreen.resume()
        oldScreen.dispose()
        return newScreen
    }

    /** Replaces the current screen with a new one. Automatically [disposes][BaseScreen.dispose] the old screen. */
    fun replaceCurrentScreen(newScreen: BaseScreen) {
        val oldScreen = screenStack.removeLast()
        screenStack.addLast(newScreen)
        setScreen(newScreen)
        oldScreen.dispose()
    }

    /** Resets the game to the stored world screen and automatically [disposes][Screen.dispose] all other screens. */
    fun resetToWorldScreen(): WorldScreen {
        for (screen in screenStack.filter { it !is WorldScreen }) screen.dispose()
        screenStack.removeAll { it !is WorldScreen }
        val worldScreen = screenStack.last() as WorldScreen

        // Re-initialize translations, images etc. that may have been 'lost' when we were playing around in NewGameScreen
        val ruleset = worldScreen.gameInfo.ruleset
        translations.translationActiveMods = ruleset.mods
        ImageGetter.setNewRuleset(ruleset, true)

        setScreen(worldScreen)
        return worldScreen
    }

    /** Get all currently existing screens of type [clazz]
     *  - Not a generic to allow screenStack to be private
     */
    @Readonly
    fun getScreensOfType(clazz: KClass<out BaseScreen>): Sequence<BaseScreen> = 
        screenStack.asSequence().filter { it::class == clazz }

    /** Dispose and remove all currently existing screens of type [clazz]
     *  - Not a generic to allow screenStack to be private
     */
    fun removeScreensOfType(clazz: KClass<out BaseScreen>) {
        val toRemove = getScreensOfType(clazz).toList()
        for (screen in toRemove) screen.dispose()
        screenStack.removeAll(toRemove)
    }

    private fun tryLoadDeepLinkedGame() = Concurrency.run("LoadDeepLinkedGame") {
        if (deepLinkedMultiplayerGame == null) return@run

        launchOnGLThread {
            if (screenStack.isEmpty() || screenStack[0] !is GameStartScreen) {
                setAsRootScreen(LoadingScreen(getScreen()!!))
            }
        }
        try {
            onlineMultiplayer.downloadGame(deepLinkedMultiplayerGame!!)
        } catch (ex: Exception) {
            launchOnGLThread {
                val mainMenu = MainMenuScreen()
                replaceCurrentScreen(mainMenu)
                val popup = Popup(mainMenu)
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                popup.addGoodSizedLabel(message)
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
        if (!isInitialized) return // The stuff from Create() is still happening, so the main screen will load eventually
        musicController.resumeFromShutdown()

        // This is also needed in resume to open links and notifications
        // correctly when the app was already running. The handling in onCreate
        // does not seem to be enough
        tryLoadDeepLinkedGame()
    }

    override fun pause() {
        // Needs to go ASAP - on Android, there's a tiny race condition: The OS will stop our playback forcibly, it likely
        // already has, but if we do _our_ pause before the MusicController timer notices, it will at least remember the current track.
        if (::musicController.isInitialized) musicController.pause()
        val curGameInfo = gameInfo
        // Since we're pausing the game, we don't need to clone it before autosave - no one else will touch it
        if (curGameInfo != null) files.autosaves.requestAutoSaveUnCloned(curGameInfo)
        super.pause()
    }

    override fun resize(width: Int, height: Int) {
        screen.resize(width, height)
    }

    override fun render() = wrappedCrashHandlingRender()

    override fun dispose() {
        Gdx.input.inputProcessor = null // don't allow ANRs when shutting down, that's silly
        SoundPlayer.clearCache()
        if (::musicController.isInitialized) musicController.gracefulShutdown()  // Do allow fade-out
        // We stop the *in-game* multiplayer update, so that it doesn't keep working and A. we'll have errors and B. we'll have multiple updaters active
        if (::onlineMultiplayer.isInitialized) onlineMultiplayer.multiplayerGameUpdater.cancel()

        val curGameInfo = gameInfo
        if (curGameInfo != null) {
            val autoSaveJob = files.autosaves.autoSaveJob
            if (autoSaveJob != null && autoSaveJob.isActive) {
                // auto save is already in progress (e.g. started by onPause() event)
                // let's allow it to finish and do not try to autosave second time
                Concurrency.runBlocking {
                    autoSaveJob.join()
                }
            } else {
                files.autosaves.autoSave(curGameInfo)      // NO new thread
            }
        }
        settings.save()
        Concurrency.stopThreadPools()

        // On desktop this should only be this one and "DestroyJavaVM"
        logRunningThreads()

        // DO NOT `exitProcess(0)` - bypasses all Gdx and GLFW cleanup
    }

    private fun logRunningThreads() {
        val numThreads = Thread.activeCount()
        val threadList = Array(numThreads) { Thread() }
        Thread.enumerate(threadList)
        threadList.filter { it !== Thread.currentThread() && it.name != "DestroyJavaVM" }.forEach {
            debug("Thread %s still running in UncivGame.dispose().", it.name)
        }
    }

    /** Handles an uncaught exception or error. First attempts a platform-specific handler, and if that didn't handle the exception or error, brings the game to a [CrashScreen]. */
    fun handleUncaughtThrowable(ex: Throwable) {
        if (ex is CancellationException) {
            return // kotlin coroutines use this for control flow... so we can just ignore them.
        }
        Log.error("Uncaught throwable", ex)
        try {
            PrintWriter(files.fileWriter("lasterror.txt")).use {
                ex.printStackTrace(it)
            }
        } catch (_: Exception) {
            // ignore
        }
        Gdx.app.postRunnable {
            Gdx.input.inputProcessor = null // CrashScreen needs to toJson which can take a while
            // This may not be enough, we may need to run "generate crash text" in a different thread,
            //   but for now let's try this.
            setAsRootScreen(CrashScreen(ex))
        }
    }

    /** Returns the [worldScreen] if it is the currently active screen of the game */
    @Readonly
    fun getWorldScreenIfActive(): WorldScreen? {
        return if (screen == worldScreen) worldScreen else null
    }

    fun goToMainMenu(): MainMenuScreen {
        val curGameInfo = gameInfo
        if (curGameInfo != null) {
            files.autosaves.requestAutoSaveUnCloned(curGameInfo) // Can save gameInfo directly because the user can't modify it on the MainMenuScreen
        }
        val mainMenuScreen = MainMenuScreen()
        pushScreen(mainMenuScreen)
        return mainMenuScreen
    }

    companion object {
        //region AUTOMATICALLY GENERATED VERSION DATA - DO NOT CHANGE THIS REGION, INCLUDING THIS COMMENT
        val VERSION = Version("4.17.18", 1160)
        //endregion

        /** Global reference to the one Gdx.Game instance created by the platform launchers - do not use without checking [isCurrentInitialized] first. */
        // Set by Gdx Game.create callback, or the special cases ConsoleLauncher and unit tests make do with out Gdx and set this themselves.
        lateinit var Current: UncivGame
        /** @return `true` if [Current] has been set yet and can be accessed */
        @Readonly fun isCurrentInitialized() = this::Current.isInitialized
        /** Get the game currently in progress safely - null either if [Current] has not yet been set or if its gameInfo field has no game */
        @Readonly fun getGameInfoOrNull() = if (isCurrentInitialized()) Current.gameInfo else null
        @Readonly fun isCurrentGame(gameId: String): Boolean = isCurrentInitialized() && Current.gameInfo != null && Current.gameInfo!!.gameId == gameId
        @Readonly fun isDeepLinkedGameLoading() = isCurrentInitialized() && Current.deepLinkedMultiplayerGame != null

        @Readonly
        fun getUserAgent(fallbackStr: String = "Unknown"): String = if (isCurrentInitialized()) {
            "Unciv/${VERSION.toNiceString()}-GNU-Terry-Pratchett"
        } else "Unciv/$fallbackStr-GNU-Terry-Pratchett"
    }

    data class Version(
        val text: String,
        val number: Int
    ) : IsPartOfGameInfoSerialization {
        @Suppress("unused") // used by json serialization
        constructor() : this("", -1)
        @Pure fun toNiceString() = "$text (Build ${number.tr()})"
    }
}

class GameStartScreen : BaseScreen() {
    init {
        val logoImage = ImageGetter.getExternalImage("banner.png")
        logoImage.center(stage)
        logoImage.color.a = 0f
        logoImage.addAction(Actions.alpha(1f, 0.3f))
        stage.addActor(logoImage)
    }
}

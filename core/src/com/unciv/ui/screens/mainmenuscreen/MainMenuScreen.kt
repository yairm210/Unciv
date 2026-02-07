package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Application
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.HolidayDates
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.platform.PlatformCapabilities
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.surroundWithThinCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onLongPress
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.padTopDescent
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.popups.options.aboutTab
import com.unciv.ui.popups.popups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.mainmenuscreen.EasterEggRulesets.modifyForEasterEgg
import com.unciv.ui.screens.mapeditorscreen.EditorMapHolder
import com.unciv.ui.screens.mapeditorscreen.MapEditorScreen
import com.unciv.ui.screens.modmanager.ModManagementScreen
import com.unciv.ui.screens.multiplayerscreens.MultiplayerScreen
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.savescreens.QuickSave
import com.unciv.ui.screens.worldscreen.BackgroundActor
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMenuPopup
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.Job
import yairm210.purity.annotations.Pure
import kotlin.math.ceil


class MainMenuScreen: BaseScreen(), RecreateOnResize {
    private val backgroundStack = Stack()
    private val singleColumn = isCrampedPortrait()

    private val backgroundMapRuleset: Ruleset
    private var easterEggRuleset: Ruleset? = null  // Cache it so the next 'egg' can be found in Civilopedia

    private var backgroundMapGenerationJob: Job? = null
    private var backgroundMapExists = false

    companion object {
        const val mapFadeTime = 1.3f
        const val mapFirstFadeTime = 0.3f
        const val mapReplaceDelay = 20f
        /** Inner size of the Civilopedia+Discord+Github buttons (effective size adds 2f for the thin circle) */
        const val buttonsSize = 60f
        /** Distance of the Civilopedia and Discord+Github buttons from the stage edges */
        const val buttonsPosFromEdge = 30f
    }

    /** Create one **Main Menu Button** including onClick/key binding
     *  @param text      The text to display on the button
     *  @param icon      The path of the icon to display on the button
     *  @param binding   keyboard binding
     *  @param function  Action to invoke when the button is activated
     */
    private fun getMenuButton(
        text: String,
        icon: String,
        binding: KeyboardBinding?,
        function: () -> Unit
    ): Table {
        var buildStep = "start"
        try {
            buildStep = "create table"
            val table = Table().pad(15f, 30f, 15f, 30f)
            buildStep = "set background"
            table.background = skinStrings.getUiBackground(
                "MainMenuScreen/MenuButton",
                skinStrings.roundedEdgeRectangleShape,
                skinStrings.skinConfig.baseColor
            )
            buildStep = "add icon=$icon"
            table.add(ImageGetter.getImage(icon)).size(50f).padRight(20f)
            buildStep = "add label=$text"
            table.add(text.toLabel(fontSize = 30, alignment = Align.left)).expand().left().minWidth(200f)
                .padTopDescent()

            buildStep = "set touch + activation"
            table.touchable = Touchable.enabled
            if (binding != null) {
                table.onActivation(binding = binding) {
                    stopBackgroundMapGeneration()
                    function()
                }
            } else {
                table.onClick {
                    stopBackgroundMapGeneration()
                    function()
                }
            }

            buildStep = "pack"
            table.pack()
            return table
        } catch (ex: Exception) {
            throw IllegalStateException(
                "MainMenu button build failed (text=$text icon=$icon binding=$binding step=$buildStep)",
                ex
            )
        }
    }

    init {
        var initStep = "init-start"
        try {
        initStep = "SoundPlayer.initializeForMainMenu"
        SoundPlayer.initializeForMainMenu()

        initStep = "background actor setup"
        val background = skinStrings.getUiBackground("MainMenuScreen/Background", tintColor = clearColor)
        backgroundStack.add(BackgroundActor(background, Align.center))
        stage.addActor(backgroundStack)
        backgroundStack.setFillParent(true)

        // If we were in a mod, some of the resource images for the background map we're creating
        // will not exist unless we reset the ruleset and images
        initStep = "load base ruleset"
        val baseRuleset = RulesetCache.getVanillaRuleset()
        ImageGetter.setNewRuleset(baseRuleset)

        initStep = "setup easter eggs"
        if (game.settings.enableEasterEggs) {
            val holiday = HolidayDates.getHolidayByDate()
            if (holiday != null)
                EasterEggFloatingArt(stage, holiday.name)
            val easterEggMod = EasterEggRulesets.getTodayEasterEggRuleset()
            if (easterEggMod != null)
                easterEggRuleset = RulesetCache.getComplexRuleset(baseRuleset, listOf(easterEggMod))
        }
        backgroundMapRuleset = easterEggRuleset ?: baseRuleset

        // This is an extreme safeguard - should an invalid settings.tileSet ever make it past the
        // guard in UncivGame.create, simply omit the background so the user can at least get to options
        // (let him crash when loading a game but avoid locking him out entirely)
        initStep = "start background map generation"
        if (game.settings.tileSet in TileSetCache)
            startBackgroundMapGeneration()

        initStep = "build main menu columns"
        val column1 = Table().apply { defaults().pad(10f).fillX() }
        val column2 = if (singleColumn) column1 else Table().apply { defaults().pad(10f).fillX() }
        val enableKeyboardBindings = Gdx.app.type != Application.ApplicationType.WebGL

        initStep = "autosave resume button check"
        if (game.files.autosaves.autosaveExists()) {
            val resumeBinding = if (enableKeyboardBindings) KeyboardBinding.Resume else null
            val resumeTable = getMenuButton("Resume","OtherIcons/Resume", resumeBinding)
                { resumeGame() }
            column1.add(resumeTable).row()
        }

        initStep = "quickstart button:binding"
        val quickstartBinding = if (enableKeyboardBindings) KeyboardBinding.Quickstart else null
        initStep = "quickstart button:create"
        val quickstartTable = getMenuButton("Quickstart", "OtherIcons/Quickstart", quickstartBinding)
            { quickstartNewGame() }
        initStep = "quickstart button:add"
        column1.add(quickstartTable).row()

        initStep = "start new game button"
        val startNewGameBinding = if (enableKeyboardBindings) KeyboardBinding.StartNewGame else null
        val newGameButton = getMenuButton("Start new game", "OtherIcons/New", startNewGameBinding)
            { game.pushScreen(NewGameScreen()) }
        column1.add(newGameButton).row()

        initStep = "load game button"
        val loadGameBinding = if (enableKeyboardBindings) KeyboardBinding.MainMenuLoad else null
        val loadGameTable = getMenuButton("Load game", "OtherIcons/Load", loadGameBinding)
            { game.pushScreen(LoadGameScreen()) }
        column1.add(loadGameTable).row()

        if (PlatformCapabilities.current.onlineMultiplayer) {
            initStep = "multiplayer button"
            val multiplayerBinding = if (enableKeyboardBindings) KeyboardBinding.Multiplayer else null
            val multiplayerTable = getMenuButton("Multiplayer", "OtherIcons/Multiplayer", multiplayerBinding)
                { game.pushScreen(MultiplayerScreen()) }
            column2.add(multiplayerTable).row()
        }

        initStep = "map editor button"
        val mapEditorBinding = if (enableKeyboardBindings) KeyboardBinding.MapEditor else null
        val mapEditorScreenTable = getMenuButton("Map editor", "OtherIcons/MapEditor", mapEditorBinding)
            { game.pushScreen(MapEditorScreen()) }
        column2.add(mapEditorScreenTable).row()

        initStep = "mods button"
        val modsBinding = if (enableKeyboardBindings) KeyboardBinding.ModManager else null
        val modsTable = getMenuButton("Mods", "OtherIcons/Mods", modsBinding)
            { game.pushScreen(ModManagementScreen()) }
        column2.add(modsTable).row()

        initStep = "options button"
        val optionsBinding = if (enableKeyboardBindings) KeyboardBinding.MainMenuOptions else null
        val optionsTable = getMenuButton("Options", "OtherIcons/Options", optionsBinding)
            { openOptionsPopup() }
        optionsTable.onLongPress { openOptionsPopup(withDebug = true) }
        column2.add(optionsTable).row()


        initStep = "menu table + scroll pane"
        val table = Table().apply { defaults().pad(10f) }
        table.add(column1)
        if (!singleColumn) table.add(column2)
        table.pack()

        val scrollPane = AutoScrollPane(table)
        scrollPane.setFillParent(true)
        stage.addActor(scrollPane)
        table.center(scrollPane)

        initStep = "global shortcuts setup"
        if (enableKeyboardBindings) {
            globalShortcuts.add(KeyboardBinding.QuitMainMenu) {
                if (hasOpenPopups()) {
                    closeAllPopups()
                    return@add
                }
                game.popScreen()
            }
        }

        initStep = "civilopedia button setup"
        val civilopediaButton = "?".toLabel(fontSize = 48)
            .apply { setAlignment(Align.center) }
            .surroundWithCircle(buttonsSize, color = skinStrings.skinConfig.baseColor)
            .apply { actor.y -= Fonts.getDescenderHeight(48) / 2 } // compensate font baseline
            .surroundWithThinCircle(Color.WHITE)
        civilopediaButton.touchable = Touchable.enabled
        // Passing the binding directly to onActivation gives you a size 26 tooltip...
        civilopediaButton.onActivation { openCivilopedia() }
        if (enableKeyboardBindings) {
            civilopediaButton.keyShortcuts.add(KeyboardBinding.Civilopedia)
            civilopediaButton.addTooltip(KeyboardBinding.Civilopedia, 30f)
        }
        civilopediaButton.setPosition(buttonsPosFromEdge, buttonsPosFromEdge)
        stage.addActor(civilopediaButton)

        initStep = "right-side buttons setup"
        val rightSideButtons = Table().apply { defaults().space(10f) }
        val discordButton = ImageGetter.getImage("OtherIcons/Discord")
            .surroundWithCircle(buttonsSize, color = skinStrings.skinConfig.baseColor)
            .surroundWithThinCircle(Color.WHITE)
            .onActivation { Gdx.net.openURI("https://discord.gg/bjrB4Xw") }
        rightSideButtons.add(discordButton)

        val githubButton = ImageGetter.getImage("OtherIcons/Github")
            .surroundWithCircle(buttonsSize, color = skinStrings.skinConfig.baseColor)
            .surroundWithThinCircle(Color.WHITE)
            .onActivation { Gdx.net.openURI(Constants.uncivRepoURL) }
        rightSideButtons.add(githubButton)

        rightSideButtons.pack()
        rightSideButtons.setPosition(stage.width - buttonsPosFromEdge, buttonsPosFromEdge, Align.bottomRight)
        stage.addActor(rightSideButtons)

        initStep = "version label setup"
        val versionLabel = "{Version} ${UncivGame.VERSION.text}".toLabel()
        versionLabel.setAlignment(Align.center)
        val versionTable = Table()
        versionTable.background = skinStrings.getUiBackground("MainMenuScreen/Version",
            skinStrings.roundedEdgeRectangleShape, Color.DARK_GRAY.cpy().apply { a = 0.7f })
        versionTable.add(versionLabel)
        versionTable.pack()
        versionTable.setPosition(stage.width / 2, 10f, Align.bottom)
        versionTable.touchable = Touchable.enabled
        versionTable.onClick {
            val popup = Popup(stage)
            popup.add(aboutTab()).row()
            popup.addCloseButton()
            popup.open()
        }
        stage.addActor(versionTable)
        } catch (ex: Exception) {
            throw IllegalStateException("MainMenuScreen init failed at step: $initStep", ex)
        }
    }

    private fun startBackgroundMapGeneration() {
        stopBackgroundMapGeneration()  // shouldn't be necessary as resize re-instantiates this class
        backgroundMapGenerationJob = Concurrency.run("ShowMapBackground") {
            // MapSize.Small has easily enough tiles to fill the entire background - unless the user sized their window to some extreme aspect ratio
            val mapWidth = stage.width / TileGroupMap.groupHorizontalAdvance
            val mapHeight = stage.height / TileGroupMap.groupSize
            @Pure fun Float.scaleCoord(scale: Float) = ceil(this * scale).toInt().coerceAtLeast(6)
            // These scale values are chosen so that a common 4:3 screen minus taskbar gives the same as MapSize.Small
            val backgroundMapSize = MapSize(mapWidth.scaleCoord(.77f), mapHeight.scaleCoord(1f))

            val newMap = MapGenerator(backgroundMapRuleset, this)
                .generateMap(MapParameters().apply {
                    shape = MapShape.rectangular
                    mapSize = backgroundMapSize
                    type = MapType.pangaea
                    temperatureintensity = .7f
                    waterThreshold = -0.1f // mainly land, gets about 30% water
                    modifyForEasterEgg()
                })

            launchOnGLThread { // for GL context
                ImageGetter.setNewRuleset(backgroundMapRuleset, ignoreIfModsAreEqual = true)
                val mapHolder = EditorMapHolder(
                    this@MainMenuScreen,
                    newMap
                ) {}
                mapHolder.color = mapHolder.color.cpy()
                mapHolder.color.a = 0f
                backgroundStack.add(mapHolder)

                if (backgroundMapExists) {
                    mapHolder.addAction(Actions.sequence(
                        Actions.fadeIn(mapFadeTime),
                        Actions.run { backgroundStack.removeActorAt(1, false) }
                    ))
                } else {
                    backgroundMapExists = true
                    mapHolder.addAction(Actions.fadeIn(mapFirstFadeTime))
                }
            }
        }.apply {
            invokeOnCompletion {
                backgroundMapGenerationJob = null
                backgroundStack.addAction(Actions.sequence(
                    Actions.delay(mapReplaceDelay),
                    Actions.run { startBackgroundMapGeneration() }
                ))
            }
        }
    }

    private fun stopBackgroundMapGeneration() {
        backgroundStack.clearActions()
        val currentJob = backgroundMapGenerationJob
            ?: return
        backgroundMapGenerationJob = null
        if (currentJob.isCancelled) return
        currentJob.cancel()
    }

    private fun resumeGame() {
        if (GUI.isWorldLoaded()) {
            val currentTileSet = GUI.getMap().currentTileSetStrings
            val currentGameSetting = GUI.getSettings()
            if (currentTileSet.tileSetName != currentGameSetting.tileSet ||
                    currentTileSet.unitSetName != currentGameSetting.unitSet) {
                game.removeScreensOfType(WorldScreen::class)
                QuickSave.autoLoadGame(this)
            } else {
                GUI.resetToWorldScreen()
                GUI.getWorldScreen().popups.filterIsInstance<WorldScreenMenuPopup>().forEach(Popup::close)
            }
        } else {
            QuickSave.autoLoadGame(this)
        }
    }

    private fun quickstartNewGame() {
        ToastPopup(Constants.working, this)
        val errorText = "Cannot start game with the default new game parameters!"
        Concurrency.run("QuickStart") {
            val newGame: GameInfo
            // Can fail when starting the game...
            try {
                val gameInfo = GameSetupInfo.fromSettings("Chieftain")
                if (gameInfo.gameParameters.victoryTypes.isEmpty()) {
                    val ruleSet = RulesetCache.getComplexRuleset(gameInfo.gameParameters)
                    gameInfo.gameParameters.victoryTypes.addAll(ruleSet.victories.keys)
                }
                newGame = GameStarter.startNewGame(gameInfo)

            } catch (notAPlayer: UncivShowableException) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(notAPlayer)
                launchOnGLThread { ToastPopup(message, this@MainMenuScreen) }
                return@run
            } catch (_: Exception) {
                launchOnGLThread { ToastPopup(errorText, this@MainMenuScreen) }
                return@run
            }

            // ...or when loading the game
            try {
                game.loadGame(newGame)
            } catch (_: OutOfMemoryError) {
                launchOnGLThread {
                    ToastPopup("Not enough memory on phone to load game!", this@MainMenuScreen)
                }
            } catch (notAPlayer: UncivShowableException) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(notAPlayer)
                launchOnGLThread {
                    ToastPopup(message, this@MainMenuScreen)
                }
            } catch (_: Exception) {
                launchOnGLThread {
                    ToastPopup(errorText, this@MainMenuScreen)
                }
            }
        }
    }

    override fun getCivilopediaRuleset(): Ruleset {
        if (easterEggRuleset != null) return easterEggRuleset!!
        val rulesetParameters = game.settings.lastGameSetup?.gameParameters
        if (rulesetParameters != null) return RulesetCache.getComplexRuleset(rulesetParameters)
        return RulesetCache[BaseRuleset.Civ_V_GnK.fullName]
            ?: throw IllegalStateException("No ruleset found")
    }

    override fun openCivilopedia(link: String) {
        stopBackgroundMapGeneration()
        val ruleset = getCivilopediaRuleset()
        UncivGame.Current.translations.translationActiveMods = ruleset.mods
        ImageGetter.setNewRuleset(ruleset)
        setSkin()
        openCivilopedia(ruleset, link = link)
    }

    override fun recreate(): BaseScreen {
        stopBackgroundMapGeneration()
        return MainMenuScreen()
    }

    override fun resume() {
        startBackgroundMapGeneration()
    }

    // We contain a map...
    override fun getShortcutDispatcherVetoer() = KeyShortcutDispatcherVeto.createTileGroupMapDispatcherVetoer()
}

package com.unciv.ui.screens.mainmenuscreen

import com.badlogic.gdx.Gdx
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
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.tilesets.TileSetCache
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.surroundWithThinCircle
import com.unciv.ui.components.extensions.toLabel
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
import kotlin.math.min


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
        binding: KeyboardBinding,
        function: () -> Unit
    ): Table {
        val table = Table().pad(15f, 30f, 15f, 30f)
        table.background = skinStrings.getUiBackground(
            "MainMenuScreen/MenuButton",
            skinStrings.roundedEdgeRectangleShape,
            skinStrings.skinConfig.baseColor
        )
        table.add(ImageGetter.getImage(icon)).size(50f).padRight(20f)
        table.add(text.toLabel(fontSize = 30, alignment = Align.left)).expand().left().minWidth(200f)
            .padTopDescent()

        table.touchable = Touchable.enabled
        table.onActivation(binding = binding) {
            stopBackgroundMapGeneration()
            function()
        }

        table.pack()
        return table
    }

    init {
        SoundPlayer.initializeForMainMenu()

        val background = skinStrings.getUiBackground("MainMenuScreen/Background", tintColor = clearColor)
        backgroundStack.add(BackgroundActor(background, Align.center))
        stage.addActor(backgroundStack)
        backgroundStack.setFillParent(true)

        // If we were in a mod, some of the resource images for the background map we're creating
        // will not exist unless we reset the ruleset and images
        val baseRuleset = RulesetCache.getVanillaRuleset()
        ImageGetter.setNewRuleset(baseRuleset)

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
        if (game.settings.tileSet in TileSetCache)
            startBackgroundMapGeneration()

        val column1 = Table().apply { defaults().pad(10f).fillX() }
        val column2 = if (singleColumn) column1 else Table().apply { defaults().pad(10f).fillX() }

        if (game.files.autosaves.autosaveExists()) {
            val resumeTable = getMenuButton("Resume","OtherIcons/Resume", KeyboardBinding.Resume)
                { resumeGame() }
            column1.add(resumeTable).row()
        }

        val quickstartTable = getMenuButton("Quickstart", "OtherIcons/Quickstart", KeyboardBinding.Quickstart)
            { quickstartNewGame() }
        column1.add(quickstartTable).row()

        val newGameButton = getMenuButton("Start new game", "OtherIcons/New", KeyboardBinding.StartNewGame)
            { game.pushScreen(NewGameScreen()) }
        column1.add(newGameButton).row()

        val loadGameTable = getMenuButton("Load game", "OtherIcons/Load", KeyboardBinding.MainMenuLoad)
            { game.pushScreen(LoadGameScreen()) }
        column1.add(loadGameTable).row()

        val multiplayerTable = getMenuButton("Multiplayer", "OtherIcons/Multiplayer", KeyboardBinding.Multiplayer)
            { game.pushScreen(MultiplayerScreen()) }
        column2.add(multiplayerTable).row()

        val mapEditorScreenTable = getMenuButton("Map editor", "OtherIcons/MapEditor", KeyboardBinding.MapEditor)
            { game.pushScreen(MapEditorScreen()) }
        column2.add(mapEditorScreenTable).row()

        val modsTable = getMenuButton("Mods", "OtherIcons/Mods", KeyboardBinding.ModManager)
            { game.pushScreen(ModManagementScreen()) }
        column2.add(modsTable).row()

        val optionsTable = getMenuButton("Options", "OtherIcons/Options", KeyboardBinding.MainMenuOptions)
            { openOptionsPopup() }
        optionsTable.onLongPress { openOptionsPopup(withDebug = true) }
        column2.add(optionsTable).row()


        val table = Table().apply { defaults().pad(10f) }
        table.add(column1)
        if (!singleColumn) table.add(column2)
        table.pack()

        val scrollPane = AutoScrollPane(table)
        scrollPane.setFillParent(true)
        stage.addActor(scrollPane)
        table.center(scrollPane)

        globalShortcuts.add(KeyboardBinding.QuitMainMenu) {
            if (hasOpenPopups()) {
                closeAllPopups()
                return@add
            }
            game.popScreen()
        }

        val civilopediaButton = "?".toLabel(fontSize = 48)
            .apply { setAlignment(Align.center) }
            .surroundWithCircle(60f, color = skinStrings.skinConfig.baseColor)
            .apply { actor.y -= 2.5f } // compensate font baseline (empirical)
            .surroundWithCircle(64f, resizeActor = false)
        civilopediaButton.touchable = Touchable.enabled
        // Passing the binding directly to onActivation gives you a size 26 tooltip...
        civilopediaButton.onActivation { openCivilopedia() }
        civilopediaButton.keyShortcuts.add(KeyboardBinding.Civilopedia)
        civilopediaButton.addTooltip(KeyboardBinding.Civilopedia, 30f)
        civilopediaButton.setPosition(30f, 30f)
        stage.addActor(civilopediaButton)

        val rightSideButtons = Table().apply { defaults().pad(10f) }
        val discordButton = ImageGetter.getImage("OtherIcons/Discord")
            .surroundWithCircle(60f, color = skinStrings.skinConfig.baseColor)
            .surroundWithThinCircle(Color.WHITE)
            .onActivation { Gdx.net.openURI("https://discord.gg/bjrB4Xw") }
        rightSideButtons.add(discordButton)

        val githubButton = ImageGetter.getImage("OtherIcons/Github")
            .surroundWithCircle(60f, color = skinStrings.skinConfig.baseColor)
            .surroundWithThinCircle(Color.WHITE)
            .onActivation { Gdx.net.openURI("https://github.com/yairm210/Unciv") }
        rightSideButtons.add(githubButton)
        
        rightSideButtons.pack()
        rightSideButtons.setPosition(stage.width - 30, 30f, Align.bottomRight)
        stage.addActor(rightSideButtons)
        
        
        val versionLabel = "{Version} ${UncivGame.VERSION.text}".toLabel()
        versionLabel.setAlignment(Align.center)
        val versionTable = Table()
        versionTable.background = skinStrings.getUiBackground("MainMenuScreen/Version",
            skinStrings.roundedEdgeRectangleShape, Color.DARK_GRAY.cpy().apply { a=0.7f })
        versionTable.add(versionLabel)
        versionTable.pack()
        versionTable.setPosition(stage.width/2, 10f, Align.bottom)
        versionTable.touchable = Touchable.enabled
        versionTable.onClick {
            val popup = Popup(stage)
            popup.add(aboutTab()).row()
            popup.addCloseButton()
            popup.open()
        }
        stage.addActor(versionTable)
    }
    
    private fun startBackgroundMapGeneration() {
        stopBackgroundMapGeneration()  // shouldn't be necessary as resize re-instantiates this class
        backgroundMapGenerationJob = Concurrency.run("ShowMapBackground") {
            var scale = 1f
            var mapWidth = stage.width / TileGroupMap.groupHorizontalAdvance
            var mapHeight = stage.height / TileGroupMap.groupSize
            if (mapWidth * mapHeight > 3000f) {  // 3000 as max estimated number of tiles is arbitrary (we had typically 721 before)
                scale = mapWidth * mapHeight / 3000f
                mapWidth /= scale
                mapHeight /= scale
                scale = min(scale, 20f)
            }

            val newMap = MapGenerator(backgroundMapRuleset, this)
                .generateMap(MapParameters().apply {
                    shape = MapShape.rectangular
                    mapSize = MapSize.Small
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
                mapHolder.setScale(scale)
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

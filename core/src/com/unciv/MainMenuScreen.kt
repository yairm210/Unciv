package com.unciv

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.multiplayer.MultiplayerScreen
import com.unciv.ui.mapeditor.*
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.pickerscreens.ModManagementScreen
import com.unciv.ui.popup.*
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import kotlinx.coroutines.Job
import kotlinx.coroutines.job

class MainMenuScreen: BaseScreen() {
    private val backgroundTable = Table().apply { background= ImageGetter.getBackground(Color.WHITE) }
    private val singleColumn = isCrampedPortrait()
    private var backgroundWorker: Job? = null

    /** Create one **Main Menu Button** including onClick/key binding
     *  @param text      The text to display on the button
     *  @param icon      The path of the icon to display on the button
     *  @param key       Optional key binding (limited to Char subset of [KeyCharAndCode], which is OK for the main menu)
     *  @param function  Action to invoke when the button is activated
     */
    private fun getMenuButton(
        text: String,
        icon: String,
        key: Char? = null,
        function: () -> Unit
    ): Table {
        val table = Table().pad(15f, 30f, 15f, 30f)
        table.background = ImageGetter.getRoundedEdgeRectangle(ImageGetter.getBlue())
        table.add(ImageGetter.getImage(icon)).size(50f).padRight(30f)
        table.add(text.toLabel().setFontSize(30)).minWidth(200f)

        table.touchable = Touchable.enabled
        val onClickAction = {
            cancelGenerateBackground()
            function()
        }
        table.onClick(onClickAction)

        if (key != null) {
            keyPressDispatcher[key] = onClickAction
            table.addTooltip(key, 32f)
        }

        table.pack()
        return table
    }

    init {
        stage.addActor(backgroundTable)
        backgroundTable.center(stage)

        // If we were in a mod, some of the resource images for the background map we're creating
        // will not exist unless we reset the ruleset and images
        ImageGetter.ruleset = RulesetCache.getVanillaRuleset()

        backgroundWorker = launchCrashHandling("ShowMapBackground") {
            generateBackground(coroutineContext.job)
        }

        val column1 = Table().apply { defaults().pad(10f).fillX() }
        val column2 = if (singleColumn) column1 else Table().apply { defaults().pad(10f).fillX() }

        val autosaveGame = GameSaver.getSave(GameSaver.autoSaveFileName, false)
        if (autosaveGame.exists()) {
            val resumeTable = getMenuButton("Resume","OtherIcons/Resume", 'r')
                { autoLoadGame() }
            column1.add(resumeTable).row()
        }

        val quickstartTable = getMenuButton("Quickstart", "OtherIcons/Quickstart", 'q')
            { quickstartNewGame() }
        column1.add(quickstartTable).row()

        val newGameButton = getMenuButton("Start new game", "OtherIcons/New", 'n')
            { game.setScreen(NewGameScreen(this)) }
        column1.add(newGameButton).row()

        if (GameSaver.getSaves(false).any()) {
            val loadGameTable = getMenuButton("Load game", "OtherIcons/Load", 'l')
                { game.setScreen(LoadGameScreen(this)) }
            column1.add(loadGameTable).row()
        }

        val multiplayerTable = getMenuButton("Multiplayer", "OtherIcons/Multiplayer", 'm')
            { game.setScreen(MultiplayerScreen(this)) }
        column2.add(multiplayerTable).row()

        val mapEditorScreenTable = getMenuButton("Map editor", "OtherIcons/MapEditor", 'e')
            { game.setScreen(MapEditorScreen()) }
        column2.add(mapEditorScreenTable).row()

        val modsTable = getMenuButton("Mods", "OtherIcons/Mods", 'd')
            { game.setScreen(ModManagementScreen()) }
        column2.add(modsTable).row()

        val optionsTable = getMenuButton("Options", "OtherIcons/Options", 'o')
            { this.openOptionsPopup() }
        column2.add(optionsTable).row()


        val table = Table().apply { defaults().pad(10f) }
        table.add(column1)
        if (!singleColumn) table.add(column2)
        table.pack()

        val scrollPane = AutoScrollPane(table)
        scrollPane.setFillParent(true)
        stage.addActor(scrollPane)
        table.center(scrollPane)

        onBackButtonClicked {
            if (hasOpenPopups()) {
                closeAllPopups()
                return@onBackButtonClicked
            }
            ExitGamePopup(this)
        }

        val helpButton = "?".toLabel(fontSize = 32)
            .apply { setAlignment(Align.center) }
            .surroundWithCircle(40f, color = ImageGetter.getBlue())
            .apply { actor.y -= 2.5f } // compensate font baseline (empirical)
            .surroundWithCircle(42f, resizeActor = false)
        helpButton.touchable = Touchable.enabled
        helpButton.onClick {
            cancelGenerateBackground()
            openCivilopedia()
        }
        keyPressDispatcher[Input.Keys.F1] = { openCivilopedia() }
        helpButton.addTooltip(KeyCharAndCode(Input.Keys.F1), 20f)
        helpButton.setPosition(20f, 20f)
        stage.addActor(helpButton)
    }

    private fun generateBackground(job: Job) {
        val newMap = MapGenerator(RulesetCache.getVanillaRuleset(), job)
            .generateMap(MapParameters().apply {
                mapSize = MapSizeNew(MapSize.Small)
                type = MapType.default
                waterThreshold = -0.055f // Gives the same level as when waterThreshold was unused in MapType.default
            })
        if (!job.isActive) return
        var waitingForDisplay = true
        postCrashHandlingRunnable { // for GL context
            if (!job.isActive) return@postCrashHandlingRunnable
            ImageGetter.setNewRuleset(RulesetCache.getVanillaRuleset())
            if (!job.isActive) return@postCrashHandlingRunnable
            val mapHolder = EditorMapHolder(this@MainMenuScreen, newMap) {}
            if (!job.isActive) return@postCrashHandlingRunnable
            backgroundTable.addAction(Actions.sequence(
                Actions.fadeOut(0f),
                Actions.run {
                    backgroundTable.addActor(mapHolder)
                    mapHolder.center(backgroundTable)
                },
                Actions.fadeIn(0.3f)
            ))
            if (backgroundWorker == job)
                backgroundWorker = null
            waitingForDisplay = false
        }
        while(waitingForDisplay) Unit
    }

    private fun cancelGenerateBackground() {
        val worker = backgroundWorker
        backgroundWorker = null
        worker?.cancel()
    }

    private fun autoLoadGame() {
        val loadingPopup = Popup(this)
        loadingPopup.addGoodSizedLabel("Loading...")
        loadingPopup.open()
        launchCrashHandling("autoLoadGame") {
            // Load game from file to class on separate thread to avoid ANR...
            fun outOfMemory() {
                postCrashHandlingRunnable {
                    loadingPopup.close()
                    ToastPopup("Not enough memory on phone to load game!", this@MainMenuScreen)
                }
            }

            var savedGame: GameInfo
            try {
                savedGame = GameSaver.loadGameByName(GameSaver.autoSaveFileName)
            } catch (oom: OutOfMemoryError) {
                outOfMemory()
                return@launchCrashHandling
            } catch (ex: Exception) { // silent fail if we can't read the autosave for any reason - try to load the last autosave by turn number first
                // This can help for situations when the autosave is corrupted
                try {
                    val autosaves = GameSaver.getSaves()
                        .filter { it.name() != GameSaver.autoSaveFileName && it.name().startsWith(GameSaver.autoSaveFileName) }
                    savedGame =
                        GameSaver.loadGameFromFile(autosaves.maxByOrNull { it.lastModified() }!!)
                } catch (oom: OutOfMemoryError) { // The autosave could have oom problems as well... smh
                    outOfMemory()
                    return@launchCrashHandling
                } catch (ex: Exception) {
                    postCrashHandlingRunnable {
                        loadingPopup.close()
                        ToastPopup("Cannot resume game!", this@MainMenuScreen)
                    }
                    return@launchCrashHandling
                }
            }

            postCrashHandlingRunnable { /// ... and load it into the screen on main thread for GL context
                try {
                    game.loadGame(savedGame)
                    dispose()
                } catch (oom: OutOfMemoryError) {
                    outOfMemory()
                }
            }
        }
    }

    private fun quickstartNewGame() {
        ToastPopup("Working...", this)
        val errorText = "Cannot start game with the default new game parameters!"
        launchCrashHandling("QuickStart") {
            val newGame: GameInfo
            // Can fail when starting the game...
            try {
                newGame = GameStarter.startNewGame(GameSetupInfo.fromSettings("Chieftain"))
            } catch (ex: Exception) {
                postCrashHandlingRunnable { ToastPopup(errorText, this@MainMenuScreen) }
                return@launchCrashHandling
            }

            // ...or when loading the game
            postCrashHandlingRunnable {
                try {
                    game.loadGame(newGame)
                } catch (outOfMemory: OutOfMemoryError) {
                    ToastPopup("Not enough memory on phone to load game!", this@MainMenuScreen)
                } catch (ex: Exception) {
                    ToastPopup(errorText, this@MainMenuScreen)
                }
            }
        }
    }

    private fun openCivilopedia() {
        val ruleset =RulesetCache[game.settings.lastGameSetup?.gameParameters?.baseRuleset]
            ?: RulesetCache[BaseRuleset.Civ_V_GnK.fullName]
            ?: return
        game.setScreen(CivilopediaScreen(ruleset, this))
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            cancelGenerateBackground()
            game.setScreen(MainMenuScreen())
        }
    }
}

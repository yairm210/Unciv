package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.ruleset.RulesetCache
import com.unciv.scripting.ScriptingState
import com.unciv.ui.MultiplayerScreen
import com.unciv.ui.mapeditor.*
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.ui.console.ConsoleScreen
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.pickerscreens.ModManagementScreen
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import kotlin.concurrent.thread

class MainMenuScreen: BaseScreen() {
    private val autosave = "Autosave"
    private val backgroundTable = Table().apply { background=ImageGetter.getBackground(Color.WHITE) }
    private val singleColumn = isCrampedPortrait()

    private val consoleScreen: ConsoleScreen
        get() = game.consoleScreen

    private val scriptingState: ScriptingState
        get() = game.scriptingState

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
        keyVisualOnly: Boolean = false,
        function: () -> Unit
    ): Table {
        val table = Table().pad(15f, 30f, 15f, 30f)
        table.background = ImageGetter.getRoundedEdgeRectangle(ImageGetter.getBlue())
        table.add(ImageGetter.getImage(icon)).size(50f).padRight(30f)
        table.add(text.toLabel().setFontSize(30)).minWidth(200f)

        table.touchable = Touchable.enabled
        table.onClick(function)

        if (key != null) {
            if (!keyVisualOnly)
                keyPressDispatcher[key] = function
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
        ImageGetter.ruleset = RulesetCache.getBaseRuleset()

        thread(name = "ShowMapBackground") {
            val newMap = MapGenerator(RulesetCache.getBaseRuleset())
                    .generateMap(MapParameters().apply { mapSize = MapSizeNew(MapSize.Small); type = MapType.default })
            Gdx.app.postRunnable { // for GL context
                ImageGetter.setNewRuleset(RulesetCache.getBaseRuleset())
                val mapHolder = EditorMapHolder(MapEditorScreen(), newMap)
                backgroundTable.addAction(Actions.sequence(
                        Actions.fadeOut(0f),
                        Actions.run {
                            mapHolder.apply {
                                addTiles(this@MainMenuScreen.stage.width, this@MainMenuScreen.stage.height)
                                touchable = Touchable.disabled
                            }
                            backgroundTable.addActor(mapHolder)
                            mapHolder.center(backgroundTable)
                        },
                        Actions.fadeIn(0.3f)
                ))
            }
        }

        val column1 = Table().apply { defaults().pad(10f).fillX() }
        val column2 = if(singleColumn) column1 else Table().apply { defaults().pad(10f).fillX() }

        val autosaveGame = GameSaver.getSave(autosave, false)
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
            { if(stage.actors.none { it is MapEditorMainScreenPopup }) MapEditorMainScreenPopup(this) }
        column2.add(mapEditorScreenTable).row()

        val modsTable = getMenuButton("Mods", "OtherIcons/Mods", 'd')
            { game.setScreen(ModManagementScreen()) }
        column2.add(modsTable).row()

        val optionsTable = getMenuButton("Options", "OtherIcons/Options", 'o')
            { this.openOptionsPopup() }
        column2.add(optionsTable).row()


        val table=Table().apply { defaults().pad(10f) }
        table.add(column1)
        if (!singleColumn) table.add(column2)
        table.pack()

        val scrollPane = AutoScrollPane(table)
        scrollPane.setFillParent(true)
        stage.addActor(scrollPane)
        table.center(scrollPane)

        onBackButtonClicked {
            if(hasOpenPopups()) {
                closeAllPopups()
                return@onBackButtonClicked
            }
            ExitGamePopup(this)
        }

        keyPressDispatcher[Input.Keys.GRAVE] = { game.setConsoleScreen() }
        consoleScreen.closeAction = { game.setScreen(this) }
        scriptingState.gameInfo = null
        scriptingState.civInfo = null
        scriptingState.worldScreen = null
    }


    /** Shows the [Popup] with the map editor initialization options */
    class MapEditorMainScreenPopup(screen: MainMenuScreen):Popup(screen){
        init{
            // Using MainMenuScreen.getMenuButton - normally that would place key bindings into the
            // screen's key dispatcher, but we need them in this Popup's dispatcher instead.
            // Thus the crutch with keyVisualOnly, we assign the key binding here but want
            // The button to install the tooltip handler anyway.

            defaults().pad(10f)

            val tableBackground = ImageGetter.getBackground(colorFromRGB(29, 102, 107))

            val newMapAction = {
                val newMapScreen = NewMapScreen()
                newMapScreen.setDefaultCloseAction(MainMenuScreen())
                screen.game.setScreen(newMapScreen)
                screen.dispose()
            }
            val newMapButton = screen.getMenuButton("New map", "OtherIcons/New", 'n', true, newMapAction)
            newMapButton.background = tableBackground
            add(newMapButton).row()
            keyPressDispatcher['n'] = newMapAction

            val loadMapAction = {
                val loadMapScreen = SaveAndLoadMapScreen(null, false, screen)
                loadMapScreen.setDefaultCloseAction(MainMenuScreen())
                screen.game.setScreen(loadMapScreen)
                screen.dispose()
            }
            val loadMapButton = screen.getMenuButton("Load map", "OtherIcons/Load", 'l', true, loadMapAction)
            loadMapButton.background = tableBackground
            add(loadMapButton).row()
            keyPressDispatcher['l'] = loadMapAction

            add(screen.getMenuButton(Constants.close, "OtherIcons/Close") { close() }
                    .apply { background=tableBackground })
            keyPressDispatcher[KeyCharAndCode.BACK] = { close() }

            open(force = true)
        }
    }


    private fun autoLoadGame() {
        val loadingPopup = Popup(this)
        loadingPopup.addGoodSizedLabel("Loading...")
        loadingPopup.open()
        thread {
            // Load game from file to class on separate thread to avoid ANR...
            fun outOfMemory() {
                Gdx.app.postRunnable {
                    loadingPopup.close()
                    ToastPopup("Not enough memory on phone to load game!", this)
                }
            }

            var savedGame: GameInfo
            try {
                savedGame = GameSaver.loadGameByName(autosave)
            } catch (oom: OutOfMemoryError) {
                outOfMemory()
                return@thread
            } catch (ex: Exception) { // silent fail if we can't read the autosave for any reason - try to load the last autosave by turn number first
                // This can help for situations when the autosave is corrupted
                try {
                    val autosaves = GameSaver.getSaves()
                        .filter { it.name() != autosave && it.name().startsWith(autosave) }
                    savedGame =
                        GameSaver.loadGameFromFile(autosaves.maxByOrNull { it.lastModified() }!!)
                } catch (oom: OutOfMemoryError) { // The autosave could have oom problems as well... smh
                    outOfMemory()
                    return@thread
                } catch (ex: Exception) {
                    Gdx.app.postRunnable {
                        loadingPopup.close()
                        ToastPopup("Cannot resume game!", this)
                    }
                    return@thread
                }
            }

            Gdx.app.postRunnable { /// ... and load it into the screen on main thread for GL context
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
        thread {
            val newGame: GameInfo
            // Can fail when starting the game...
            try {
                newGame = GameStarter.startNewGame(GameSetupInfo.fromSettings("Chieftain"))
            } catch (ex: Exception) {
                Gdx.app.postRunnable { ToastPopup(errorText, this) }
                return@thread
            }

            // ...or when loading the game
            Gdx.app.postRunnable {
                try {
                    game.loadGame(newGame)
                } catch (outOfMemory: OutOfMemoryError) {
                    ToastPopup("Not enough memory on phone to load game!", this)
                } catch (ex: Exception) {
                    ToastPopup(errorText, this)
                }
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(MainMenuScreen())
        }
    }
}

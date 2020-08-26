package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.MultiplayerScreen
import com.unciv.ui.mapeditor.*
import com.unciv.ui.newgamescreen.GameSetupInfo
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.pickerscreens.ModManagementScreen
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

class MainMenuScreen: CameraStageBaseScreen() {
    private val autosave = "Autosave"
    private val backgroundTable = Table().apply { background=ImageGetter.getBackground(Color.WHITE) }

    private fun getTableBlock(text: String, icon: String, function: () -> Unit): Table {
        val table = Table().pad(15f, 30f, 15f, 30f)
        table.background = ImageGetter.getRoundedEdgeTableBackground(ImageGetter.getBlue())
        table.add(ImageGetter.getImage(icon)).size(50f).padRight(30f)
        table.add(text.toLabel().setFontSize(30)).minWidth(200f)
        table.touchable = Touchable.enabled
        table.onClick(function)
        table.pack()
        return table
    }

    init {
        stage.addActor(backgroundTable)
        backgroundTable.center(stage)

        // If we were in a mod, some of the resource images for the background map we're creating
        // will not exist unless we reset the ruleset and images
        ImageGetter.ruleset = RulesetCache.getBaseRuleset()
        ImageGetter.refreshAtlas()

        thread(name = "ShowMapBackground") {
            val newMap = MapGenerator(RulesetCache.getBaseRuleset())
                    .generateMap(MapParameters().apply { size = MapSize.Small; type = MapType.default })
            Gdx.app.postRunnable { // for GL context
                ImageGetter.setNewRuleset(RulesetCache.getBaseRuleset())
                val mapHolder = EditorMapHolder(MapEditorScreen(), newMap)
                backgroundTable.addAction(Actions.sequence(
                        Actions.fadeOut(0f),
                        Actions.run {
                            mapHolder.apply {
                                addTiles(30f)
                                touchable = Touchable.disabled
                                setScale(1f)
                                center(this@MainMenuScreen.stage)
                                layout()
                            }
                            backgroundTable.add(mapHolder).size(stage.width, stage.height)
                        },
                        Actions.fadeIn(0.3f)
                ))

            }
        }

        val column1 = Table().apply { defaults().pad(10f) }
        val column2 = Table().apply { defaults().pad(10f) }
        val autosaveGame = GameSaver.getSave(autosave, false)
        if (autosaveGame.exists()) {
            val resumeTable = getTableBlock("Resume","OtherIcons/Resume") { autoLoadGame() }
            column1.add(resumeTable).padTop(0f).row()
        }

        val quickstartTable = getTableBlock("Quickstart", "OtherIcons/Quickstart") { quickstartNewGame() }
        column1.add(quickstartTable).row()

        val newGameButton = getTableBlock("Start new game", "OtherIcons/New") {
            game.setScreen(NewGameScreen(this))
            dispose()
        }
        column1.add(newGameButton).row()

        if (GameSaver.getSaves(false).any()) {
            val loadGameTable = getTableBlock("Load game", "OtherIcons/Load")
                { game.setScreen(LoadGameScreen(this)) }
            column1.add(loadGameTable).row()
        }

        val multiplayerTable = getTableBlock("Multiplayer", "OtherIcons/Multiplayer")
            { game.setScreen(MultiplayerScreen(this)) }
        column2.add(multiplayerTable).row()

        val mapEditorScreenTable = getTableBlock("Map editor", "OtherIcons/MapEditor")
            { if(stage.actors.none { it is MapEditorMainScreenPopup }) MapEditorMainScreenPopup(this) }
        column2.add(mapEditorScreenTable).row()

        if(game.settings.showModManager) {
            val modsTable = getTableBlock("Mods", "OtherIcons/Mods")
            { game.setScreen(ModManagementScreen()) }
            column2.add(modsTable).row()
        }


        val table=Table().apply { defaults().pad(10f) }
        table.add(column1)
        table.add(column2)
        table.pack()

        val scroll = ScrollPane(table)
        scroll.setSize(table.width, stage.height * 0.98f)
        scroll.center(stage)
        scroll.setOverscroll(false, false)
        stage.addActor(scroll)
    }


    /** Shows the [Popup] with the map editor initialization options */
    class MapEditorMainScreenPopup(screen: MainMenuScreen):Popup(screen){
        init{
            defaults().pad(10f)

            val tableBackground = ImageGetter.getBackground(colorFromRGB(29, 102, 107))

            val newMapButton = screen.getTableBlock("New map", "OtherIcons/New") {
                screen.game.setScreen(NewMapScreen())
                screen.dispose()
            }
            newMapButton.background = tableBackground
            add(newMapButton).row()

            val loadMapButton = screen.getTableBlock("Load map", "OtherIcons/Load") {
                val loadMapScreen = LoadMapScreen(null)
                loadMapScreen.closeButton.isVisible = true
                loadMapScreen.closeButton.onClick {
                    screen.game.setScreen(MainMenuScreen())
                    loadMapScreen.dispose()
                }
                screen.game.setScreen(loadMapScreen)
                screen.dispose()
            }

            loadMapButton.background = tableBackground
            add(loadMapButton).row()

            add(screen.getTableBlock("Close", "OtherIcons/Close") { close() }
                    .apply { background=tableBackground })

            open(force = true)
        }
    }


    private fun autoLoadGame() {
        try {
            game.loadGame(autosave)
            dispose()
        }
        catch (outOfMemory:OutOfMemoryError){
            ResponsePopup("Not enough memory on phone to load game!", this)
        }
        catch (ex: Exception) { // silent fail if we can't read the autosave
            ResponsePopup("Cannot resume game!", this)
        }
    }

    private fun quickstartNewGame() {
        val newGame = GameStarter.startNewGame(GameSetupInfo().apply { gameParameters.difficulty = "Chieftain" })
        game.loadGame(newGame)
    }

}
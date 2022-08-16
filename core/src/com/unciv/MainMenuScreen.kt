package com.unciv

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.UncivShowableException
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSizeNew
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.mapeditor.EditorMapHolder
import com.unciv.ui.mapeditor.MapEditorScreen
import com.unciv.ui.multiplayer.MultiplayerScreen
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.pickerscreens.ModManagementScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.closeAllPopups
import com.unciv.ui.popup.hasOpenPopups
import com.unciv.ui.popup.popups
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.QuickSave
import com.unciv.ui.tutorials.EasterEggRulesets
import com.unciv.ui.tutorials.EasterEggRulesets.modifyForEasterEgg
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.RecreateOnResize
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.setFontSize
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.worldscreen.mainmenu.WorldScreenMenuPopup
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import kotlin.math.min


class MainMenuScreen: BaseScreen(), RecreateOnResize {
    private val backgroundTable = Table().apply { background= ImageGetter.getBackground(Color.WHITE) }
    private val singleColumn = isCrampedPortrait()
    private var easterEggRuleset: Ruleset? = null  // Cache it so the next 'egg' can be found in Civilopedia

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
        table.onActivation(function)

        if (key != null) {
            if (!keyVisualOnly)
                table.keyShortcuts.add(key)
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

        Concurrency.run("ShowMapBackground") {
            var scale = 1f
            var mapWidth = stage.width / TileGroupMap.groupHorizontalAdvance
            var mapHeight = stage.height / TileGroupMap.groupSize
            if (mapWidth * mapHeight > 3000f) {  // 3000 as max estimated number of tiles is arbitrary (we had typically 721 before)
                scale = mapWidth * mapHeight / 3000f
                mapWidth /= scale
                mapHeight /= scale
                scale = min(scale, 20f)
            }

            val baseRuleset = RulesetCache.getVanillaRuleset()
            easterEggRuleset = EasterEggRulesets.getTodayEasterEggRuleset()?.let {
                RulesetCache.getComplexRuleset(baseRuleset, listOf(it))
            }
            val mapRuleset = easterEggRuleset ?: baseRuleset

            val newMap = MapGenerator(mapRuleset)
                    .generateMap(MapParameters().apply {
                        shape = MapShape.rectangular
                        mapSize = MapSizeNew(mapWidth.toInt() + 1, mapHeight.toInt() + 1)
                        type = MapType.default
                        waterThreshold = -0.055f // Gives the same level as when waterThreshold was unused in MapType.default
                        modifyForEasterEgg()
                    })

            launchOnGLThread { // for GL context
                ImageGetter.setNewRuleset(mapRuleset)
                val mapHolder = EditorMapHolder(this@MainMenuScreen, newMap) {}
                mapHolder.setScale(scale)
                backgroundTable.addAction(Actions.sequence(
                        Actions.fadeOut(0f),
                        Actions.run {
                            backgroundTable.addActor(mapHolder)
                            mapHolder.center(backgroundTable)
                        },
                        Actions.fadeIn(0.3f)
                ))
            }
        }

        val column1 = Table().apply { defaults().pad(10f).fillX() }
        val column2 = if (singleColumn) column1 else Table().apply { defaults().pad(10f).fillX() }

        if (game.files.autosaveExists()) {
            val resumeTable = getMenuButton("Resume","OtherIcons/Resume", 'r')
                { resumeGame() }
            column1.add(resumeTable).row()
        }

        val quickstartTable = getMenuButton("Quickstart", "OtherIcons/Quickstart", 'q')
            { quickstartNewGame() }
        column1.add(quickstartTable).row()

        val newGameButton = getMenuButton("Start new game", "OtherIcons/New", 'n')
            { game.pushScreen(NewGameScreen()) }
        column1.add(newGameButton).row()

        if (game.files.getSaves().any()) {
            val loadGameTable = getMenuButton("Load game", "OtherIcons/Load", 'l')
                { game.pushScreen(LoadGameScreen(this)) }
            column1.add(loadGameTable).row()
        }

        val multiplayerTable = getMenuButton("Multiplayer", "OtherIcons/Multiplayer", 'm')
            { game.pushScreen(MultiplayerScreen(this)) }
        column2.add(multiplayerTable).row()

        val mapEditorScreenTable = getMenuButton("Map editor", "OtherIcons/MapEditor", 'e')
            { game.pushScreen(MapEditorScreen()) }
        column2.add(mapEditorScreenTable).row()

        val modsTable = getMenuButton("Mods", "OtherIcons/Mods", 'd')
            { game.pushScreen(ModManagementScreen()) }
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

        globalShortcuts.add(KeyCharAndCode.BACK) {
            if (hasOpenPopups()) {
                closeAllPopups()
                return@add
            }
            game.popScreen()
        }

        val helpButton = "?".toLabel(fontSize = 32)
            .apply { setAlignment(Align.center) }
            .surroundWithCircle(40f, color = ImageGetter.getBlue())
            .apply { actor.y -= 2.5f } // compensate font baseline (empirical)
            .surroundWithCircle(42f, resizeActor = false)
        helpButton.touchable = Touchable.enabled
        helpButton.onActivation { openCivilopedia() }
        helpButton.keyShortcuts.add(Input.Keys.F1)
        helpButton.addTooltip(KeyCharAndCode(Input.Keys.F1), 20f)
        helpButton.setPosition(20f, 20f)
        stage.addActor(helpButton)
    }


    private fun resumeGame() {
        val curWorldScreen = game.worldScreen
        if (curWorldScreen != null) {
            game.resetToWorldScreen()
            ImageGetter.ruleset = game.gameInfo!!.ruleSet
            curWorldScreen.popups.filterIsInstance(WorldScreenMenuPopup::class.java).forEach(Popup::close)
        } else {
            QuickSave.autoLoadGame(this)
        }
    }

    private fun quickstartNewGame() {
        ToastPopup("Working...", this)
        val errorText = "Cannot start game with the default new game parameters!"
        Concurrency.run("QuickStart") {
            val newGame: GameInfo
            // Can fail when starting the game...
            try {
                newGame = GameStarter.startNewGame(GameSetupInfo.fromSettings("Chieftain"))
            } catch (notAPlayer: UncivShowableException) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(notAPlayer)
                launchOnGLThread { ToastPopup(message, this@MainMenuScreen) }
                return@run
            } catch (ex: Exception) {
                launchOnGLThread { ToastPopup(errorText, this@MainMenuScreen) }
                return@run
            }

            // ...or when loading the game
            try {
                game.loadGame(newGame)
            } catch (outOfMemory: OutOfMemoryError) {
                launchOnGLThread {
                    ToastPopup("Not enough memory on phone to load game!", this@MainMenuScreen)
                }
            } catch (notAPlayer: UncivShowableException) {
                val (message) = LoadGameScreen.getLoadExceptionMessage(notAPlayer)
                launchOnGLThread {
                    ToastPopup(message, this@MainMenuScreen)
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    ToastPopup(errorText, this@MainMenuScreen)
                }
            }
        }
    }

    private fun openCivilopedia() {
        val rulesetParameters = game.settings.lastGameSetup?.gameParameters
        val ruleset = easterEggRuleset ?:
            if (rulesetParameters == null)
                RulesetCache[BaseRuleset.Civ_V_GnK.fullName] ?: return
            else RulesetCache.getComplexRuleset(rulesetParameters)
        UncivGame.Current.translations.translationActiveMods = ruleset.mods
        ImageGetter.setNewRuleset(ruleset)
        setSkin()
        game.pushScreen(CivilopediaScreen(ruleset))
    }

    override fun recreate(): BaseScreen = MainMenuScreen()
}

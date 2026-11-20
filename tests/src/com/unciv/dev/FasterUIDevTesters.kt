package com.unciv.dev

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.city.CityFlags
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.HexMath
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.tilesets.TileSetCache
import com.unciv.testing.TestGame
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.clearActivationActions
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.components.widgets.LoadingImage.Style
import com.unciv.ui.popups.AnimatedMenuPopup
import com.unciv.ui.popups.AnimatedMenuPopup.Companion.addContextMenu
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.cityscreen.CityStatsTable
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.overviewscreen.GlobalPoliticsDiagramGroup
import com.unciv.view.GameView
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.random.Random


/**
 *  Container for [IFasterUITester] instances.
 *
 *  Note implementations do not strictly need to live here, one might want to keep them closer to a widget in development - same package or file.
 *  This was tested and works when one moves the interface to the core project.
 */
internal enum class FasterUIDevTesters : IFasterUITester {

    Civilopedia {
        override fun testCreateExample(screen: BaseScreen) = Actor()
        override val testLaunchScreen: (() -> BaseScreen)
            get() = {
                val game = TestGame(forUITesting = true)
                TileSetCache.loadTileSetConfigs()
                CivilopediaScreen(game.ruleset)
            }
    },

    GlobalPoliticsDiagram {
        override fun testCreateExample(screen: BaseScreen): Actor {
            val game = TestGame(forUITesting = true)
            game.makeHexagonalMap(3)
            val civNames = listOf("Rome", "Greece", "France", "Spain", "Lhasa", "Milan")
            val civs = civNames
                .mapNotNull { game.ruleset.nations[it] }
                .map { game.addCiv(it) }

            for ((i, civ) in civs.withIndex()) {
                // civ.isDefeated() is still true, and CS get-relation code runs deep, especially get tribute willingness needs a fully defined capital

                val pos = HexMath.getClockPositionToHexcoord(i * 2).times(3)
                game.addCity(civ, game.tileMap[pos])
                // create random relations
                for ((j, other) in civs.withIndex()) {
                    if (j <= i || Random.nextInt(3) == 0) continue
                    val rng = GameContext(civInfo = civ, otherCiv = other).stateBasedRandom("FasterUIDevTesters.testCreateExample")
                    // Do a makeCivilizationsMeet without gifts, notifications, or war joins
                    val status = DiplomaticStatus.entries.random(rng)
                    civ.diplomacy[other.civID] = diplomacyManagerFactory(civ, other, status)
                    other.diplomacy[civ.civID] = diplomacyManagerFactory(other, civ, status)
                }
            }

            val size = screen.stage.width.coerceAtMost(screen.stage.height) * 0.9f - 10f
            return GlobalPoliticsDiagramGroup(civs, size)
        }

        private fun diplomacyManagerFactory(civ: Civilization, other: Civilization, status: DiplomaticStatus): DiplomacyManager {
            val mgr = DiplomacyManager(civ, other)
            mgr.diplomaticStatus = status
            mgr.setInfluenceWithoutSideEffects(Random.nextDouble(-90.0, 90.0).toFloat())
            mgr.diplomaticModifiers["Test"] = Random.nextDouble(-90.0, 90.0).toFloat()
            return mgr
        }
    },

    LoadingImage {
        override fun testCreateExample(screen: BaseScreen): Actor = Table().apply {
            val testee = LoadingImage(52f, Style(
                circleColor = Color.NAVY,
                loadingColor = Color.SCARLET,
                idleIconColor = Color.CYAN,
                idleImageName = "OtherIcons/Multiplayer",
                minShowTime = 1500))
            defaults().pad(10f).center()
            add(testee).colspan(2).row()
            add(TextButton("Start", BaseScreen.skin).onClick {
                testee.show()
            })
            add(TextButton("Stop", BaseScreen.skin).onClick {
                testee.hide()
            })
            row()
            val check = CheckBox(" animated ", BaseScreen.skin)
            check.isChecked = testee.animated
            check.onChange { testee.animated = check.isChecked }
            add(check).colspan(2)
            pack()
        }
    },

    CityStats {
        override fun testCreateExample(screen: BaseScreen): Actor {
            val game = TestGame(forUITesting = true)
            game.makeHexagonalMap(3)
            val civ = game.addCiv(game.ruleset.nations["Rome"]!!, isPlayer = true)
            game.gameInfo.currentPlayerCiv = civ
            val city = game.addCity(civ, game.getTile(0, 0), initialPopulation = 3)

            // Use existing resource icons for city-level stockpiles, as used by mods.
            for ((name, amount) in listOf("Iron" to 23, "Horses" to -1)) {
                game.ruleset.tileResources[name] = TileResource().apply {
                    this.name = name
                    resourceType = ResourceType.Strategic
                    uniques.add("City-level resource")
                    uniques.add("Stockpiled")
                    setTransients(game.ruleset)
                }
                city.resourceStockpiles[name] = amount
            }
            city.setFlag(CityFlags.WeLoveTheKing, 12)
            city.cityStats.update()

            // Preview the real widget without constructing the full city screen or navigating away.
            val cityScreen = mock(CityScreen::class.java)
            `when`(cityScreen.cityView).thenReturn(GameView(game.gameInfo, civ).getCityView(city))
            `when`(cityScreen.stage).thenReturn(screen.stage)
            `when`(cityScreen.game).thenReturn(screen.game)
            return CityStatsTable(cityScreen).apply {
                update(screen.stage.height - 100f)
            }
        }
    },

    AnimatedMenuPopup {
        override fun testCreateExample(screen: BaseScreen): Actor {
            val button = TextButton("Testing", BaseScreen.skin)
            button.keyShortcuts.add('x')
            button.addTips(screen)
            button.onActivation { ToastPopup("You hit me!", screen) }
            button.keyShortcuts.add(Input.Keys.F10) { Menu(screen, button) }

            val check = CheckBox("Simulate Android", BaseScreen.skin)
            check.isChecked = FasterUIDevelopment.simulateAndroid
            check.onChange {
                FasterUIDevelopment.simulateAndroid = check.isChecked
                button.addTips(screen)
            }

            val wrapper = Table()
            wrapper.defaults().pad(10f).center()
            wrapper.add(check).row()
            wrapper.add(button)
            return wrapper
        }

        @Suppress("RedundantInnerClassModifier") // A private class is forbidden in enum instance
        inner class Menu(private val screen: BaseScreen, actor: Group) : AnimatedMenuPopup(screen.stage, actor) {
            override fun createContentTable() = super.createContentTable()!!.apply {
                add(getButton("Hello?", KeyboardBinding.Civilopedia) {
                    ToastPopup("Hello!", screen)
                })
            }
        }

        private fun TextButton.addTips(screen: BaseScreen) {
            children.filterIsInstance<Image>().forEach { it.remove() }
            clearActivationActions(ActivationTypes.RightClick)
            addTooltip('x')
            addContextMenu { Menu(screen, this) }
        }
    }
    ;
    override fun testGetLabel(): String? = name // maybe use unCamelCase in KeyboardBinding?
}

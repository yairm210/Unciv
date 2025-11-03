package com.unciv.dev

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.HexMath
import com.unciv.testing.TestGame
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.components.widgets.LoadingImage.Style
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.overviewscreen.GlobalPoliticsDiagramGroup
import kotlin.random.Random


/**
 *  Container for [IFasterUITester] instances.
 *
 *  Note implementations do not strictly need to live here, one might want to keep them closer to a widget in development - same package or file.
 *  This was tested and works when one moves the interface to the core project.
 */
enum class FasterUIDevTesters : IFasterUITester {

    GlobalPoliticsDiagram {
        override fun testCreateExample(screen: BaseScreen): Actor {
            val game = TestGame(setupUncivGame = false)
            game.makeHexagonalMap(3)
            val civNames = listOf("Rome", "Greece", "France", "Spain", "Lhasa", "Milan")
            val civs = civNames
                .mapNotNull { game.ruleset.nations[it] }
                .map { game.addCiv(it) }

            for ((i, civ) in civs.withIndex()) {
                // civ.isDefeated() is still true, and CS get-relation code runs deep, especially get tribute willingness needs a fully defined capital
                val pos = HexMath.getClockPositionToHexVector(i * 2).cpy().scl(3f)
                game.addCity(civ, game.tileMap[pos])
                // create random relations
                for ((j, other) in civs.withIndex()) {
                    if (j <= i || Random.nextInt(3) == 0) continue
                    // Do a makeCivilizationsMeet without gifts, notifications, or war joins
                    val status = DiplomaticStatus.entries.random()
                    civ.diplomacy[other.civName] = diplomacyManagerFactory(civ, other, status)
                    other.diplomacy[civ.civName] = diplomacyManagerFactory(other, civ, status)
                }
            }

            val size = screen.stage.width.coerceAtMost(screen.stage.height) * 0.9f - 10f
            return GlobalPoliticsDiagramGroup(civs, size)
        }

        private fun diplomacyManagerFactory(civ: Civilization, other: Civilization, status: DiplomaticStatus): DiplomacyManager {
            val mgr = DiplomacyManager(civ, other.civName)
            mgr.diplomaticStatus = status
            mgr.setInfluenceWithoutSideEffects(Random.nextDouble(-90.0, 90.0).toFloat())
            mgr.diplomaticModifiers["Test"] = Random.nextDouble(-90.0, 90.0).toFloat()
            return mgr
        }
    },

    LoadingImage {
        override fun testGetLabel() = name

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
    ;
    override fun testGetLabel() = name // maybe use unCamelCase in KeyboardBinding?
}

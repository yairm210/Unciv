package com.unciv.uniques

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CityStateType
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.worldscreen.unit.UnitActions
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class UnitUniquesTests {
    private lateinit var game: TestGame

    @Before
    fun initTheWorld() {
        game = TestGame()
    }

    @Test
    fun `Sweden can gift Great Persons to City States`() {
        // when
        game.makeHexagonalMap(1)
        val cityState = game.addCiv(cityState = CityStateType.Cultured)
        val cityStateCapitalTile = game.getTile(Vector2(0f, 0f))
        val cityStateCapital = game.addCity(cityState, cityStateCapitalTile)

        val mainCiv = game.addCiv("Gain [90] Influence with a [Great Person] gift to a City-State",
            isPlayer = true
        )
        game.gameInfo.currentPlayerCiv = mainCiv

        val unitTile = game.getTile(Vector2(1f, 0f))
        cityStateCapital.expansion.takeOwnership(unitTile)

        val greatPerson = game.addUnit("Great Scientist", mainCiv, unitTile)

        // then
        val giftAction = UnitActions.getGiftAction(greatPerson, unitTile)

        assertNotNull("Great Person should have a gift action", giftAction)
    }
}

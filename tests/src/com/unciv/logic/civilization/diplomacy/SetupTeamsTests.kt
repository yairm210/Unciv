package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class SetupTeamsTests {

    private val testGame = TestGame()

    private fun addCiv(defaultUnitTile: Tile? = null) =
        testGame.addCiv().apply { testGame.addUnit("Warrior", this, defaultUnitTile) }

    private lateinit var a: Civilization
    private lateinit var b: Civilization
    private lateinit var c: Civilization

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
        a = addCiv()
        b = addCiv()
        c = addCiv()
    }

    @Test
    fun `unique teamIds are not teammates`() {
        a.teamId = 1
        b.teamId = 2
        assertFalse(a.isTeammate(b))
        assertTrue(a.getTeammates().none())
    }

    @Test
    fun `shared teamId makes teammates`() {
        a.teamId = 1
        b.teamId = 1
        c.teamId = 2
        assertTrue(a.isTeammate(b))
        assertFalse(a.isTeammate(c))
        assertTrue(b in a.getTeammates().toList())
        assertFalse(c in a.getTeammates().toList())
    }

    @Test
    fun `cannot declare war on teammate`() {
        a.teamId = 1
        b.teamId = 1
        a.diplomacyFunctions.makeCivilizationsMeet(b)
        assertFalse(a.getDiplomacyManager(b)!!.canDeclareWar())
    }

    @Test
    fun `declaring war pulls teammates in`() {
        a.teamId = 1
        b.teamId = 1
        c.teamId = 2
        a.diplomacyFunctions.makeCivilizationsMeet(b)
        a.diplomacyFunctions.makeCivilizationsMeet(c)
        b.diplomacyFunctions.makeCivilizationsMeet(c)

        a.getDiplomacyManager(c)!!.declareWar()

        assertTrue(a.isAtWarWith(c))
        assertTrue(b.isAtWarWith(c))
    }

    @Test
    fun `target teammates also join against aggressor`() {
        a.teamId = 1
        b.teamId = 2
        c.teamId = 2
        a.diplomacyFunctions.makeCivilizationsMeet(b)
        a.diplomacyFunctions.makeCivilizationsMeet(c)
        b.diplomacyFunctions.makeCivilizationsMeet(c)

        a.getDiplomacyManager(b)!!.declareWar()

        assertTrue(a.isAtWarWith(b))
        assertTrue(a.isAtWarWith(c))
    }
}

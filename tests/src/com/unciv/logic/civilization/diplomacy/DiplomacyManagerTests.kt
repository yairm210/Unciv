package com.unciv.logic.civilization.diplomacy

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyTurnManager.nextTurn
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.BeliefType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class DiplomacyManagerTests {

    private val testGame = TestGame()

    fun addCiv(cityStateType: String? = null, defaultUnitTile: Tile? = null) = testGame.addCiv(cityStateType = cityStateType).apply { testGame.addUnit("Warrior", this@apply, defaultUnitTile) }
    // We need to add units so they are not considered defeated, since defeated civs are filtered out of knowncivs
    private val a = addCiv()
    private val b = addCiv()
    private val c = addCiv()
    private val d = addCiv()


    private fun meet(civilization: Civilization, otherCivilization: Civilization) {
        civilization.diplomacyFunctions.makeCivilizationsMeet(otherCivilization)
    }

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
    }

    @Test
    fun `getCommonKnownCivs does not include either DiplomacyManagers's civs`() {
        meet(a, b)
        val commonKnownCivs = a.getDiplomacyManager(b)!!.getCommonKnownCivs()

        assertTrue(a !in commonKnownCivs)
        assertTrue(b !in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs includes civs met by both civs`() {
        meet(a,b)
        meet(b,c)
        meet(c,a)
        val commonKnownCivs = a.getDiplomacyManager(b)!!.getCommonKnownCivs()

        assertTrue(c in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs does not include civs met by only one civ`() {
        meet(a,b)
        meet(a,c)
        val commonKnownCivs = a.getDiplomacyManager(b)!!.getCommonKnownCivs()

        assertTrue(c !in commonKnownCivs)
    }

    @Test
    fun getCommonKnownCivsIsEqualForMirroredDiplomacyManagers() {
        meet(a,b)
        meet(a,c)
        meet(b,c)
        meet(a,d)
        meet(b,d)

        assertEquals(
            a.getDiplomacyManager(b)!!.getCommonKnownCivs(),
            b.getDiplomacyManager(a)!!.getCommonKnownCivs()
        )
    }

    @Test
    fun `should have 0 opinion when just met`() {
        // when
        meet(a, b)

        // then
        val opinionOfOtherCiv = a.getDiplomacyManager(b.civName)!!.opinionOfOtherCiv()
        assertEquals(0f, opinionOfOtherCiv)
    }

    @Test
    fun `should change opinion when denuncing`() {
        // given
        meet(a, b)

        // when
        a.getDiplomacyManager(b)!!.denounce()

        // then
        val aOpinionOfB = a.getDiplomacyManager(b.civName)!!.opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a.civName)!!.opinionOfOtherCiv()

        assertEquals(-35f, aOpinionOfB)
        assertEquals(-35f, bOpinionOfA)
    }

    @Test
    fun `should change opinions when liberating city`() {
        // given
        meet(a, b)
        meet(a, c)
        meet(c, b)
        meet(a, d)
        meet(b, d)
        meet(c, d)

        testGame.gameInfo.currentPlayerCiv = addCiv() // otherwise test crashes when puppetying city
        testGame.gameInfo.currentPlayer = testGame.gameInfo.currentPlayerCiv.civName

        val bCity = testGame.addCity(b, testGame.getTile(Vector2.Zero), initialPopulation = 2)
        testGame.addCity(b, testGame.getTile(Vector2(1f, 1f)))  // another city otherwise b is destroyed when bCity is captured
        bCity.puppetCity(c)

        // when
        bCity.liberateCity(a)

        // then
        val aOpinionOfB = a.getDiplomacyManager(b.civName)!!.opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a.civName)!!.opinionOfOtherCiv()
        val cOpinionOfA = c.getDiplomacyManager(a.civName)!!.opinionOfOtherCiv()
        val dOpinionOfA = d.getDiplomacyManager(a.civName)!!.opinionOfOtherCiv()

        assertEquals(0f, aOpinionOfB) // A shouldn't change its opinion of others
        assertEquals(66f, bOpinionOfA) // massive boost, liberated their city
        assertEquals(0f, cOpinionOfA) // city conquering counters liberated city
        assertEquals(6f, dOpinionOfA) // small boost, liberated another civ's city
    }

    @Test
    fun `should change opinions when conquering city`() {
        // given
        meet(a, b)
        meet(a, c)
        meet(c, b)

        testGame.gameInfo.currentPlayerCiv = addCiv() // otherwise test crashes when puppetying city
        testGame.gameInfo.currentPlayer = testGame.gameInfo.currentPlayerCiv.civName

        val bCity = testGame.addCity(b, testGame.getTile(Vector2.Zero), initialPopulation = 2)
        testGame.addCity(b, testGame.getTile(Vector2(1f, 1f)))  // another city otherwise b is destroyed when bCity is captured

        // when
        bCity.puppetCity(a)

        // then
        val aOpinionOfB = a.getDiplomacyManager(b.civName)!!.opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a.civName)!!.opinionOfOtherCiv()
        val cOpinionOfA = c.getDiplomacyManager(a.civName)!!.opinionOfOtherCiv()

        assertEquals(0f, aOpinionOfB) // A shouldn't change its opinion of others
        assertEquals(-85f, bOpinionOfA) // massive penality, conquered their city
        assertEquals(-8f, cOpinionOfA) // warmonging penalty
    }

    @Test
    fun `should make city state friend when over threshold`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)

        // when
        cityState.getDiplomacyManager(a)!!.addInfluence(31f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
    }

    @Test
    fun `should make city state allied when over threshold and no other civ are allied`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)

        // when
        cityState.getDiplomacyManager(a)!!.addInfluence(61f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Ally))
    }

    @Test
    fun `should not make city state allied when over threshold and other civ has more influence`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        meet(b, cityState)
        cityState.getDiplomacyManager(a)!!.addInfluence(70f)

        // when
        cityState.getDiplomacyManager(b)!!.addInfluence(61f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Ally))
        assertTrue(cityState.getDiplomacyManager(b)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
    }

    @Test
    fun `should make city state allied when over threshold and most influencial`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        meet(b, cityState)
        cityState.getDiplomacyManager(a)!!.addInfluence(61f)

        // when
        cityState.getDiplomacyManager(b)!!.addInfluence(70f)

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
        assertTrue(cityState.getDiplomacyManager(b)!!.isRelationshipLevelEQ(RelationshipLevel.Ally))
    }

    @Test
    fun `should make city state angry when at war regardless of previous influence`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        cityState.getDiplomacyManager(a)!!.addInfluence(61f)

        // when
        a.getDiplomacyManager(cityState)!!.declareWar()

        // then
        assertTrue(cityState.getDiplomacyManager(a)!!.isRelationshipLevelEQ(RelationshipLevel.Unforgivable))
        assertEquals(-60f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should gain previous influence in city state after indirect war`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic", testGame.getTile(Vector2.Zero)) // making peace tries to move units around, so we need to initialize their positions
        val e = addCiv(defaultUnitTile = testGame.getTile(Vector2.X))
        meet(e, cityState)
        // we cannot be allied and simoultaneously having a city state declare indirect war on us
        cityState.getDiplomacyManager(e)!!.addInfluence(31f)
        cityState.getDiplomacyManager(e)!!.declareWar(DeclareWarReason(WarType.DefensivePactWar, a))

        // when
        e.getDiplomacyManager(cityState)!!.makePeace()

        // then
        assertTrue(cityState.getDiplomacyManager(e)!!.isRelationshipLevelEQ(RelationshipLevel.Friend))
        assertEquals(31f, cityState.getDiplomacyManager(e)!!.getInfluence())
    }

    @Test
    fun `should degrade influence in city state on next turn`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral
        meet(a, cityState)

        cityState.getDiplomacyManager(a)!!.addInfluence(30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(29f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should degrade influence in hostile city state on next turn`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        cityState.cityStatePersonality = CityStatePersonality.Hostile
        meet(a, cityState)

        cityState.getDiplomacyManager(a)!!.addInfluence(30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(28.5f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should degrade influence in city state when sharing religion on next turn`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral

        meet(a, cityState)

        // to spread religion, need cities
        testGame.addCity(a, testGame.getTile(Vector2.Zero))
        val cityStateCapital = testGame.addCity(cityState, testGame.getTile(Vector2.X), initialPopulation = 2)

        val religion = testGame.addReligion(a)
        val belief = testGame.createBelief(BeliefType.Founder, "[+1 Food] from every [Shrine]")
        religion.addBeliefs(listOf(belief))
        cityStateCapital.religion.addPressure(religion.name, 1000)

        cityState.getDiplomacyManager(a)!!.addInfluence(30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(29.25f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should increase influence in city state when under resting points`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral
        meet(a, cityState)

        cityState.getDiplomacyManager(a)!!.addInfluence(-30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(-29f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should increase influence in city state when under resting points and sharing religion`() {
        // given
        val cityState = addCiv(cityStateType = "Mercantile")
        cityState.cityStatePersonality = CityStatePersonality.Neutral
        meet(a, cityState)

        // to spread religion, need cities
        testGame.addCity(a, testGame.getTile(Vector2.Zero))
        val cityStateCapital = testGame.addCity(cityState, testGame.getTile(Vector2.X), initialPopulation = 2)

        val religion = testGame.addReligion(a)
        val belief = testGame.createBelief(BeliefType.Founder, "[+1 Food] from every [Shrine]")
        religion.addBeliefs(listOf(belief))
        cityStateCapital.religion.addPressure(religion.name, 1000)

        cityState.getDiplomacyManager(a)!!.addInfluence(-30f)

        // when
        cityState.getDiplomacyManager(a)!!.nextTurn()

        // then
        assertEquals(-28.5f, cityState.getDiplomacyManager(a)!!.getInfluence())
    }

    @Test
    fun `should give science for research agreement`() {
        // given
        meet(a, b)

        testGame.addCity(a, testGame.getTile(Vector2.Zero), initialPopulation = 10)
        testGame.addCity(b, testGame.getTile(Vector2.X), initialPopulation = 20)

        val expectedSciencePerTurnCivA = 13 // 10 pop, 3 palace. Smaller than 23 science per turn of civ B (20 pop, 3 palace)
        val turns = 10

        // when
        a.getDiplomacyManager(b)!!.setFlag(DiplomacyFlags.ResearchAgreement, turns)
        b.getDiplomacyManager(a)!!.setFlag(DiplomacyFlags.ResearchAgreement, turns)
        repeat(turns) {
            a.getDiplomacyManager(b)!!.nextTurn()
            b.getDiplomacyManager(a)!!.nextTurn()
        }

        // then
        assertFalse(a.getDiplomacyManager(b)!!.hasFlag(DiplomacyFlags.ResearchAgreement))
        assertFalse(b.getDiplomacyManager(a)!!.hasFlag(DiplomacyFlags.ResearchAgreement))
        assertEquals(expectedSciencePerTurnCivA * turns, a.tech.scienceFromResearchAgreements)
        assertEquals(expectedSciencePerTurnCivA * turns, b.tech.scienceFromResearchAgreements)
    }

}

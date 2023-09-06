package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.diplomacy.DiplomacyConstants
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
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


    private fun meet(civilization: Civilization, otherCivilization: Civilization){
        civilization.diplomacyFunctions.makeCivilizationsMeet(otherCivilization)
    }

    @Before
    fun setUp() {
        testGame.makeHexagonalMap(4)
    }

    @Test
    fun `getCommonKnownCivs does not include either DiplomacyManagers's civs`() {
        meet(a, b)
        val commonKnownCivs = a.getDiplomacyManager(b).getCommonKnownCivs()

        assertTrue(a !in commonKnownCivs)
        assertTrue(b !in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs includes civs met by both civs`() {
        meet(a,b)
        meet(b,c)
        meet(c,a)
        val commonKnownCivs = a.getDiplomacyManager(b).getCommonKnownCivs()

        assertTrue(c in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs does not include civs met by only one civ`() {
        meet(a,b)
        meet(a,c)
        val commonKnownCivs = a.getDiplomacyManager(b).getCommonKnownCivs()

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
            a.getDiplomacyManager(b).getCommonKnownCivs(),
            b.getDiplomacyManager(a).getCommonKnownCivs()
        )
    }

    @Test
    fun `should have 0 opinion when just met`() {
        // when
        meet(a, b)

        // then
        val opinionOfOtherCiv = a.getDiplomacyManager(b.civName).opinionOfOtherCiv()
        assertEquals(0f, opinionOfOtherCiv)
    }

    @Test
    fun `should change opinion when denuncing`() {
        // given
        meet(a, b)

        // when
        a.getDiplomacyManager(b).denounce()

        // then
        val aOpinionOfB = a.getDiplomacyManager(b.civName).opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a.civName).opinionOfOtherCiv()

        assertEquals(DiplomacyConstants.DENOUNCE_PENALTY, aOpinionOfB)
        assertEquals(DiplomacyConstants.DENOUNCE_PENALTY, bOpinionOfA)
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
        val aOpinionOfB = a.getDiplomacyManager(b.civName).opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a.civName).opinionOfOtherCiv()
        val cOpinionOfA = c.getDiplomacyManager(a.civName).opinionOfOtherCiv()
        val dOpinionOfA = d.getDiplomacyManager(a.civName).opinionOfOtherCiv()

        assertEquals(0f, aOpinionOfB) // A shouldn't change its opinion of others
        assertEquals(121f, bOpinionOfA) // massive boost, liberated their city
        assertEquals(0f, cOpinionOfA) // city conquering counters liberated city
        assertEquals(11f, dOpinionOfA) // small boost, liberated another civ's city
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
        val aOpinionOfB = a.getDiplomacyManager(b.civName).opinionOfOtherCiv()
        val bOpinionOfA = b.getDiplomacyManager(a.civName).opinionOfOtherCiv()
        val cOpinionOfA = c.getDiplomacyManager(a.civName).opinionOfOtherCiv()

        assertEquals(0f, aOpinionOfB) // A shouldn't change its opinion of others
        assertEquals(-121f, bOpinionOfA) // massive penality, conquered their city
        assertEquals(-11f, cOpinionOfA) // warmonging penalty
    }

    @Test
    fun `should make city state friend when over threshold`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)

        // when
        cityState.getDiplomacyManager(a).addInfluence(DiplomacyConstants.FRIEND_INFLUENCE_THRESHOLD + 1f)

        // then
        assertTrue(cityState.getDiplomacyManager(a).isRelationshipLevelEQ(RelationshipLevel.Friend))
    }

    @Test
    fun `should make city state allied when over threshold and no other civ are allied`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)

        // when
        cityState.getDiplomacyManager(a).addInfluence(DiplomacyConstants.ALLY_INFLUENCE_MIN_THRESHOLD + 1f)

        // then
        assertTrue(cityState.getDiplomacyManager(a).isRelationshipLevelEQ(RelationshipLevel.Ally))
    }

    @Test
    fun `should not make city state allied when over threshold and other civ has more influence`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        meet(b, cityState)
        cityState.getDiplomacyManager(a).addInfluence(DiplomacyConstants.ALLY_INFLUENCE_MIN_THRESHOLD + 10f)

        // when
        cityState.getDiplomacyManager(b).addInfluence(DiplomacyConstants.ALLY_INFLUENCE_MIN_THRESHOLD + 1f)

        // then
        assertTrue(cityState.getDiplomacyManager(a).isRelationshipLevelEQ(RelationshipLevel.Ally))
        assertTrue(cityState.getDiplomacyManager(b).isRelationshipLevelEQ(RelationshipLevel.Friend))
    }

    @Test
    fun `should make city state allied when over threshold and most influencial`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        meet(b, cityState)
        cityState.getDiplomacyManager(a).addInfluence(DiplomacyConstants.ALLY_INFLUENCE_MIN_THRESHOLD + 1f)

        // when
        cityState.getDiplomacyManager(b).addInfluence(DiplomacyConstants.ALLY_INFLUENCE_MIN_THRESHOLD + 10f)

        // then
        assertTrue(cityState.getDiplomacyManager(a).isRelationshipLevelEQ(RelationshipLevel.Friend))
        assertTrue(cityState.getDiplomacyManager(b).isRelationshipLevelEQ(RelationshipLevel.Ally))
    }

    @Test
    fun `should make city state angry when at war regardless of previous influence`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic")
        meet(a, cityState)
        cityState.getDiplomacyManager(a).addInfluence(DiplomacyConstants.ALLY_INFLUENCE_MIN_THRESHOLD + 1f)

        // when
        a.getDiplomacyManager(cityState).declareWar()

        // then
        assertTrue(cityState.getDiplomacyManager(a).isRelationshipLevelEQ(RelationshipLevel.Unforgivable))
        assertEquals(DiplomacyConstants.MINIMUM_CITY_STATE_INFLUENCE, cityState.getDiplomacyManager(a).getInfluence())
    }

    @Test
    fun `should gain previous influence in city state after indirect war`() {
        // given
        val cityState = addCiv(cityStateType = "Militaristic", testGame.getTile(Vector2.Zero)) // making peace tries to move units around, so we need to initialize their positions
        val e = addCiv(defaultUnitTile = testGame.getTile(Vector2.X))
        meet(e, cityState)
        // we cannot be allied and simoultaneously having a city state declare indirect war on us
        cityState.getDiplomacyManager(e).addInfluence(DiplomacyConstants.FRIEND_INFLUENCE_THRESHOLD + 1f)
        cityState.getDiplomacyManager(e).declareWar(indirectCityStateAttack = true)

        // when
        e.getDiplomacyManager(cityState).makePeace()

        // then
        assertTrue(cityState.getDiplomacyManager(e).isRelationshipLevelEQ(RelationshipLevel.Friend))
        assertEquals(DiplomacyConstants.FRIEND_INFLUENCE_THRESHOLD + 1f, cityState.getDiplomacyManager(e).getInfluence())
    }

}

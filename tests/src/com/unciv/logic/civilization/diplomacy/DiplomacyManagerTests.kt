package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Nation
import com.unciv.testing.GdxTestRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class DiplomacyManagerTests {

    private val mockGameInfo = mockk<GameInfo>()
    private val slot = slot<String>()

    private val testCivilizationNames = arrayListOf("Russia", "America", "Germany", "Greece", "Babylon","Almaty")
    private val civilizations = testCivilizationNames.associateWith { CivilizationInfo(it) }

    private fun meetByName(civilization: String, civilizationToMeet: String) {
        civilizations.getValue(civilization)
            .meetCivilization(civilizations.getValue(civilizationToMeet))
    }

    @Before
    fun setup() {
        // Setup the GameInfo mock

        every { mockGameInfo.getCivilization(capture(slot)) } answers { civilizations.getValue(slot.captured) }
        // Just return the default CivilizationInfo, since the .meetCivilization() includes the tutorial logic and crashes otherwise
        every { mockGameInfo.getCurrentPlayerCivilization() } returns CivilizationInfo()

        for (civ in civilizations.values) {
            civ.gameInfo = mockGameInfo
        }
    }

    @After
    fun tearDown() {
        // Clear the diplomacy
        for (civ in civilizations.values) {
            civ.diplomacy.clear()
        }
    }

    @Test
    fun `getCommonKnownCivs does not include either DiplomacyManagers's civs`() {
        val russia = civilizations.getValue("Russia")
        val america = civilizations.getValue("America")
        meetByName("Russia", "America")

        val commonKnownCivs = russia.getDiplomacyManager(america).getCommonKnownCivs()

        Assert.assertTrue(russia !in commonKnownCivs)
        Assert.assertTrue(america !in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs includes civs met by both civs`() {
        val russia = civilizations.getValue("Russia")
        val america = civilizations.getValue("America")
        meetByName("Russia", "America")
        meetByName("Russia", "Germany")
        meetByName("America", "Germany")

        val commonKnownCivs = russia.getDiplomacyManager(america).getCommonKnownCivs()

        Assert.assertTrue(civilizations.getValue("Germany") in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs does not include civs met by only one civ`() {
        val russia = civilizations.getValue("Russia")
        val america = civilizations.getValue("America")
        meetByName("Russia", "America")
        meetByName("Russia", "Germany")

        val commonKnownCivs = russia.getDiplomacyManager(america).getCommonKnownCivs()

        Assert.assertTrue(civilizations.getValue("Germany") !in commonKnownCivs)
    }

    @Test
    fun `getCommonKnownCivs is equal for mirrored DiplomacyManagers`() {
        val russia = civilizations.getValue("Russia")
        val america = civilizations.getValue("America")
        meetByName("Russia", "America")
        meetByName("Russia", "Germany")
        meetByName("America", "Germany")
        meetByName("Russia", "Greece")
        meetByName("America", "Greece")

        Assert.assertEquals(
            america.getDiplomacyManager(russia).getCommonKnownCivs(),
            russia.getDiplomacyManager(america).getCommonKnownCivs()
        )
    }

    @Test
    fun `militaristic city state grants units`() {
        val almaty = civilizations.getValue("Almaty");
        val testNation = Nation()
        testNation.name = "Almaty"
        testNation.cityStateType = CityStateType.Militaristic
        almaty.nation = testNation
        Assert.assertTrue(almaty.isCityState())
        Assert.assertEquals(almaty.cityStateType, CityStateType.Militaristic)

        // get a friend
        val russiaNation = Nation()
        russiaNation.name = "Russia"
        val russia = civilizations.getValue("Russia")
        russia.nation = russiaNation
        russia.meetCivilization(almaty)
        almaty.meetCivilization(russia)


        var turnsToUnit = 20;
        var unitsGranted = 0;
        val almatyDiplo = almaty.getDiplomacyManager(russia)
        almatyDiplo.onUnitGrant = { unitsGranted += 1 }
        val russiaDiplo = russia.getDiplomacyManager(almaty)
        repeat(turnsToUnit) {
            almatyDiplo.influence = 50f;
            russiaDiplo.influence = 50f;
            russiaDiplo.nextTurn();
            almatyDiplo.nextTurn();
        }
        Assert.assertEquals(1, unitsGranted)

    }

}
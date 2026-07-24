package com.unciv.uniques

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexMath
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueTriggerActivationLimiter
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.fillPlaceholders
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TriggerLimiterTests {
    private val game = TestGame()

    @Before
    fun clearLimiter() {
        UniqueTriggerActivationLimiter.clear()
    }

    @Test
    fun limiterThrowsForFreeBuildingLoop() {
        val building = game.createBuilding()
        val removeUnique = UniqueType.RemoveBuilding.placeholderText.fillPlaceholders(building.name, "in this city")
        building.uniques.add(removeUnique)
        val civ = game.addCiv(freeBuildingUnique(building))
        val ex = Assert.assertThrows(UniqueTriggerActivationLimiter.InfiniteRecursionException::class.java) {
            game.addCity(civ, game.getTile(0, 0))
        }
        val hasWords = ex.message?.run { contains("Gain") && contains("Remove") } == true
        Assert.assertTrue("Exception message should contain both words 'Remove' and 'Gain'", hasWords )
    }

    @Test
    fun limiterThrowsForTriggerLoop() {
        val civ = prepareRecursiveFreeUnitsTest()
        val ex = Assert.assertThrows(UniqueTriggerActivationLimiter.InfiniteRecursionException::class.java) {
            game.addCity(civ, game.getTile(0, 0))
        }
        val hasWords = ex.message?.run { contains("Gain") && contains("appears") } == true
        Assert.assertTrue("Exception message should contain both words 'appears' and 'Gain'", hasWords )
    }

    @Test
    fun limiterDoesNotThrowForDeepLimitedLoop() {
        val max = UniqueTriggerActivationLimiter.maxTriggerRecursionDepth / 3 - 1
        val limitUnique = UniqueType.MaxNumberBuildable.placeholderText.fillPlaceholders(max.toString())
        val civ = prepareRecursiveFreeUnitsTest(limitUnique)
        game.addCity(civ, game.getTile(0, 0)) // Shouldn't throw
        Assert.assertTrue("Recursion depth should have reached ${ max * 3 }", UniqueTriggerActivationLimiter.maxDepth >= max * 3)
    }

    private fun prepareRecursiveFreeUnitsTest(vararg extraUnitUniques: String): Civilization {
        // Since MapUnit.getUniques() contrary to common sense does NOT include BaseUnit uniques,
        // this is a bit convoluted - can't simply place the OneTimeFreeUnit on the units
        // Also, it's important the map has room for all the units until the limit hits
        game.makeHexagonalMap(HexMath.getHexagonalRadiusForArea(UniqueTriggerActivationLimiter.maxTriggerRecursionDepth).toInt() + 1)
        val unitA = game.createBaseUnit("Civilian", *extraUnitUniques)
        val unitB = game.createBaseUnit("Civilian", *extraUnitUniques)
        val unitC = game.createBaseUnit("Civilian", *extraUnitUniques)
        val firstUnitUnique = UniqueType.OneTimeFreeUnit.placeholderText.fillPlaceholders(unitA.name)
        val building = game.createBuilding(firstUnitUnique)
        val civ = game.addCiv(
            freeBuildingUnique(building),
            freeUnitUnique(unitA, unitB),
            freeUnitUnique(unitB, unitC),
            freeUnitUnique(unitC, unitA),
        )
        return civ
    }

    private fun freeBuildingUnique(b: Building) =
        UniqueType.GainFreeBuildings.placeholderText.fillPlaceholders(b.name, "in all cities")
    private fun freeUnitUnique(a: BaseUnit, b: BaseUnit) =
        UniqueType.OneTimeFreeUnit.placeholderText.fillPlaceholders(a.name) +
        " <${UniqueType.TriggerUponGainingUnit.placeholderText.fillPlaceholders(b.name)}>"
}

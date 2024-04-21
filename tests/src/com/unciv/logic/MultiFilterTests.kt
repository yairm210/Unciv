package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.CityFlags
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Conditionals
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class MultiFilterTests {
    private val game = TestGame()
    private val civ = game.addCiv()
    private val city = game.addCity(civ, game.getTile(Vector2.Zero))
    private val stateForConditionals = StateForConditionals(city)

    @Test
    fun testSplitTerms() {
        Assert.assertTrue(MultiFilter.multiFilter("{A} {B}", { it=="A" || it=="B"}))
        Assert.assertFalse(MultiFilter.multiFilter("{A} {B}", { it=="A"}))
        Assert.assertFalse(MultiFilter.multiFilter("{A} {B}", { it=="B"}))
    }

    @Test
    fun testNotTerm() {
        Assert.assertTrue(MultiFilter.multiFilter("non-[B]", { it=="A"}))
        Assert.assertFalse(MultiFilter.multiFilter("non-[A]", { it=="A"}))
    }

    @Test
    fun testSplitNotTerm() {
        Assert.assertTrue(MultiFilter.multiFilter("{non-[A]} {non-[B]}", { it=="C"}))
        Assert.assertFalse(MultiFilter.multiFilter("{non-[A]} {non-[B]}", { it=="A"}))
    }

    @Test
    fun testParameterTypeSplits(){
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("{Wounded} {Barbarian}", game.ruleset))
        Assert.assertEquals(UniqueParameterType.MapUnitFilter.getErrorSeverity("{Wounded} {NONEXISTANTFILTER}", game.ruleset),
            UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique)
    }


    @Test
    fun testParameterNonFilters(){
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("non-[Wounded]", Ruleset()))
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("{non-[Wounded]} {Barbarian}", game.ruleset))
    }

    @Test
    fun testParameterTypeSplitsWorkWithMultipleLevels() {
        // Wounded is part of MapUnitFilter, Melee - BaseUnitFilter, and Land - UnitTypeFilter
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("{Wounded} {Melee} {Land}", game.ruleset))
    }

    @Test
    fun testOrLogic() {
        Assert.assertTrue(MultiFilter.multiFilter("[A] or [B]", { it=="A"}))
        Assert.assertTrue(MultiFilter.multiFilter("[A] or [B]", { it=="B"}))
        Assert.assertFalse(MultiFilter.multiFilter("[A] or [B]", { it=="C"}))
    }

    @Test
    fun testAndNestedInOrLogic() {
        Assert.assertTrue(MultiFilter.multiFilter("[{A} {B}] or [{C} {D}]", { it=="A" || it == "B"}))
        Assert.assertTrue(MultiFilter.multiFilter("[{A} {B}] or [{C} {D}]", { it=="C" || it == "D"}))
        Assert.assertFalse(MultiFilter.multiFilter("[{A} {B}] or [{C} {D}]", { it=="A" || it == "C"}))
    }

    @Test
    fun `test a complete Unique with a complex multi-filter is parsed and validated correctly`() {
        val text = "Only available <if [Colosseum] is constructed in all [non-[{non-[Resisting]} {non-[Razing]} {non-[Coastal]}]] cities>"
        val unique = Unique(text)
        val errors = UniqueValidator(game.ruleset).checkUnique(unique, false, null, false)
        Assert.assertFalse(errors.isNotOK())
    }

    @Before
    fun resetCity() {
        city.isBeingRazed = false
        city.isPuppet = false
    }

    @Test
    fun `test cityFilter combining two non-filters`() {
        val condition = "in [{non-[Puppeted]} {non-[Razing]}] cities"
        val conditional = Unique(condition)
        Assert.assertTrue(Conditionals.conditionalApplies(null, conditional, stateForConditionals))

        city.isBeingRazed = true
        Assert.assertFalse(Conditionals.conditionalApplies(null, conditional, stateForConditionals))

        city.isBeingRazed = false
        city.isPuppet = true
        Assert.assertFalse(Conditionals.conditionalApplies(null, conditional, stateForConditionals))

        city.isBeingRazed = true
        Assert.assertFalse(Conditionals.conditionalApplies(null, conditional, stateForConditionals))
    }

    @Test
    fun `test cityFilter negating a combined filter`() {
        val condition = "in [non-[{Puppeted} {Resisting}]] cities"
        val conditional = Unique(condition)
        Assert.assertTrue(Conditionals.conditionalApplies(null, conditional, stateForConditionals))

        city.isPuppet = true
        Assert.assertTrue(Conditionals.conditionalApplies(null, conditional, stateForConditionals))

        city.isPuppet = false
        city.setFlag(CityFlags.Resistance, 3)
        Assert.assertTrue(Conditionals.conditionalApplies(null, conditional, stateForConditionals))

        city.isPuppet = true
        Assert.assertFalse(Conditionals.conditionalApplies(null, conditional, stateForConditionals))
    }

    @Test
    fun `test cityFilter nesting 'or' logic in 'and' logic`() {
        val condition = "in [{[Puppeted] or [Razing]} {[Coastal] or [non-[Garrisoned]]}] cities"
        val conditional = Unique(condition)
        Assert.assertFalse(Conditionals.conditionalApplies(null, conditional, stateForConditionals)) // only [non-[Garrisoned]] is true

        city.isPuppet = true
        Assert.assertTrue(Conditionals.conditionalApplies(null, conditional, stateForConditionals)) // Puppeted fulfills left of AND, no garrison right

        game.addUnit("Warrior", civ, game.getTile(Vector2.Zero)).fortify()
        Assert.assertFalse(Conditionals.conditionalApplies(null, conditional, stateForConditionals)) // Adding garrison will make right term false
    }
}

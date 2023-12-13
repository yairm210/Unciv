package com.unciv.logic

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class MultiFilterTests {
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
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("{Wounded} {Barbarian}", Ruleset()))
        Assert.assertEquals(UniqueParameterType.MapUnitFilter.getErrorSeverity("{Wounded} {NONEXISTANTFILTER}", Ruleset()),
            UniqueType.UniqueParameterErrorSeverity.PossibleFilteringUnique)
    }


    @Test
    fun testParameterNonFilters(){
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("non-[Wounded]", Ruleset()))
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("{non-[Wounded]} {Barbarian}", Ruleset()))
    }

    @Test
    fun testParameterTypeSplitsWorkWithMultipleLevels() {
        // Wounded is part of MapUnitFilter, Melee - BaseUnitFilter, and Land - UnitTypeFilter
        Assert.assertNull(UniqueParameterType.MapUnitFilter.getErrorSeverity("{Wounded} {Melee} {Land}", Ruleset()))
    }
}

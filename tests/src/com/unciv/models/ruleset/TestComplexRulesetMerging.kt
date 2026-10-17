package com.unciv.models.ruleset

import com.unciv.json.json
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.testing.BaseTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.collections.set


/**
 *  Unit tests for the complex parts of [Ruleset.add()][Ruleset.add]:
 *  - mergeTechnologies
 *  - TODO mergePolicies
 *
 *  ### Notes:
 *  - The snapshot comparison relies on [TechColumn] and [Technology] not overriding [equals].
 */
@RunWith(BaseTestRunner::class)
class TestComplexRulesetMerging {
    //region Helpers
    private fun getGnKRuleset(): Ruleset {
        if (RulesetCache.isEmpty())
            RulesetCache.loadRulesets(noMods = true)
        return RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
    }

    /** Pseudo-load, mirrors the block in Ruleset.load */
    private fun Ruleset.loadTechs(data: String) {
        val techColumns = json().fromJson(Array<TechColumn>::class.java, data)
        for (techColumn in techColumns) {
            this.techColumns.add(techColumn)
            for (tech in techColumn.techs) {
                if (tech.cost == 0) tech.cost = techColumn.techCost
                tech.column = techColumn
                tech.originRuleset = name
                technologies[tech.name] = tech
            }
        }
    }

    /** Create minimal ruleset for tech merge checks with easy notation */
    private fun ruleset(name: String, costBase: Int, vararg cols: Pair<Int, List<String>>, remove: Set<String> = emptySet()) =
        Ruleset().apply {
            this.name = name
            loadTechs(colsJson(name, costBase, *cols))
            modOptions.techsToRemove.addAll(remove)
        }

    /** cols: columnNumber to tech names. Era/costs are derived from [name] and [costBase] so merges are distinguishable. */
    private fun colsJson(name: String, costBase: Int, vararg cols: Pair<Int, List<String>>) =
        cols.joinToString(",", "[", "]") { (nr, techs) ->
            """{"columnNumber":$nr,"era":"$name era $nr","techCost":${costBase + nr},
               "buildingCost":${costBase + nr + 1},"wonderCost":${costBase + nr + 2},
               "techs":[${techs.joinToString(",") { """{"name":"$it","row":1}""" }}]}"""
        }

    /** Everything a merge must not change, with instance identity (list/map equality falls back to identity for these classes) */
    private fun snapshot(ruleset: Ruleset) = Triple(
        ruleset.techColumns.map {
            listOf(it, it.columnNumber, it.era, it.techCost, it.buildingCost, it.wonderCost, it.techs.toList())
        },
        ruleset.technologies.toMap(),
        ruleset.technologies.values.map {
            listOf(it.column, it.row, it.cost, it.originRuleset, it.prerequisites.toList(), it.uniques.toList())
        }
    )

    /** Checks:
     *  - tech columns are numbered zero-based, monotonous and contiguous
     *  - for every column, the backreference from referenced tech to that column is identical
     *  - every tech reference in the columns is identical to looking up that tech by name
     *  - total count of tech references in the columns equals techs in the `technologies` map
     */
    private fun assertConsistent(ruleset: Ruleset) {
        assertEquals(ruleset.techColumns.indices.toList(), ruleset.techColumns.map { it.columnNumber })
        for (col in ruleset.techColumns) for (tech in col.techs) {
            assertSame("back-reference of ${tech.name}", col, tech.column)
            assertSame("map entry of ${tech.name}", tech, ruleset.technologies[tech.name])
        }
        assertEquals(ruleset.technologies.size, ruleset.techColumns.sumOf { it.techs.size })
    }

    /** Merges close to how getComplexRuleset does it (base first, into an empty Ruleset), verifying sources stay untouched */
    private fun mergeAndCheck(base: Ruleset, mod: Ruleset): Ruleset {
        fun snapshot() = listOf(base, mod).map(::snapshot)
        val before = snapshot()
        val result = Ruleset().apply { add(base); add(mod) }
        assertEquals("source rulesets were modified", before, snapshot())
        assertConsistent(result)
        return result
    }

    private fun Ruleset.namesByColumn() = techColumns.map { col -> col.techs.map { it.name } }

    private fun complexTestMod(): Ruleset {
        val modData = """
[
	{
		"columnNumber": -1,
		"era": "Prehistoric era",
		"techCost": 10,
		"buildingCost": 20,
		"wonderCost": 100,
		"techs": [
			{
				"name": "Breathing",
				"row": 6,
				"uniques": ["Starting tech"]
			}
		]
	},
	{
		"columnNumber": 0,
		"era": "Ancient era",
		"techCost": 20,
		"buildingCost": 40,
		"wonderCost": 185,
		"techs": [
			{
				"name": "Agriculture",
				"row": 6,
				"prerequisites": ["Breathing"],
			}
		]
	},
	{
		"columnNumber": 3,
		"era": "Classical era",
		"techCost": 105,
		"buildingCost": 100,
		"wonderCost": 42,
		"techs": [
			{
				"name": "Grooming",
				"row": 3,
				"prerequisites": ["Trapping"]
			}
        ]
    }
]
"""
        return Ruleset().apply {
            name = "Test mod"
            modOptions.techsToRemove = hashSetOf("Rifling", "Renaissance era")
            loadTechs(modData)
        }
    }
    //endregion

    @Test
    fun testComplexMod() {
        val base = getGnKRuleset()
        val mod = complexTestMod()
        val combined = mergeAndCheck(base, mod)
        fun List<TechColumn>.countTechs() = associate { it.columnNumber to it.techs.size }
        val actual = combined.techColumns.groupBy { it.era }.map { it.key to it.value.countTechs() }.toMap()
        val expected = mapOf(
            "Prehistoric era" to mapOf(0 to 1),
            "Ancient era" to mapOf(1 to 1, 2 to 4, 3 to 7), // renumbered due to Prehistoric insertion
            "Classical era" to mapOf(4 to 5, 5 to 5),
            "Medieval era" to mapOf(6 to 4, 7 to 6),
            "Industrial era" to mapOf(8 to 5, 9 to 4), // "Renaissance era" skipped, first column -1 for "Rifling"
            "Modern era" to mapOf(10 to 5, 11 to 4),
            "Atomic era" to mapOf(12 to 4, 13 to 4),
            "Information era" to mapOf(14 to 6, 15 to 5),
            "Future era" to mapOf(16 to 1)
        )
        assertEquals("Tech counts per column per era", expected, actual)
        assertEquals("Tech Agriculture modified", "", combined.technologies["Agriculture"]!!.quote)
        assertEquals("TechColumn in Classical era modified", 42, combined.techColumns[4].wonderCost)
    }

    @Test
    fun incomingColumnsAreRenumberedFromZero() {
        val mod = ruleset("mod", 10, -1 to listOf("a"), 0 to listOf("b"))
        val result = mergeAndCheck(Ruleset(), mod)   // empty base, so both columns take the derive path
        assertEquals(listOf(0, 1), result.techColumns.map { it.columnNumber })
        assertEquals(listOf(-1, 0), mod.techColumns.map { it.columnNumber })  // source keeps its numbers
        assertEquals("mod era -1", result.techColumns[0].era)                 // era survives derive
    }

    @Test
    fun unsortedIncomingColumnsAreMergedInOrder() {
        val base = ruleset("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val mod = ruleset("mod", 200, 3 to listOf("d"), 2 to listOf("c"))  // unsorted in json
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("d")), mergeAndCheck(base, mod).namesByColumn())
    }

    @Test
    fun sameColumnNumberMergesAndIncomingEraAndCostsWin() {
        val base = ruleset("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val mod = ruleset("mod", 200, 1 to listOf("c"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf(listOf("a"), listOf("b", "c")), result.namesByColumn())
        assertEquals("mod era 1", result.techColumns[1].era)
        assertEquals(201, result.techColumns[1].techCost)
    }

    @Test
    fun columnsEmptiedByRemovalAreDroppedAndNewOnesFitByNumber() {
        val base = ruleset("base", 100, 0 to listOf("a"), 1 to listOf("b"), 2 to listOf("c"))
        val mod = ruleset("mod", 200, 1 to listOf("d"), remove = setOf("b"))
        // base after removal has columns 0 and 2 (gap); mod's column 1 belongs between them
        assertEquals(listOf(listOf("a"), listOf("d"), listOf("c")), mergeAndCheck(base, mod).namesByColumn())
    }

    @Test
    fun partiallyPrunedColumnIsClonedNotModified() {
        val base = ruleset("base", 100, 0 to listOf("a", "b"))
        val mod = ruleset("mod", 200, remove = setOf("b"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf(listOf("a")), result.namesByColumn())
        assertNotSame(base.techColumns[0], result.techColumns[0])
        assertNotSame(base.technologies["a"], result.technologies["a"])  // cloned, back-reference differs
        assertEquals(listOf("a", "b"), base.techColumns[0].techs.map { it.name })
    }

    @Test
    fun untouchedColumnsAreSharedNotCloned() {
        val base = ruleset("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val mod = ruleset("mod", 200, 2 to listOf("c"))
        val result = mergeAndCheck(base, mod)
        assertSame(base.techColumns[0], result.techColumns[0])
        assertSame(base.technologies["b"], result.technologies["b"])
        assertSame(mod.technologies["c"], result.technologies["c"])
    }

    @Test
    fun redefinedTechMovesToNewColumnAndModInstanceWins() {
        val base = ruleset("base", 100, 0 to listOf("a"), 1 to listOf("b"), 2 to listOf("c"))
        val mod = ruleset("mod", 200, 0 to listOf("c"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf(listOf("a", "c"), listOf("b")), result.namesByColumn())  // old column 2 emptied and dropped
        assertEquals("mod", result.technologies["c"]!!.originRuleset)
    }

    @Test
    fun clonedRulesetHasConsistentTechs() {
        val base = ruleset("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val copy = base.clone()
        assertConsistent(copy)
        assertEquals(base.namesByColumn(), copy.namesByColumn())
    }

    @Test
    fun builtInRulesetsAreNotModified() {
        val builtIn = getGnKRuleset()
        val mod = ruleset("mod", 1000, 1 to listOf("Extra"), remove = setOf("Agriculture"))
        val before = RulesetCache.values.map(::snapshot)

        val result = Ruleset().apply { add(builtIn); add(mod) }
        assertConsistent(result)
        assertNull(result.technologies["Agriculture"])
        assertNotNull(builtIn.technologies["Agriculture"])

        val complex = RulesetCache.getComplexRuleset(builtIn, listOf(mod))
        assertConsistent(complex)

        assertEquals(before, RulesetCache.values.map(::snapshot))
    }
}

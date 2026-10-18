package com.unciv.models.ruleset

import com.unciv.json.json
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.testing.TestRunnerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.junit.runners.Parameterized.UseParametersRunnerFactory


/**
 *  Unit tests for the complex parts of [Ruleset.add()][Ruleset.add]:
 *  - mergePolicies
 *  - mergeTechnologies
 *
 *  ### TODO
 *  - Remove parameterization once all fixes are stable
 *  - Loader code: victory priorities - test that part?
 *  - Loader code: conflictingLocationPolicy - seems bogus, as it won't apply an merge where it might see those conflicts?
 *    ...Mods conflicting with themselves should be covered by RulesetValidator!
 *  - Loader code: Overwriting last policy name with "$branch Complete" but still register the old name in ruleset.policies -> bull!
 *  - Cover tech column monotony requirement in RulesetValidator
 */
@RunWith(Parameterized::class)
@UseParametersRunnerFactory(TestRunnerFactory::class)
class TestComplexRulesetMerging(
    private val verifyPolicyConsistency: Boolean,
    private val verifyPolicyReferences: Boolean
) {
    companion object {
        @Suppress("unused")
        @Parameters
        @JvmStatic
        fun parameters() = listOf(arrayOf(
            /* verifyPolicyConsistency = */ true,
            /* verifyPolicyReferences = */ false,
        ))
    }

    //region Helpers
    /** semi-dynamic `@Ignore` */
    private fun assumePolicyReferencesVerified() =
        assumeTrue("needs policy merge fix", verifyPolicyReferences)

    private fun getGnKRuleset(): Ruleset {
        if (RulesetCache.isEmpty() || !verifyPolicyReferences) // As long as policy merge is broken, we must load fresh copies
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

    /** Pseudo-load, mirrors the policies block in Ruleset.load.
     *  Except: victory priorities, location-conflict check, forcing "Complete" name on last policy */
    private fun Ruleset.loadPolicies(data: String) {
        for (branch in json().fromJson(Array<PolicyBranch>::class.java, data)) {
            policyBranches[branch.name] = branch
            branch.requires = ArrayList()
            branch.branch = branch
            policies[branch.name] = branch
            for (policy in branch.policies) {
                policy.branch = branch
                policy.originRuleset = this.name
                if (policy.requires == null) policy.requires = arrayListOf(branch.name)
                policies[policy.name] = policy
            }
        }
    }

    /** Create minimal ruleset for tech merge checks with easy notation */
    private fun rulesetWithTechs(
        name: String, costBase: Int,
        vararg cols: Pair<Int, List<String>>,
        remove: Set<String> = emptySet()
    ) = Ruleset().apply {
        this.name = name
        loadTechs(techColJson(name, costBase, *cols))
        modOptions.techsToRemove.addAll(remove)
    }

    /** Create minimal ruleset for policy merge checks with easy notation */
    private fun rulesetWithPolicies(name: String,
        vararg branches: String,
        removePolicies: Set<String> = emptySet(),
        removeBranches: Set<String> = emptySet()
    ) = Ruleset().apply {
        this.name = name
        loadPolicies(branches.joinToString(",", "[", "]"))
        modOptions.policiesToRemove.addAll(removePolicies)
        modOptions.policyBranchesToRemove.addAll(removeBranches)
    }

    /** cols: columnNumber to tech names. Era/costs are derived from [name] and [costBase] so merges are distinguishable. */
    private fun techColJson(name: String, costBase: Int, vararg cols: Pair<Int, List<String>>) =
        cols.joinToString(",", "[", "]") { (nr, techs) ->
            """{"columnNumber":$nr,"era":"$name era $nr","techCost":${costBase + nr},
               "buildingCost":${costBase + nr + 1},"wonderCost":${costBase + nr + 2},
               "techs":[${techs.joinToString(",") { """{"name":"$it","row":1}""" }}]}"""
        }

    /** Members are given in order, the last one is the finisher and must be named "<branch> Complete" like in the real json */
    private fun branchJson(name: String, vararg members: String) =
        """{"name":"$name","era":"Ancient era","uniques":["[+1 Culture] [in capital]"],
            "policies":[${members.withIndex().joinToString(",") { (i, m) ->
            """{"name":"$m","row":1,"column":${i + 1}}""" }}]}"""

    private data class TechColumnSnapshot(
        val col: TechColumn,
        val columnNumber: Int = col.columnNumber,
        val techs: List<Technology> = col.techs.toList(),
        val era: String = col.era,
        val techCost: Int = col.techCost,
        val buildingCost: Int = col.buildingCost,
        val wonderCost: Int = col.wonderCost
    )
    private data class TechSnapshot(
        val tech: Technology,
        val column: TechColumn? = tech.column,
        val row: Int = tech.row,
        val cost: Int = tech.cost,
        val originRuleset: String = tech.originRuleset,
        val prerequisites: List<String> = tech.prerequisites.toList(),
        val uniques: List<String> = tech.uniques.toList()
    )

    /** Identity-based snapshot: instances, list contents, map entries, back-references */
    private inner class Snapshot(ruleset: Ruleset, val name: String = ruleset.name) {
        val branches = ruleset.policyBranches.values.toList()
        val branchMembers = ruleset.policyBranches.values.map { it.policies.toList() }
        val policyEntries = ruleset.policies.entries.map { it.key to it.value }
        val policyBranchRefs = ruleset.policies.values.map { it.branch }
        val policyRequires = ruleset.policies.values.map { it.requires?.toList() }
        val techColumns = ruleset.techColumns.map { TechColumnSnapshot(it) }
        val technologies = ruleset.technologies.values.toList()
        val techProperties = ruleset.technologies.values.map { TechSnapshot(it) }

        fun assertUnchanged(ruleset: Ruleset) {
            val now = Snapshot(ruleset)
            if (verifyPolicyReferences) {
                assertSameItems("$name ruleset modified: branches", branches, now.branches)
                assertEquals("$name ruleset modified: branch count", branchMembers.size, now.branchMembers.size)
                for (i in branchMembers.indices)
                    assertSameItems("$name ruleset modified: members of ${branches[i].name}", branchMembers[i], now.branchMembers[i])
                assertEquals("$name ruleset modified: policies keys", policyEntries.map { it.first }, now.policyEntries.map { it.first })
                assertSameItems("$name ruleset modified: policies values", policyEntries.map { it.second }, now.policyEntries.map { it.second })
                assertSameItems("$name ruleset modified: policy.branch", policyBranchRefs, now.policyBranchRefs)
                assertEquals("$name ruleset modified: policy.requires", policyRequires, now.policyRequires)
            }
            assertEquals("$name ruleset modified: tech columns", techColumns, now.techColumns)
            assertEquals("$name ruleset modified: tech properties", techProperties, now.techProperties)
            assertSameItems("$name ruleset modified: technologies", technologies, now.technologies)
            assertSameItems("$name ruleset modified: technologies column references", technologies.map { it.column }, now.technologies.map { it.column })
            for ((index, col) in techColumns.withIndex())
                assertSameItems("$name ruleset modified: tech column ${col.columnNumber} tech references", col.techs, now.techColumns[index].techs)
        }
    }

    private fun <T> assertSameItems(message: String, expected: List<T>, actual: List<T>) {
        assertEquals("$message (size)", expected.size, actual.size)
        for (i in expected.indices) assertSame("$message [$i]", expected[i], actual[i])
    }

    private fun snapshot(vararg ruleset: Ruleset) = snapshot(ruleset.asIterable())
    private fun snapshot(rulesets: Iterable<Ruleset>) = rulesets.associate { it.name to Snapshot(it) }
    private fun Map<String, Snapshot>.verifySnapshot(vararg ruleset: Ruleset) = verifySnapshot(ruleset.asIterable())
    private fun Map<String, Snapshot>.verifySnapshot(rulesets: Iterable<Ruleset>) {
        for (now in rulesets) {
            this[now.name]!!.assertUnchanged(now)
        }
    }

    /** Checks:
     *  - tech columns are numbered zero-based, monotonous and contiguous
     *  - for every column, the backreference from referenced tech to that column is identical
     *  - every tech reference in the columns is identical to looking up that tech by name
     *  - total count of tech references in the columns equals techs in the `technologies` map
     */
    private fun assertConsistent(ruleset: Ruleset) {
        if (verifyPolicyConsistency) {
            for ((key, policy) in ruleset.policies) {
                assertEquals("policy map key and name", key, policy.name)
                if (verifyPolicyReferences)
                    assertSame("branch of ${policy.name} must be registered", policy.branch, ruleset.policyBranches[policy.branch.name])
                if (policy.name != policy.branch.name)
                    assertTrue("${policy.name} must be listed in ${policy.branch.name}", policy.branch.policies.any { it === policy })
            }
            for ((key, branch) in ruleset.policyBranches) {
                assertEquals("policy branch map key and name", key, branch.name)
                if (!verifyPolicyReferences) continue
                assertSame("branch entry of ${branch.name} in policies", branch, ruleset.policies[branch.name])
                for (member in branch.policies) {
                    assertSame("member ${member.name} in policies", member, ruleset.policies[member.name])
                    assertSame("branch of member ${member.name}", branch, member.branch)
                }
            }
        }
        for ((key, tech) in ruleset.technologies)
            assertEquals("tech map key and name", key, tech.name)
        assertEquals("tech columns should be numbered zero-based, monotonous and contiguous", ruleset.techColumns.indices.toList(), ruleset.techColumns.map { it.columnNumber })
        for (col in ruleset.techColumns) for (tech in col.techs) {
            assertSame("back-reference of ${tech.name}", col, tech.column)
            assertSame("tech column ${col.columnNumber} entry of ${tech.name}", tech, ruleset.technologies[tech.name])
        }
        assertEquals("total count of tech references in the columns equals techs in the `technologies` map", ruleset.technologies.size, ruleset.techColumns.sumOf { it.techs.size })
    }

    /** Merges close to how getComplexRuleset does it (into an empty Ruleset) */
    private fun merge(vararg ruleset: Ruleset): Ruleset {
        val result = Ruleset()
        for (r in ruleset) result.add(r)
        return result
    }

    /** Merges close to how getComplexRuleset does it (base first, into an empty Ruleset), verifying sources stay untouched */
    private fun mergeAndCheck(base: Ruleset, mod: Ruleset): Ruleset {
        val before = snapshot(base, mod)
        val result = merge(base, mod)
        before.verifySnapshot(base, mod)
        assertConsistent(result)
        return result
    }

    private fun Ruleset.techNamesByColumn() = techColumns.map { col -> col.techs.map { it.name } }
    private fun Ruleset.memberNames(branch: String) = policyBranches[branch]!!.policies.map { it.name }

    private fun complexTestMod(): Ruleset {
        val modTechData = """
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
        val modPolicyData = """
[
    {
        "name": "Patronage",
        "era": "Renaissance era",
        "policies": [
            {
                "name": "Nepotism",
                "row": 2,
                "column": 2
            },
        ]
    }
]
"""
        return Ruleset().apply {
            name = "Test mod"
            modOptions.policyBranchesToRemove = hashSetOf("Honor", "Order", "Autocracy", "Freedom")
            modOptions.policiesToRemove = hashSetOf("[Piety] branch", "Educated Elite", "Commerce Complete")
            modOptions.techsToRemove = hashSetOf("Rifling", "Renaissance era")
            loadPolicies(modPolicyData)
            loadTechs(modTechData)
        }
    }
    //endregion

    /** Verifies our [assertConsistent] helper is OK */
    @Test
    fun builtInIsConsistent() {
        assertConsistent(getGnKRuleset())
    }

    @Test
    fun testComplexMod() {
        val base = getGnKRuleset()
        val mod = complexTestMod()
        val combined = mergeAndCheck(base, mod)
        fun List<TechColumn>.countTechs() = associate { it.columnNumber to it.techs.size }
        val actualTechs = combined.techColumns.groupBy { it.era }.map { it.key to it.value.countTechs() }.toMap()
        val expectedTechs = mapOf(
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
        val actualPolicies = combined.policyBranches.map { it.key to it.value.policies.map { p -> p.name } }.toMap()
        val expectedPolicies = mapOf(
            "Tradition" to listOf("Aristocracy", "Legalism", "Oligarchy", "Landed Elite", "Monarchy", "Tradition Complete"),
            "Liberty" to listOf("Republic", "Citizenship", "Collective Rule", "Representation", "Meritocracy", "Liberty Complete"),
            "Piety" to listOf(),
            "Patronage" to listOf("Nepotism"),
            "Commerce" to listOf("Naval Tradition", "Trade Unions", "Merchant Navy", "Mercantilism", "Protectionism"),
            "Rationalism" to listOf("Secularism", "Humanism", "Free Thought", "Sovereignty", "Scientific Revolution", "Rationalism Complete")
        )
        assertEquals("Tech counts per column per era", expectedTechs, actualTechs)
        assertEquals("Tech Agriculture modified", "", combined.technologies["Agriculture"]!!.quote)
        assertEquals("TechColumn in Classical era modified", 42, combined.techColumns[4].wonderCost)
        assertEquals("Policies per branch", expectedPolicies, actualPolicies)
        assertEquals("Patronage branch era modified", "Renaissance era", combined.policyBranches["Patronage"]!!.era)
    }

    //region Policy-specific tests
    @Test
    fun removedBranchLeavesNoTraceInPolicies() {
        assumePolicyReferencesVerified()
        val base = rulesetWithPolicies("base", branchJson("A", "a1", "A Complete"), branchJson("B", "b1", "B Complete"))
        val mod = rulesetWithPolicies("mod", removeBranches = setOf("B"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf("A"), result.policyBranches.keys.toList())
        assertTrue(result.policies.values.none { it.branch.name == "B" })   // catches the stale branch entry
        assertNull(result.policies["B"])
    }

    @Test
    fun removingPolicyOfBaseBranchDoesNotModifyBase() {
        assumePolicyReferencesVerified()
        val base = rulesetWithPolicies("base", branchJson("A", "a1", "a2", "A Complete"))
        val mod = rulesetWithPolicies("mod", removePolicies = setOf("a1"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf("a2", "A Complete"), result.memberNames("A"))
        assertNull(result.policies["a1"])
        assertEquals(listOf("a1", "a2", "A Complete"), base.memberNames("A"))
    }

    @Test
    fun modsOwnPolicyIsNotRemovedByItsOwnRemovalList() {
        assumePolicyReferencesVerified()
        // Removals apply to what existed before the mod, so the mod can remove and re-add
        val base = rulesetWithPolicies("base", branchJson("A", "a1", "A Complete"))
        val mod = rulesetWithPolicies("mod", branchJson("C", "c1", "c2", "C Complete"), removePolicies = setOf("c1"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf("c1", "c2", "C Complete"), result.memberNames("C"))
        assertNotNull(result.policies["c1"])
    }

    @Test
    fun overridingBranchReplacesAllItsOldPolicies() {
        val base = rulesetWithPolicies("base", branchJson("A", "a1", "A Complete"), branchJson("B", "b1", "B Complete"))
        val mod = rulesetWithPolicies("mod", branchJson("A", "x1", "A Complete"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf("x1", "A Complete"), result.memberNames("A"))
        if (!verifyPolicyReferences) return
        assertNull(result.policies["a1"])
        assertSame(mod.policyBranches["A"], result.policyBranches["A"])
        assertSame(base.policyBranches["B"], result.policyBranches["B"])
    }

    @Test
    fun untouchedBranchesStayShared() {
        val base = rulesetWithPolicies("base", branchJson("A", "a1", "A Complete"), branchJson("B", "b1", "B Complete"))
        val mod = rulesetWithPolicies("mod", removePolicies = setOf("a1"))
        val result = mergeAndCheck(base, mod)
        assertSame(base.policyBranches["B"], result.policyBranches["B"])
        assertSame(base.policies["b1"], result.policies["b1"])
        if (verifyPolicyReferences)
            assertNotSame(base.policyBranches["A"], result.policyBranches["A"])   // pruned branch is a copy
    }

    @Test
    fun builtInRulesetsAreNotModifiedByPolicyRemoval() {
        assumePolicyReferencesVerified()
        val builtIn = getGnKRuleset()
        val before = snapshot(RulesetCache.values)
        val policyMod = rulesetWithPolicies("mod", removePolicies = setOf("Aristocracy"), removeBranches = setOf("Piety"))

        val result = merge(builtIn, policyMod)
        assertConsistent(result)
        assertNull(result.policies["Aristocracy"])
        assertNull(result.policyBranches["Piety"])
        assertNotNull(builtIn.policies["Aristocracy"])

        val complex = RulesetCache.getComplexRuleset(builtIn, listOf(policyMod))
        assertConsistent(complex)

        before.verifySnapshot(RulesetCache.values)
    }
    //endregion

    //region Tech-specific tests
    @Test
    fun incomingColumnsAreRenumberedFromZero() {
        val mod = rulesetWithTechs("mod", 10, -1 to listOf("a"), 0 to listOf("b"))
        val result = mergeAndCheck(Ruleset(), mod)   // empty base, so both columns take the derive path
        assertEquals(listOf(0, 1), result.techColumns.map { it.columnNumber })
        assertEquals(listOf(-1, 0), mod.techColumns.map { it.columnNumber })  // source keeps its numbers
        assertEquals("mod era -1", result.techColumns[0].era)                 // era survives derive
    }

    @Test
    fun unsortedIncomingColumnsAreMergedInOrder() {
        val base = rulesetWithTechs("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val mod = rulesetWithTechs("mod", 200, 3 to listOf("d"), 2 to listOf("c"))  // unsorted in json
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c"), listOf("d")), mergeAndCheck(base, mod).techNamesByColumn())
    }

    @Test
    fun sameColumnNumberMergesAndIncomingEraAndCostsWin() {
        val base = rulesetWithTechs("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val mod = rulesetWithTechs("mod", 200, 1 to listOf("c"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf(listOf("a"), listOf("b", "c")), result.techNamesByColumn())
        assertEquals("mod era 1", result.techColumns[1].era)
        assertEquals(201, result.techColumns[1].techCost)
    }

    @Test
    fun columnsEmptiedByRemovalAreDroppedAndNewOnesFitByNumber() {
        val base = rulesetWithTechs("base", 100, 0 to listOf("a"), 1 to listOf("b"), 2 to listOf("c"))
        val mod = rulesetWithTechs("mod", 200, 1 to listOf("d"), remove = setOf("b"))
        // base after removal has columns 0 and 2 (gap); mod's column 1 belongs between them
        assertEquals(listOf(listOf("a"), listOf("d"), listOf("c")), mergeAndCheck(base, mod).techNamesByColumn())
    }

    @Test
    fun partiallyPrunedColumnIsClonedNotModified() {
        val base = rulesetWithTechs("base", 100, 0 to listOf("a", "b"))
        val mod = rulesetWithTechs("mod", 200, remove = setOf("b"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf(listOf("a")), result.techNamesByColumn())
        assertNotSame(base.techColumns[0], result.techColumns[0])
        assertNotSame(base.technologies["a"], result.technologies["a"])  // cloned, back-reference differs
        assertEquals(listOf("a", "b"), base.techColumns[0].techs.map { it.name })
    }

    @Test
    fun untouchedColumnsAreSharedNotCloned() {
        val base = rulesetWithTechs("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val mod = rulesetWithTechs("mod", 200, 2 to listOf("c"))
        val result = mergeAndCheck(base, mod)
        assertSame(base.techColumns[0], result.techColumns[0])
        assertSame(base.technologies["b"], result.technologies["b"])
        assertSame(mod.technologies["c"], result.technologies["c"])
    }

    @Test
    fun redefinedTechMovesToNewColumnAndModInstanceWins() {
        val base = rulesetWithTechs("base", 100, 0 to listOf("a"), 1 to listOf("b"), 2 to listOf("c"))
        val mod = rulesetWithTechs("mod", 200, 0 to listOf("c"))
        val result = mergeAndCheck(base, mod)
        assertEquals(listOf(listOf("a", "c"), listOf("b")), result.techNamesByColumn())  // old column 2 emptied and dropped
        assertEquals("mod", result.technologies["c"]!!.originRuleset)
    }

    @Test
    fun clonedRulesetHasConsistentTechs() {
        val base = rulesetWithTechs("base", 100, 0 to listOf("a"), 1 to listOf("b"))
        val copy = base.clone()
        assertConsistent(copy)
        assertEquals(base.techNamesByColumn(), copy.techNamesByColumn())
    }

    @Test
    fun builtInRulesetsAreNotModifiedByTechRemoval() {
        val builtIn = getGnKRuleset()
        val techMod = rulesetWithTechs("tech mod", 1000, 1 to listOf("Extra"), remove = setOf("Agriculture"))
        val before = snapshot(RulesetCache.values)

        val result = merge(builtIn, techMod)
        assertConsistent(result)
        assertNull(result.technologies["Agriculture"])
        assertNotNull(builtIn.technologies["Agriculture"])

        val complex = RulesetCache.getComplexRuleset(builtIn, listOf(techMod))
        if (verifyPolicyReferences)
            assertConsistent(complex)

        before.verifySnapshot(RulesetCache.values)
    }
    //endregion
}

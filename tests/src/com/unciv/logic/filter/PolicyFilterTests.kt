package com.unciv.logic.filter

import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Ruleset
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [Policy][com.unciv.models.ruleset.Policy] filters*/
@RunWith(GdxTestRunner::class)
class PolicyFilterTests {
    private lateinit var game: TestGame
    private lateinit var civ: Civilization

    @Before
    fun initTheWorld() {
        setupModdedGame()
    }
    
    @Test
    fun testPolicyMatchesFilter() {
        // Don't use a fake Policy without a branch, the policyFilter would stumble over a lateinit.
        val taggedPolicyBranch = game.createPolicyBranch("Some marker")
        val polices = listOf(
            "Tradition", "Aristocracy", "Legalism", "Oligarchy", "Landed Elite", "Monarchy",
            "Liberty", "Citizenship", "Honor", "Piety"
        ).mapNotNull { game.ruleset.policies[it] } + taggedPolicyBranch
        for (policy in polices) {
            civ.policies.freePolicies++
            civ.policies.adopt(policy)
        }

        val filters = listOf(
            "Tradition Complete" to (listOf("Tradition Complete") to 1), // Completion policies are their own separate policies
            "Tradition" to (listOf("Tradition") to 1), // Should just be the starter by itself
            "[Tradition] branch" to ( // Should include the starter and completion policy, along with all policies in its branch
                listOf("Tradition", "Tradition Complete", "Aristocracy", "Legalism", "Oligarchy", "Landed Elite", "Monarchy") to 7),
            "Liberty Complete" to (emptyList<String>() to 0), // Should not include completion branches that have not been completed
            "[Liberty] branch" to (listOf("Liberty", "Citizenship") to 2), // Should only include polices actually gotten
            "Military Tradition" to (emptyList<String>() to 0),
            "Some marker" to (listOf(taggedPolicyBranch.name) to 1) // Should include uniques of policies
        )
        val failures = ArrayList<String>()
        for (test in filters) {
            val filtered = civ.policies.getAdoptedPoliciesMatching(test.first)
                .toList()
            try {
                Assert.assertTrue(filtered.map { it.name }.containsAll(test.second.first))
                Assert.assertTrue(filtered.size == test.second.second)
            }
            catch (_: AssertionError) {
                failures.add("Filter: ${test.first}\nExpected result: ${test.second.first}, expected size: ${test.second.second}\n" +
                    "Result: $filtered, size: ${filtered.size}\n")
            }
        }
        if (failures.any()) {
            println(failures.joinToString("\n"))
            throw AssertionError()
        }
        Assert.assertEquals(civ.getCompletedPolicyBranchesCount(), 2) // Tradion and taggedPolicyBranch
    }

    private fun setupModdedGame(): Ruleset {
        game = TestGame()
        game.makeHexagonalMap(3)
        civ = game.addCiv()
        return game.ruleset
    }
}

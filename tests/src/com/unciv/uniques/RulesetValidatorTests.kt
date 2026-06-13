package com.unciv.uniques

import com.unciv.models.ruleset.Building
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class RulesetValidatorTests {

    @Test
    fun `ruleset validator warns about spy name collision with ruleset object`() {
        val game = TestGame()
        game.ruleset.buildings["Park"] = Building().apply {
            name = "Park"
            originRuleset = "Test mod"
            uniques.add("Unbuildable")
        }
        game.ruleset.nations.values.first().spyNames = arrayListOf("Park")

        val errors = game.ruleset.getErrorList()

        assertTrue(errors.any {
            it.errorSeverityToReport == RulesetErrorSeverity.WarningOptionsOnly
                && it.text.contains("\"Park\"")
                && it.text.contains("Building")
                && it.text.contains("Nation.spyNames")
        })
    }

    @Test
    fun `ruleset validator warns about non ruleset object name collisions`() {
        val game = TestGame()
        game.ruleset.name = "Test mod"
        game.ruleset.buildings["Park"] = Building().apply {
            name = "Park"
            originRuleset = "Test mod"
            uniques.add("Unbuildable")
        }
        game.ruleset.religions.add("Park")

        val errors = game.ruleset.getErrorList()

        assertTrue(errors.any {
            it.errorSeverityToReport == RulesetErrorSeverity.WarningOptionsOnly
                && it.text.contains("\"Park\"")
                && it.text.contains("Building")
                && it.text.contains("Religion")
        })
    }

    @Test
    fun `ruleset validator warns when mod name collision has empty origin`() {
        val game = TestGame()
        game.ruleset.name = ""
        game.ruleset.buildings["Park"] = Building().apply {
            name = "Park"
            originRuleset = BaseRuleset.Civ_V_GnK.fullName
            uniques.add("Unbuildable")
        }
        game.ruleset.religions.add("Park")

        val errors = game.ruleset.getErrorList()

        assertTrue(errors.any {
            it.errorSeverityToReport == RulesetErrorSeverity.WarningOptionsOnly
                && it.text.contains("\"Park\"")
                && it.text.contains("Building")
                && it.text.contains("Religion")
        })
    }

    @Test
    fun `ruleset validator warns about leader name collisions`() {
        val game = TestGame()
        game.ruleset.buildings["Park"] = Building().apply {
            name = "Park"
            originRuleset = "Test mod"
            uniques.add("Unbuildable")
        }
        game.ruleset.nations.values.first().leaderName = "Park"

        val errors = game.ruleset.getErrorList()

        assertTrue(errors.any {
            it.errorSeverityToReport == RulesetErrorSeverity.WarningOptionsOnly
                && it.text.contains("\"Park\"")
                && it.text.contains("Building")
                && it.text.contains("Nation.leaderName")
        })
    }

    @Test
    fun `ruleset validator warns about city name collisions`() {
        val game = TestGame()
        game.ruleset.buildings["Park"] = Building().apply {
            name = "Park"
            originRuleset = "Test mod"
            uniques.add("Unbuildable")
        }
        game.ruleset.nations.values.first().cities = arrayListOf("Park")

        val errors = game.ruleset.getErrorList()

        assertTrue(errors.any {
            it.errorSeverityToReport == RulesetErrorSeverity.WarningOptionsOnly
                && it.text.contains("\"Park\"")
                && it.text.contains("Building")
                && it.text.contains("Nation.cities")
        })
    }
}

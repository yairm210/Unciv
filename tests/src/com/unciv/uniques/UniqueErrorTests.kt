package com.unciv.uniques

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivFlags
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueParameterType
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.testing.BaseTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(BaseTestRunner::class)
class UniqueErrorTests {
    private fun validateUnique(uniqueText: String) =
        UniqueValidator(RulesetCache.getVanillaRuleset()).checkUnique(Unique(uniqueText), false, null)

    private fun hasUnacceptableModifierWarning(uniqueText: String): Boolean =
        validateUnique(uniqueText).any { it.text.contains("which is not an acceptable modifier for this unique") }

    private fun commentAngleBracketWarnings(uniqueText: String) =
        validateUnique(uniqueText).filter { it.text.contains("is a Comment containing") }

    @Test
    fun testMultipleUniqueTypesSameText() {
        val textToUniqueType = HashMap<String, UniqueType>()
        var errors = false
        for (uniqueType in UniqueType.entries) {
            if (textToUniqueType.containsKey(uniqueType.placeholderText)) {
                println("UniqueTypes ${uniqueType.name} and ${textToUniqueType[uniqueType.placeholderText]!!.name} have the same text!")
                errors = true
            }
            else textToUniqueType[uniqueType.placeholderText] = uniqueType
        }
        assert(!errors)
    }

    @Test
    fun testCodependantTechs() {
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()

        // Create a prerequisite loop
        val techWithPrerequisites = ruleset.technologies.values.first { it.prerequisites.isNotEmpty() }
        val prereq = ruleset.technologies[techWithPrerequisites.prerequisites.first()]!!
        prereq.prerequisites.add(techWithPrerequisites.name)
        ruleset.modOptions.isBaseRuleset = true

        // Check mod links and ensure we don't get a crash, instead we get errors
        val errors = ruleset.getErrorList(false)
        assert(errors.isNotOK())
    }


    @Test
    fun testPromotionCircularReferenceWithAcyclicAlternativeIsAllowed() {
        val game = TestGame()
        val root = game.createUnitPromotion()
        val firstBranch = game.createUnitPromotion()
        val secondBranch = game.createUnitPromotion()
        listOf(root, firstBranch, secondBranch).forEach { it.unitTypes = listOf("Scout") }

        firstBranch.prerequisites = listOf(secondBranch.name, root.name)
        secondBranch.prerequisites = listOf(firstBranch.name)
        game.ruleset.modOptions.isBaseRuleset = true

        val errors = game.ruleset.getErrorList(false)
        Assert.assertTrue(errors.none { it.text.startsWith("Circular Reference in Promotions") })
    }

    @Test
    fun testPromotionCircularReferenceWithoutAcyclicAlternativeIsWarned() {
        val game = TestGame()
        val firstBranch = game.createUnitPromotion()
        val secondBranch = game.createUnitPromotion()
        listOf(firstBranch, secondBranch).forEach { it.unitTypes = listOf("Scout") }

        firstBranch.prerequisites = listOf(secondBranch.name)
        secondBranch.prerequisites = listOf(firstBranch.name)
        game.ruleset.modOptions.isBaseRuleset = true

        val errors = game.ruleset.getErrorList(false)
        Assert.assertEquals(1, errors.size)
        Assert.assertEquals(
            "Circular Reference in Promotions: ${firstBranch.name}→${secondBranch.name}→${firstBranch.name}",
            errors.single().text
        )
    }

    @Test
    fun testTimedGlobalUniqueAcceptsTriggerConditionsWhenOnUnit(){
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()
        // Since the <for [3] turns> turns this unique into a triggerable, the <upon> trigger condition should be ok
        val uniqueText = "[-5]% Strength <for [3] turns> <upon damaging a [Warrior] unit>"

        // Without a unit, this is an error
        val uniqueNoSourceObject = Unique(uniqueText)
        val errorListNoSourceObject = UniqueValidator(ruleset).checkUnique(uniqueNoSourceObject, false, null)
        assert(errorListNoSourceObject.getFinalSeverity() == RulesetErrorSeverity.Warning)

        // When applied on a unit or promotion etc, this is fine
        val uniqueWithSourceObject = Unique(uniqueText, sourceObjectType = UniqueTarget.Promotion)
        val errorListCorrectUniqueContainer = UniqueValidator(ruleset).checkUnique(uniqueWithSourceObject, false, null)
        assert(errorListCorrectUniqueContainer.getFinalSeverity() == RulesetErrorSeverity.OK)
    }

    @Test
    fun testOneTimeGainStatAcceptsCivWideStats() {
        val errors = validateUnique("Gain [10] [Gold]")
        Assert.assertEquals(RulesetErrorSeverity.OK, errors.getFinalSeverity())
    }

    @Test
    fun testOneTimeGainStatRejectsNonCivWideStats() {
        assertOnlyCivWideStatsError("Gain [10] [Food]", "Food")
    }

    @Test
    fun testOneTimeGainStatRangeRejectsNonCivWideStats() {
        assertOnlyCivWideStatsError("Gain [5]-[10] [Production]", "Production")
    }

    @Test
    fun testCommentUniqueWithAngleBracketsWarnsOnceInsteadOfPerModifier() {
        RulesetCache.loadRulesets(noMods = true)
        val uniqueText = "Comment [Adopt [Feudalism] <upon entering the [Medieval era]>]"

        // The text inside a Comment is display-only, so it must not be validated as a modifier...
        Assert.assertFalse(hasUnacceptableModifierWarning(uniqueText))

        // ...but the modder still needs to know the `<...>` is parsed out and dropped from display,
        // so exactly one warning fires, carrying both likely causes.
        val warnings = commentAngleBracketWarnings(uniqueText)
        Assert.assertEquals(1, warnings.size)
        Assert.assertTrue(warnings.single().text.contains("removed from the displayed text"))
        Assert.assertTrue(warnings.single().text.contains("use nested [] instead"))
    }

    @Test
    fun testCommentUniqueWithMultipleAngleBracketsStillWarnsOnce() {
        RulesetCache.loadRulesets(noMods = true)
        val uniqueText = "Comment [see <one> and <two>]"

        Assert.assertEquals(1, commentAngleBracketWarnings(uniqueText).size)
    }

    @Test
    fun testPlainCommentUniqueHasNoValidationWarnings() {
        RulesetCache.loadRulesets(noMods = true)
        val uniqueText = "Comment [Plain display text]"

        Assert.assertEquals(RulesetErrorSeverity.OK, validateUnique(uniqueText).getFinalSeverity())
        Assert.assertTrue(commentAngleBracketWarnings(uniqueText).isEmpty())
    }

    @Test
    fun testNonCommentUniqueStillRejectsUnacceptableModifier() {
        RulesetCache.loadRulesets(noMods = true)
        val uniqueText = "Blocks line-of-sight from tiles at same elevation <upon entering the [Medieval era]>"

        Assert.assertTrue(hasUnacceptableModifierWarning(uniqueText))
    }

    @Test
    fun testEducatedEliteGreatPersonGifting() {
        // set up game
        var failures = 0
        fun logFailure(msg: String) {
            failures++
            println(msg)
        }
        val flagName = CivFlags.CityStateGreatPersonGift.name
        val game = TestGame()
        game.makeHexagonalMap(2)
        // prevent files access from completing tutorial tasks
        UncivGame.Current.settings.tutorialTasksCompleted.addAll(
            game.gameInfo.ruleset.events.keys
                .filter { it.startsWith("Tutorial Task: [") }
                .flatMap { it.getPlaceholderParameters() }
        )

        // set up civs
        val civ = game.addCiv()
        game.addCity(civ, game.getTile(-2, 0))
        game.gameInfo.currentPlayerCiv = civ
        val cityState = game.addCiv(cityStateType = "Mercantile")
        game.addCity(cityState, game.getTile(2, 0))

        // make the city-state allied
        civ.diplomacyFunctions.makeCivilizationsMeet(cityState)
        civ.addGold(10000)
        cityState.cityStateFunctions.receiveGoldGift(civ, 10000)
        if (cityState.allyCiv != civ)
            logFailure("The test civ and city-state should be allied after a 10k gift")

        // adopt the GP-gift-enabling policy
        val ee = game.ruleset.policies["Educated Elite"]!!
        civ.policies.freePolicies++
        civ.policies.adopt(ee)
        if (!civ.hasFlag(flagName))
            logFailure("The test civ should have the $flagName flag after adopting Educated Elite")

        // Ensure automation won't use up the GP - a threat won't do as it will be placed in the capital
        game.ruleset.units.entries.removeIf { it.value.isGreatPerson }
        game.createBaseUnit("Civilian", "Great Person - [Gold]", "Unbuildable", "Uncapturable")

        // Force the countdown and pass *one* turn, a GP should appear
        civ.addFlag(flagName, 1)
        game.gameInfo.simulateUntilWin = true
        game.gameInfo.simulateMaxTurns = 1
        game.gameInfo.nextTurn()
        val unit = civ.units.getCivUnits().firstOrNull { it.isGreatPerson() }
        if (unit == null)
            logFailure("No gifted Great Person unit found after setting $flagName to 1 and passing a turn")
        else
            println("GP unit gifted: $unit")
        Assert.assertEquals(0, failures)
    }

    private fun validateUnique(uniqueText: String) = run {
        RulesetCache.loadRulesets(noMods = true)
        val ruleset = RulesetCache.getVanillaRuleset()
        UniqueValidator(ruleset).checkUnique(Unique(uniqueText), false, null)
    }

    private fun assertOnlyCivWideStatsError(uniqueText: String, statName: String) {
        val error = validateUnique(uniqueText)
            .single { it.text.contains(UniqueValidator.whichDoesNotFitParameterType) }
        Assert.assertEquals(RulesetErrorSeverity.Error, error.errorSeverityToReport)
        Assert.assertTrue(error.text.contains(statName))
        Assert.assertTrue(error.text.contains(UniqueParameterType.CivWideStatName.parameterName))
    }
}

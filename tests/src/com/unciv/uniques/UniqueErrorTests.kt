package com.unciv.uniques

import com.unciv.UncivGame
import com.unciv.logic.civilization.CivFlags
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.validation.RulesetErrorSeverity
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(GdxTestRunner::class)
class UniqueErrorTests {
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
}

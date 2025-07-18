package com.unciv.models.ruleset.tech

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.objectdescriptions.uniquesToCivilopediaTextLines
import com.unciv.ui.screens.civilopediascreen.FormattedLine

class Era : RulesetObject() {
    var eraNumber: Int = -1
    var researchAgreementCost = 300
    var startingSettlerCount = 1
    var startingSettlerUnit = "Settler" // For mods which have differently named settlers
    var startingWorkerCount = 0
    var startingWorkerUnit = "Worker"
    var startingMilitaryUnitCount = 1
    var startingMilitaryUnit = "Warrior"
    var startingGold = 0
    var startingCulture = 0
    var settlerPopulation = 1
    var settlerBuildings = ArrayList<String>()
    var startingObsoleteWonders = ArrayList<String>()
    var baseUnitBuyCost = 200
    var embarkDefense = 3
    var startPercent = 0
    var citySound = "cityClassical"

    var friendBonus = HashMap<String, List<String>>()
    var allyBonus = HashMap<String, List<String>>()

    private var iconRGB: List<Int>? = null

    companion object {
        private val eraConditionals = setOf(UniqueType.ConditionalBeforeEra, UniqueType.ConditionalDuringEra, UniqueType.ConditionalStartingFromEra, UniqueType.ConditionalIfStartingInEra)
    }

    override fun getUniqueTarget() = UniqueTarget.Era

    override fun makeLink() = "Era/$name"
    override fun getCivilopediaTextHeader() = FormattedLine(name, header = 2, color = getHexColor())
    override fun getCivilopediaTextLines(ruleset: Ruleset) = sequence {
        yield(FormattedLine("Embarked strength: [$embarkDefense]${Fonts.strength}"))
        yield(FormattedLine("Base unit buy cost: [$baseUnitBuyCost]${Fonts.gold}"))
        yield(FormattedLine("Research agreement cost: [$researchAgreementCost]${Fonts.gold}"))
        yield(FormattedLine())
        yieldAll(ruleset.technologies.values.asSequence()
            .filter { it.era() == name }
            .map { FormattedLine(it.name, it.makeLink()) })

        yieldAll(uniquesToCivilopediaTextLines())

        val eraGatedObjects = getEraGatedObjects(ruleset).toList()
        if (eraGatedObjects.isEmpty()) return@sequence
        yield(FormattedLine())
        yield(FormattedLine("{See also}:"))
        yieldAll(eraGatedObjects.map { FormattedLine(it.name, it.makeLink()) })
    }.toList()
    override fun getSortGroup(ruleset: Ruleset): Int = eraNumber

    private fun getEraGatedObjects(ruleset: Ruleset): Sequence<IRulesetObject> {
        val policyBranches = ruleset.policyBranches.values.asSequence()
            .filter { it.era == name }
        return policyBranches +
            // This second part is empty in our base rulesets, yes
            ruleset.allRulesetObjects()
            .flatMap { obj ->
                obj.getMatchingUniques(
                    UniqueType.OnlyAvailable,
                    GameContext.IgnoreConditionals
                )
                .map { unique -> obj to unique }
            }.filter { (_, unique) ->
                unique.modifiers.any {
                    it.type in eraConditionals
                }
            }.map { it.first }.distinct()
    }

    fun getStartingUnits(ruleset: Ruleset): MutableList<String> {
        val startingUnits = mutableListOf<String>()
        val startingSettlerName: String =
            if (startingSettlerUnit in ruleset.units) startingSettlerUnit
            else ruleset.units.values
                .firstOrNull { it.isCityFounder() }
                ?.name
                ?: throw UncivShowableException("No Settler unit found for era $name")
        val startingWorkerName: String =
            if (startingWorkerCount == 0 || startingWorkerUnit in ruleset.units) startingWorkerUnit
            else ruleset.units.values
                .firstOrNull { it.hasUnique(UniqueType.BuildImprovements) }
                ?.name
                ?: throw UncivShowableException("No Worker unit found for era $name")
        repeat(startingSettlerCount) { startingUnits.add(startingSettlerName) }
        repeat(startingWorkerCount) { startingUnits.add(startingWorkerName) }
        repeat(startingMilitaryUnitCount) { startingUnits.add(startingMilitaryUnit) }
        return startingUnits
    }

    fun getColor(): Color {
        if (iconRGB == null) return Color.WHITE.cpy()
        return colorFromRGB(iconRGB!![0], iconRGB!![1], iconRGB!![2])
    }

    fun getHexColor() = "#" + getColor().toString().substring(0, 6)
}

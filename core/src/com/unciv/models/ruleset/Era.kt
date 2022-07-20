package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.unique.IHasUniques
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.colorFromRGB

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
    @Suppress("MemberVisibilityCanBePrivate")
    val friendBonusObjects: Map<CityStateType, List<Unique>> by lazy { initBonuses(friendBonus) }
    @Suppress("MemberVisibilityCanBePrivate")
    val allyBonusObjects: Map<CityStateType, List<Unique>> by lazy { initBonuses(allyBonus) }

    private var iconRGB: List<Int>? = null

    companion object {
        private val eraConditionals = setOf(UniqueType.ConditionalBeforeEra, UniqueType.ConditionalDuringEra, UniqueType.ConditionalStartingFromEra)
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

        if (uniques.isNotEmpty()) yield(FormattedLine())
        yieldAll(uniqueObjects.asSequence().map { FormattedLine(it) })

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
                    UniqueType.OnlyAvailableWhen,
                    StateForConditionals.IgnoreConditionals
                )
                .map { unique -> obj to unique }
            }.filter { (_, unique) ->
                unique.conditionals.any {
                    it.type in eraConditionals
                }
            }.map { it.first }.distinct()
    }

    private fun initBonuses(bonusMap: Map<String, List<String>>): Map<CityStateType, List<Unique>> {
        val objectMap = HashMap<CityStateType, List<Unique>>()
        for ((cityStateType, bonusList) in bonusMap) {
            objectMap[CityStateType.valueOf(cityStateType)] = bonusList.map { Unique(it, UniqueTarget.CityState) }
        }
        return objectMap
    }

    fun getCityStateBonuses(cityStateType: CityStateType, relationshipLevel: RelationshipLevel): List<Unique> {
        return when (relationshipLevel) {
            RelationshipLevel.Ally   -> allyBonusObjects[cityStateType]   ?: emptyList()
            RelationshipLevel.Friend -> friendBonusObjects[cityStateType] ?: emptyList()
            else -> emptyList()
        }
    }

    /** Since 3.19.5 we have a warning for mods without bonuses, eventually we should treat such mods as providing no bonus */
    fun undefinedCityStateBonuses(): Boolean {
        return friendBonus.isEmpty() || allyBonus.isEmpty()
    }

    fun getStartingUnits(): List<String> {
        val startingUnits = mutableListOf<String>()
        repeat(startingSettlerCount) { startingUnits.add(startingSettlerUnit) }
        repeat(startingWorkerCount) { startingUnits.add(startingWorkerUnit) }
        repeat(startingMilitaryUnitCount) { startingUnits.add(startingMilitaryUnit) }
        return startingUnits
    }

    fun getColor(): Color {
        if (iconRGB == null) return Color.WHITE.cpy()
        return colorFromRGB(iconRGB!![0], iconRGB!![1], iconRGB!![2])
    }

    fun getHexColor() = "#" + getColor().toString().substring(0, 6)
}

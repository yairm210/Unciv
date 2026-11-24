package com.unciv.models

import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.Conditionals
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.view.CivView
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

class InternationalProject(val name: String = "") : IsPartOfGameInfoSerialization {
    var contributions: HashMap<String, Int> = hashMapOf()
    var isClaimed: Boolean = false // Ensures only one city sends out the project completed message

    @Transient
    lateinit var gameInfo: GameInfo
    @Transient
    var projectUniqueMap = UniqueMap()

    @Cache
    private var isConstructedCache: Boolean = false

    fun clone(): InternationalProject {
        val newProject = InternationalProject(name)
        newProject.isClaimed = isClaimed
        newProject.contributions.putAll(contributions)
        return newProject
    }
    fun setTransients(gameInfo: GameInfo) {
        this.gameInfo = gameInfo
        val building = getBuilding()
        projectUniqueMap = UniqueMap(building?.uniqueObjects?.asSequence() ?: emptySequence())
    }
    @Readonly
    private fun getBuilding():Building? {
        return gameInfo.ruleset.buildings[name]
    }
    @Readonly
    fun isConstructed(): Boolean {
        if (isConstructedCache) return true
        val building = getBuilding() ?: return false
        val totalCost = building.getProductionCost(gameInfo.civilizations.first(), null)
        return contributions.values.sum() >= totalCost
    }
    @Readonly
    fun getContribution(civ: Civilization): Int? =
        contributions[civ.civName]

    @Readonly
    fun isGreatestContributor(civ: Civilization): Boolean {
        val contribution = getContribution(civ) ?: return false
        val greatest = contributions.values.maxOrNull() ?: return false
        return contribution == greatest
    }

    @Readonly
    fun hasContributionAbove(civ: Civilization, percentage: Int): Boolean {
        val threshold = getCost() * percentage / 100
        val contribution = getContribution(civ) ?: return false
        return contribution > threshold
    }
    fun completeConstruction(): Boolean {
        isClaimed = true
        val building = getBuilding() ?: return false

        val buildingIcon = "BuildingIcons/${building.name}"
        val pediaAction = CivilopediaAction(building.makeLink())
        
        val triggerNotificationText ="due to constructing [${building.name}]"
        for (civ in gameInfo.civilizations) {
            val stateForConditionals = civ.state
            // Trigger any one-time uniques (e.g. Free Social Policy, Golden Age points)
            for (unique in building.uniqueObjects) {
                if (unique.hasTriggerConditional() || !unique.conditionalsApply(stateForConditionals)) continue
                repeat(unique.getUniqueMultiplier(civ.state)) {
                    UniqueTriggerActivation.triggerUnique(unique, civ, triggerNotificationText = triggerNotificationText)
                }
            }
            for (city in civ.cities) {
                city.cityConstructions.validateConstructionQueue()
            }
            civ.addNotification(
                "The International Project [${building.name}] has been completed",
                listOf(pediaAction),
                NotificationCategory.General,
                NotificationIcon.Construction,
                buildingIcon
            )
        }
        return true
    }
    @Readonly
    fun isInternationalProjectAvailable(viewingPlayer: CivView): Boolean {
        val civ = viewingPlayer.getCiv()
        val building = viewingPlayer.ruleset.buildings[name] ?: return false
        if (isConstructed()) return true
        if (civ.gameInfo.isUnavailableBySettingsCached(building)) return false
        if (building.requiredTechs().any { !civ.tech.isResearched(it) }) return false

        for (unique in building.uniqueObjects) {
            when (unique.type) {
                UniqueType.Unavailable ->
                    if (unique.conditionalsApply(civ.state)) return false

                UniqueType.OnlyAvailable,
                UniqueType.CanOnlyBeBuiltWhen ->
                    if (unique.modifiers.any { !Conditionals.conditionalApplies(unique, it, civ.state) })
                        return false

                else -> {}
            }
        }

        if (building.uniqueTo != null && !civ.matchesFilter(building.uniqueTo!!, civ.state)) return false
        if (civ.cache.uniqueBuildings.any { it.replaces == building.name }) return false

        return true
    }
    @Readonly
    fun getCost(): Int {
        val building = getBuilding() ?: return 0
        var productionCost = building.cost.toFloat() * gameInfo.civilizations.count { it.isMajorCiv }
        productionCost *= gameInfo.speed.productionCostModifier
        return productionCost.toInt()
    }
}

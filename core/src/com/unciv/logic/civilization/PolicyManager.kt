package com.unciv.logic.civilization

import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Policy
import com.unciv.ui.VictoryScreen
import com.unciv.ui.pickerscreens.GreatPersonPickerScreen
import com.unciv.ui.utils.getRandom


class PolicyManager {

    @Transient
    lateinit var civInfo: CivilizationInfo

    var freePolicies = 0
    var storedCulture = 0
    internal val adoptedPolicies = HashSet<String>()
    var shouldOpenPolicyPicker = false

    // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
    // round down to nearest 5
    fun getCultureNeededForNextPolicy(): Int {
        val basicPolicies = adoptedPolicies.count { !it.endsWith("Complete") }
        var baseCost = 25 + Math.pow((basicPolicies * 6).toDouble(), 1.7)
        var cityModifier = 0.3 * (civInfo.cities.size - 1)
        if (isAdopted("Representation")) cityModifier *= (2 / 3f).toDouble()
        if (isAdopted("Piety Complete")) baseCost *= 0.9
        if (civInfo.buildingUniques.contains("PolicyCostReduction")) baseCost *= 0.9
        val cost: Int = Math.round(baseCost * (1 + cityModifier)).toInt()
        return cost - (cost % 5)
    }


    fun getAdoptedPolicies(): HashSet<String> = adoptedPolicies

    fun isAdopted(policyName: String): Boolean = adoptedPolicies.contains(policyName)

    fun isAdoptable(policy: Policy) = !policy.name.endsWith("Complete")
            && getAdoptedPolicies().containsAll(policy.requires!!)

    fun canAdoptPolicy(): Boolean = freePolicies > 0 || storedCulture >= getCultureNeededForNextPolicy()

    fun adopt(policy: Policy, branchCompletion: Boolean = false) {

        if(!branchCompletion) {
            if (freePolicies > 0) freePolicies--
            else  storedCulture -= getCultureNeededForNextPolicy()
        }

        adoptedPolicies.add(policy.name)

        if (!branchCompletion) {
            val branch = GameBasics.PolicyBranches[policy.branch]!!
            if (branch.policies.count { isAdopted(it.name) } == branch.policies.size - 1) { // All done apart from branch completion
                adopt(branch.policies.last(), true) // add branch completion!
            }
        } else {
            UnCivGame.Current.screen = VictoryScreen()
        }

        when (policy.name) {
            "Collective Rule" -> civInfo.placeUnitNearTile(civInfo.capital.location, "Settler")
            "Citizenship" -> civInfo.placeUnitNearTile(civInfo.capital.location, "Worker")
            "Representation", "Reformation" -> civInfo.goldenAges.enterGoldenAge()
            "Scientific Revolution" -> civInfo.tech.freeTechs += 2
            "Legalism" ->
                for (city in civInfo.cities.subList(0, Math.min(4, civInfo.cities.size)))
                    city.cityConstructions.addCultureBuilding()
            "Free Religion" -> freePolicies++
            "Liberty Complete" -> {
                if (civInfo.isPlayerCivilization()) UnCivGame.Current.screen = GreatPersonPickerScreen() // screw the victory screen!
                else civInfo.addGreatPerson(GameBasics.Units.keys.filter { it.startsWith("Great") }.getRandom())
            }
        }

        for (cityInfo in civInfo.cities)
            cityInfo.cityStats.update()
    }

    fun nextTurn(culture: Int) {
        val couldAdoptPolicyBefore = canAdoptPolicy()
        storedCulture += culture
        if (!couldAdoptPolicyBefore && canAdoptPolicy())
            shouldOpenPolicyPicker = true
    }

}
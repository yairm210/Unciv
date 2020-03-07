package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.VictoryType
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt


class PolicyManager {

    @Transient lateinit var civInfo: CivilizationInfo

    var freePolicies = 0
    var storedCulture = 0
    internal val adoptedPolicies = HashSet<String>()
    var numberOfAdoptedPolicies = 0
    var shouldOpenPolicyPicker = false
            get() = field && canAdoptPolicy()
    var legalismState = HashMap<String, String>()

    fun clone(): PolicyManager {
        val toReturn = PolicyManager()
        toReturn.numberOfAdoptedPolicies = numberOfAdoptedPolicies
        toReturn.adoptedPolicies.addAll(adoptedPolicies)
        toReturn.freePolicies = freePolicies
        toReturn.shouldOpenPolicyPicker = shouldOpenPolicyPicker
        toReturn.storedCulture = storedCulture
        toReturn.legalismState.putAll(legalismState)
        return toReturn
    }

    fun startTurn() {
        if (isAdopted("Legalism") && legalismState.size < 4)
            tryAddLegalismBuildings()
    }

    fun endTurn(culture: Int) {
        val couldAdoptPolicyBefore = canAdoptPolicy()
        storedCulture += culture
        if (!couldAdoptPolicyBefore && canAdoptPolicy())
            shouldOpenPolicyPicker = true
    }

    // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
    // round down to nearest 5
    fun getCultureNeededForNextPolicy(): Int {
        var policyCultureCost = 25 + (numberOfAdoptedPolicies * 6).toDouble().pow(1.7)
        var cityModifier = 0.3 * (civInfo.cities.count { !it.isPuppet } - 1)

        if (isAdopted("Representation")) cityModifier *= (2 / 3f).toDouble()
        if (isAdopted("Piety Complete")) policyCultureCost *= 0.9
        if (civInfo.containsBuildingUnique("Culture cost of adopting new Policies reduced by 10%"))
            policyCultureCost *= 0.9
        if (civInfo.isPlayerCivilization())
            policyCultureCost *= civInfo.getDifficulty().policyCostModifier
        policyCultureCost *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
        val cost: Int = (policyCultureCost * (1 + cityModifier)).roundToInt()
        return cost - (cost % 5)
    }

    fun getAdoptedPolicies(): HashSet<String> = adoptedPolicies

    fun isAdopted(policyName: String): Boolean = adoptedPolicies.contains(policyName)

    fun isAdoptable(policy: Policy): Boolean {
        if(isAdopted(policy.name)) return false
        if (policy.name.endsWith("Complete")) return false
        if (!getAdoptedPolicies().containsAll(policy.requires!!)) return false
        if (policy.branch.era > civInfo.getEra()) return false
        return true
    }

    fun canAdoptPolicy(): Boolean {
        if (freePolicies == 0 && storedCulture < getCultureNeededForNextPolicy())
            return false

        val hasAdoptablePolicies = civInfo.gameInfo.ruleSet.policyBranches.values
                .flatMap { it.policies.union(listOf(it)) }
                .any { civInfo.policies.isAdoptable(it) }
        return hasAdoptablePolicies
    }

    fun adopt(policy: Policy, branchCompletion: Boolean = false) {

        if(!branchCompletion) {
            if (freePolicies > 0) freePolicies--
            else  {
                val cultureNeededForNextPolicy = getCultureNeededForNextPolicy()
                if(cultureNeededForNextPolicy > storedCulture)
                    throw Exception("How is this possible??????")
                storedCulture -= cultureNeededForNextPolicy
                numberOfAdoptedPolicies++
            }
        }

        adoptedPolicies.add(policy.name)

        if (!branchCompletion) {
            val branch = policy.branch
            if (branch.policies.count { isAdopted(it.name) } == branch.policies.size - 1) { // All done apart from branch completion
                adopt(branch.policies.last(), true) // add branch completion!
            }
        }

        val hasCapital = civInfo.cities.any{it.isCapital()}
        when (policy.name) {
            "Collective Rule" -> if(hasCapital && !civInfo.isOneCityChallenger())
                civInfo.placeUnitNearTile(civInfo.getCapital().location, Constants.settler)
            "Citizenship" -> if(hasCapital) civInfo.placeUnitNearTile(civInfo.getCapital().location, Constants.worker)
            "Representation", "Reformation" -> civInfo.goldenAges.enterGoldenAge()
            "Legalism" -> tryAddLegalismBuildings()
            "Free Religion" -> freePolicies++
            "Liberty Complete" -> {
                if (civInfo.isPlayerCivilization()) civInfo.greatPeople.freeGreatPeople++
                else {
                    val preferredVictoryType = civInfo.victoryType()
                    val greatPerson = when(preferredVictoryType) {
                        VictoryType.Cultural -> "Great Artist"
                        VictoryType.Scientific -> "Great Scientist"
                        VictoryType.Domination,VictoryType.Neutral ->
                            civInfo.gameInfo.ruleSet.units.keys.filter { it.startsWith("Great") }.random()
                    }
                    civInfo.addGreatPerson(greatPerson)
                }
            }
        }

        // This ALSO has the side-effect of updating the CivInfo statForNextTurn so we don't need to call it explicitly
        for (cityInfo in civInfo.cities)
            cityInfo.cityStats.update()

        if(!canAdoptPolicy()) shouldOpenPolicyPicker=false
    }

    fun tryAddLegalismBuildings() {
        val candidateCities = civInfo.cities
                .sortedBy { it.turnAcquired }
                .subList(0, min(4, civInfo.cities.size))
                .filter { it.id !in legalismState
                        && it.cityConstructions.hasBuildableCultureBuilding() }
        for (city in candidateCities) {
            val builtBuilding = city.cityConstructions.addCultureBuilding()
            legalismState[city.id] = builtBuilding!!
        }
    }
}
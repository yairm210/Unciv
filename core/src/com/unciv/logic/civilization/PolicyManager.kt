package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.UniqueMap
import com.unciv.models.ruleset.UniqueTriggerActivation
import com.unciv.models.ruleset.VictoryType
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt


class PolicyManager {

    @Transient lateinit var civInfo: CivilizationInfo
    // Needs to be separate from the actual adopted policies, so that
    //  in different game versions, policies can have different effects
    @Transient internal val policyUniques = UniqueMap()

    var freePolicies = 0
    var storedCulture = 0
    internal val adoptedPolicies = HashSet<String>()
    var numberOfAdoptedPolicies = 0
    var shouldOpenPolicyPicker = false
            get() = field && canAdoptPolicy()
    var legalismState = HashMap<String, String>()
    var autocracyCompletedTurns = 0

    fun clone(): PolicyManager {
        val toReturn = PolicyManager()
        toReturn.numberOfAdoptedPolicies = numberOfAdoptedPolicies
        toReturn.adoptedPolicies.addAll(adoptedPolicies)
        toReturn.freePolicies = freePolicies
        toReturn.shouldOpenPolicyPicker = shouldOpenPolicyPicker
        toReturn.storedCulture = storedCulture
        toReturn.legalismState.putAll(legalismState)
        toReturn.autocracyCompletedTurns = autocracyCompletedTurns
        return toReturn
    }

    fun getPolicyByName(name:String): Policy = getAllPolicies().first { it.name==name }
            
    fun setTransients() {
        for (policyName in adoptedPolicies)
            addPolicyToTransients(getPolicyByName(policyName))
    }

    fun addPolicyToTransients(policy: Policy){
        for(unique in policy.uniqueObjects)
            policyUniques.addUnique(unique)
    }

    private fun getAllPolicies() = civInfo.gameInfo.ruleSet.policyBranches.values.asSequence()
            .flatMap { it.policies.asSequence()+sequenceOf(it) }

    fun startTurn() {
        tryAddLegalismBuildings()
    }

    fun addCulture(culture: Int){
        val couldAdoptPolicyBefore = canAdoptPolicy()
        storedCulture += culture
        if (!couldAdoptPolicyBefore && canAdoptPolicy())
            shouldOpenPolicyPicker = true
    }

    fun endTurn(culture: Int) {
        addCulture(culture)
        if (autocracyCompletedTurns > 0)
            autocracyCompletedTurns -= 1
    }

    // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
    // round down to nearest 5
    fun getCultureNeededForNextPolicy(): Int {
        var policyCultureCost = 25 + (numberOfAdoptedPolicies * 6).toDouble().pow(1.7)
        var cityModifier = 0.3f * (civInfo.cities.count { !it.isPuppet } - 1)

        // As of 3.10.11 These are to be deprecated. Keeping it here so that mods with this can still work for now.
        // Use "Culture cost of adopting new Policies reduced by [10]%" and "Each city founded increases culture cost of policies [33]% less than normal" instead
        if (civInfo.hasUnique("Each city founded increases culture cost of policies 33% less than normal"))
            cityModifier *= (2 / 3f)
        for(unique in civInfo.getMatchingUniques("Culture cost of adopting new Policies reduced by 10%"))
            policyCultureCost *= 0.9

        for (unique in civInfo.getMatchingUniques("Each city founded increases culture cost of policies []% less than normal"))
            cityModifier *= 1 - unique.params[0].toFloat() / 100
        for (unique in civInfo.getMatchingUniques("Culture cost of adopting new Policies reduced by []%"))
            policyCultureCost *= 1 - unique.params[0].toFloat() / 100
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
        if (civInfo.gameInfo.ruleSet.getEraNumber(policy.branch.era) > civInfo.getEraNumber()) return false
        return true
    }

    fun canAdoptPolicy(): Boolean {
        if (freePolicies == 0 && storedCulture < getCultureNeededForNextPolicy())
            return false

        val hasAdoptablePolicies = getAllPolicies()
                .any { civInfo.policies.isAdoptable(it) }
        return hasAdoptablePolicies
    }

    fun adopt(policy: Policy, branchCompletion: Boolean = false) {

        if (!branchCompletion) {
            if (freePolicies > 0) freePolicies--
            else if (!civInfo.gameInfo.gameParameters.godMode) {
                val cultureNeededForNextPolicy = getCultureNeededForNextPolicy()
                if (cultureNeededForNextPolicy > storedCulture)
                    throw Exception("How is this possible??????")
                storedCulture -= cultureNeededForNextPolicy
                numberOfAdoptedPolicies++
            }
        }

        adoptedPolicies.add(policy.name)
        addPolicyToTransients(policy)

        if (!branchCompletion) {
            val branch = policy.branch
            if (branch.policies.count { isAdopted(it.name) } == branch.policies.size - 1) { // All done apart from branch completion
                adopt(branch.policies.last(), true) // add branch completion!
            }
        }

        val hasCapital = civInfo.cities.any { it.isCapital() }

        for (unique in policy.uniqueObjects)
            UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo)

        tryAddLegalismBuildings()

        // This ALSO has the side-effect of updating the CivInfo statForNextTurn so we don't need to call it explicitly
        for (cityInfo in civInfo.cities)
            cityInfo.cityStats.update()

        if (!canAdoptPolicy()) shouldOpenPolicyPicker = false
    }

    fun tryAddLegalismBuildings() {
        if(!civInfo.hasUnique("Immediately creates a cheapest available cultural building in each of your first 4 cities for free"))
            return
        if(legalismState.size >= 4) return

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
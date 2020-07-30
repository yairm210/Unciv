package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import com.unciv.ui.utils.withItem
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt


class PolicyManager {

    @Transient lateinit var civInfo: CivilizationInfo
    // Needs to be separate from the actual adopted policies, so that
    //  in different game versions, policies can have different effects
    @Transient internal val policyEffects = HashSet<String>()

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
            
    fun setTransients(){
        val effectsOfCurrentPolicies = adoptedPolicies.map { getPolicyByName(it).effect }
        policyEffects.addAll(effectsOfCurrentPolicies)
        adoptedPolicies.map { getPolicyByName(it).uniques }.forEach { policyEffects.addAll(it) }
    }

    private fun getAllPolicies() = civInfo.gameInfo.ruleSet.policyBranches.values.asSequence()
            .flatMap { it.policies.asSequence()+sequenceOf(it) }

    fun startTurn() {
        tryAddLegalismBuildings()
    }

    fun endTurn(culture: Int) {
        val couldAdoptPolicyBefore = canAdoptPolicy()
        storedCulture += culture
        if (!couldAdoptPolicyBefore && canAdoptPolicy())
            shouldOpenPolicyPicker = true
        if (autocracyCompletedTurns > 0)
            autocracyCompletedTurns -= 1
    }

    // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
    // round down to nearest 5
    fun getCultureNeededForNextPolicy(): Int {
        var policyCultureCost = 25 + (numberOfAdoptedPolicies * 6).toDouble().pow(1.7)
        var cityModifier = 0.3f * (civInfo.cities.count { !it.isPuppet } - 1)

        if (civInfo.hasUnique("Each city founded increases culture cost of policies 33% less than normal"))
            cityModifier *= (2 / 3f)
        for(unique in civInfo.getMatchingUniques("Culture cost of adopting new Policies reduced by 10%"))
            policyCultureCost *= 0.9
        if (civInfo.isPlayerCivilization())
            policyCultureCost *= civInfo.getDifficulty().policyCostModifier
        policyCultureCost *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
        val cost: Int = (policyCultureCost * (1 + cityModifier)).roundToInt()
        return cost - (cost % 5)
    }

    fun getAdoptedPolicies(): HashSet<String> = adoptedPolicies

    fun isAdopted(policyName: String): Boolean = adoptedPolicies.contains(policyName)

    fun hasEffect(effectName:String) = policyEffects.contains(effectName)

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
            else {
                val cultureNeededForNextPolicy = getCultureNeededForNextPolicy()
                if (cultureNeededForNextPolicy > storedCulture)
                    throw Exception("How is this possible??????")
                storedCulture -= cultureNeededForNextPolicy
                numberOfAdoptedPolicies++
            }
        }

        adoptedPolicies.add(policy.name)
        policyEffects.add(policy.effect)
        policyEffects.addAll(policy.uniques)

        if (!branchCompletion) {
            val branch = policy.branch
            if (branch.policies.count { isAdopted(it.name) } == branch.policies.size - 1) { // All done apart from branch completion
                adopt(branch.policies.last(), true) // add branch completion!
            }
        }

        val hasCapital = civInfo.cities.any { it.isCapital() }

        for(effect in policy.uniques.withItem(policy.effect))
            when (effect.getPlaceholderText()) {
                "Free [] appears" -> {
                    val unitName = effect.getPlaceholderParameters()[0]
                    if (hasCapital && (unitName != Constants.settler || !civInfo.isOneCityChallenger()))
                        civInfo.placeUnitNearTile(civInfo.getCapital().location, unitName)
                }
                "Gain a free policy" -> freePolicies++
                "Empire enters golden age" ->
                    civInfo.goldenAges.enterGoldenAge()
                "Free Great Person" -> {
                    if (civInfo.isPlayerCivilization()) civInfo.greatPeople.freeGreatPeople++
                    else {
                        val preferredVictoryType = civInfo.victoryType()
                        val greatPerson = when (preferredVictoryType) {
                            VictoryType.Cultural -> "Great Artist"
                            VictoryType.Scientific -> "Great Scientist"
                            VictoryType.Domination, VictoryType.Neutral ->
                                civInfo.gameInfo.ruleSet.units.keys.filter { it.startsWith("Great") }.random()
                        }
                        civInfo.addGreatPerson(greatPerson)
                    }
                }
                "Quantity of strategic resources produced by the empire increased by 100%" -> civInfo.updateDetailedCivResources()
                "+20% attack bonus to all Military Units for 30 turns" -> autocracyCompletedTurns = 30
            }
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
package com.unciv.logic.civilization

import com.unciv.logic.map.MapSize
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import kotlin.math.pow
import kotlin.math.roundToInt


class PolicyManager {

    @Transient
    lateinit var civInfo: CivilizationInfo

    // Needs to be separate from the actual adopted policies, so that
    //  in different game versions, policies can have different effects
    @Transient
    internal val policyUniques = UniqueMap()

    var freePolicies = 0
    var storedCulture = 0
    internal val adoptedPolicies = HashSet<String>()
    var numberOfAdoptedPolicies = 0
    var shouldOpenPolicyPicker = false
        get() = field && canAdoptPolicy()


    fun clone(): PolicyManager {
        val toReturn = PolicyManager()
        toReturn.numberOfAdoptedPolicies = numberOfAdoptedPolicies
        toReturn.adoptedPolicies.addAll(adoptedPolicies)
        toReturn.freePolicies = freePolicies
        toReturn.shouldOpenPolicyPicker = shouldOpenPolicyPicker
        toReturn.storedCulture = storedCulture
        return toReturn
    }

    fun getPolicyByName(name: String): Policy = civInfo.gameInfo.ruleSet.policies[name]!!

    fun setTransients() {
        for (policyName in adoptedPolicies)
            addPolicyToTransients(getPolicyByName(policyName))
    }

    fun addPolicyToTransients(policy: Policy) {
        for (unique in policy.uniqueObjects)
            policyUniques.addUnique(unique)
    }

    fun addCulture(culture: Int) {
        val couldAdoptPolicyBefore = canAdoptPolicy()
        storedCulture += culture
        if (!couldAdoptPolicyBefore && canAdoptPolicy())
            shouldOpenPolicyPicker = true
    }

    fun endTurn(culture: Int) {
        addCulture(culture)
    }

    // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
    // round down to nearest 5
    fun getCultureNeededForNextPolicy(): Int {
        var policyCultureCost = 25 + (numberOfAdoptedPolicies * 6).toDouble().pow(1.7)
        // https://civilization.fandom.com/wiki/Map_(Civ5)
        val worldSizeModifier = with (civInfo.gameInfo.tileMap.mapParameters.mapSize) {
            when {
                radius >= MapSize.Huge.radius -> 0.05f
                radius >= MapSize.Large.radius -> 0.075f
                else -> 0.1f
            }
        }
        var cityModifier = worldSizeModifier * (civInfo.cities.count { !it.isPuppet } - 1)

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

    /**
     * Test whether a policy is adoptable according to the RuleSet (ignoring cost).
     * Note: branch completion policies are automatic and therefore not adoptable in this test.
     * @param policy The Policy to check
     * @param checkEra Include era test (with false the function returns whether the policy is adoptable now or in the future)
     * @return `true` if the policy can be adopted, `false` if some rule prevents it (including when it's already adopted) 
     */
    fun isAdoptable(policy: Policy, checkEra: Boolean = true): Boolean {
        if (isAdopted(policy.name)) return false
        if (policy.policyBranchType == PolicyBranchType.BranchComplete) return false
        if (!getAdoptedPolicies().containsAll(policy.requires!!)) return false
        if (checkEra && civInfo.gameInfo.ruleSet.eras[policy.branch.era]!!.eraNumber > civInfo.getEraNumber()) return false
        if (policy.uniqueObjects.any { it.placeholderText == "Incompatible with []" && adoptedPolicies.contains(it.params[0]) }) return false
        return true
    }

    fun canAdoptPolicy(): Boolean {
        if (civInfo.cities.isEmpty()) return false

        if (freePolicies == 0 && storedCulture < getCultureNeededForNextPolicy())
            return false

        //Return true if there is a policy to adopt, else return false
        return civInfo.gameInfo.ruleSet.policies.values.any { civInfo.policies.isAdoptable(it) }
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

        for (unique in policy.uniques) {
            if (unique.equalsPlaceholderText("Triggers the following global alert: []"))
                triggerGlobalAlerts(policy, unique.getPlaceholderParameters()[0])
        }

        for (unique in policy.uniqueObjects)
            UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo)

        // This ALSO has the side-effect of updating the CivInfo statForNextTurn so we don't need to call it explicitly
        for (cityInfo in civInfo.cities)
            cityInfo.cityStats.update()

        if (!canAdoptPolicy()) shouldOpenPolicyPicker = false
    }

    private fun triggerGlobalAlerts(policy: Policy, extraNotificationText: String = "") {
        var extraNotificationTextCopy = extraNotificationText
        if (extraNotificationText != "") {
            extraNotificationTextCopy = "\n${extraNotificationText}"
        }
        for (civ in civInfo.gameInfo.civilizations) {
            if (civ == civInfo) continue
            val defaultNotificationText = 
                if (civ.getKnownCivs().contains(civInfo)) {
                    "[${civInfo.civName}] has adopted the [${policy.name}] policy"
                } else {
                    "An unknown civilization has adopted the [${policy.name}] policy"
                }
            civ.addNotification("${defaultNotificationText}${extraNotificationTextCopy}", NotificationIcon.Culture)
        }
    }
}

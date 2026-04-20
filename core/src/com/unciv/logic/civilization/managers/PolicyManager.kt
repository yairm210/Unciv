package com.unciv.logic.civilization.managers

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.Policy
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.toPercent
import yairm210.purity.annotations.Readonly
import kotlin.math.pow
import kotlin.math.roundToInt


class PolicyManager : IsPartOfGameInfoSerialization {
    companion object {
        /** Used in [getCultureRefundMap] when refunding more policies than were bought with culture
         *  to indicate the "surplus" policies - those must have been adopted as free policies. */
        const val FREE_POLICY_MARKER = -1
    }

    @Transient
    lateinit var civInfo: Civilization

    // Needs to be separate from the actual adopted policies, so that
    //  in different game versions, policies can have different effects
    @Transient
    internal val policyUniques = UniqueMap()

    var freePolicies = 0
    var storedCulture = 0
    internal val adoptedPolicies = HashSet<String>()
    private var numberOfAdoptedPolicies = 0

    private var cultureOfLast8Turns = IntArray(8)

    /** Indicates whether we should *check* if policy is adoptible, and if so open */
    var shouldOpenPolicyPicker = false

    /** Used by NextTurnAction.PickPolicy.isChoice */
    @Readonly fun shouldShowPolicyPicker() = (shouldOpenPolicyPicker || freePolicies > 0) && canAdoptPolicy()

    /** A [Map] pairing each [PolicyBranch] to its priority ([Int]). */
    val priorityMap: Map<PolicyBranch, Int>
        get() {
            val value = HashMap<PolicyBranch, Int>()
            for (branch in branches) {
                val victoryPriority = civInfo.getPreferredVictoryTypes().sumOf { branch.priorities[it] ?: 0}
                val personalityPriority = civInfo.getPersonality().priorities[branch.name] ?: 0
                val branchPriority = (victoryPriority + personalityPriority) * 
                        branch.getWeightForAiDecision(civInfo.state)
                value[branch] = branchPriority.roundToInt()
            }
            return value
        }
    /** A [Set] of newly adoptable [PolicyBranch]es. */
    val adoptableBranches: Set<PolicyBranch>
        get() = branches.filter { isAdoptable(it) }.toSet()
    /** A [Set] of incomplete [PolicyBranch]es including newly adoptable ones. */
    val incompleteBranches: Set<PolicyBranch>
        get() {
            val value = HashSet<PolicyBranch>()
            for (branch in branches) {
                if (branch.policies.any { isAdoptable(it) }) value.add(branch)
            }
            return value
        }
    /** A [Set] of completed [PolicyBranch]es. */
    val completedBranches: Set<PolicyBranch>
        get() {
            val value = HashSet<PolicyBranch>()
            for (branch in branches) {
                if (branch.policies.all { isAdopted(it.name) }) value.add(branch)
            }
            return value
        }
    /** A [Map] pairing each [PolicyBranch] to how many of its child branches are adopted ([Int]). */
    val branchCompletionMap: Map<PolicyBranch, Int>
        get() {
            val value = HashMap<PolicyBranch, Int>()
            for (branch in branches) {
                value[branch] = adoptedPolicies.count {
                    branch.policies.contains(getPolicyByName(it))
                }
            }
            return value
        }

    /** A [Set] of all [PolicyBranch]es. */
    val branches: Set<PolicyBranch>
        get() = civInfo.gameInfo.ruleset.policyBranches.values.toSet()

    fun clone(): PolicyManager {
        val toReturn = PolicyManager()
        toReturn.numberOfAdoptedPolicies = numberOfAdoptedPolicies
        toReturn.adoptedPolicies.addAll(adoptedPolicies)
        toReturn.freePolicies = freePolicies
        toReturn.shouldOpenPolicyPicker = shouldOpenPolicyPicker
        toReturn.storedCulture = storedCulture
        toReturn.cultureOfLast8Turns = cultureOfLast8Turns.clone()
        return toReturn
    }

    @Readonly private fun getRulesetPolicies() = civInfo.gameInfo.ruleset.policies

    @Suppress("MemberVisibilityCanBePrivate")
    @Readonly fun getPolicyByName(name: String): Policy =
        getRulesetPolicies()[name]
            ?: getRulesetPolicies().values.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Policy $name is not found in ruleset")

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
        for (policyName in adoptedPolicies) addPolicyToTransients(
            getPolicyByName(policyName)
        )
    }

    private fun addPolicyToTransients(policy: Policy) {
        for (unique in policy.uniqueObjects) {
            policyUniques.addUnique(unique)
        }
    }

    private fun removePolicyFromTransients(policy: Policy) {
        for (unique in policy.uniqueObjects) {
            policyUniques.removeUnique(unique)
        }
    }

    fun addCulture(culture: Int) {
        val couldAdoptPolicyBefore = canAdoptPolicy()
        storedCulture += culture
        if (!couldAdoptPolicyBefore && canAdoptPolicy()) {
            shouldOpenPolicyPicker = true
        }
    }

    fun endTurn(culture: Int) {
        ensureCultureHistoryInitialized()
        addCulture(culture)
        addCurrentCultureToCultureOfLast8Turns(culture)
    }

    // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
    // round down to nearest 5
    @Readonly fun getCultureNeededForNextPolicy(): Int = getPolicyCultureCost(numberOfAdoptedPolicies)

    @Readonly
    /** Maps [policiesToRemove] to a culture amount to refund.
     *  If more policies are removed than were bought with culture, the "extras" are returned
     *  with value [FREE_POLICY_MARKER]: do not refund in that case, grant the free policy back - or not.
     */
    fun getCultureRefundMap(policiesToRemove: Sequence<Policy>, refundPercentage: Int): Map<Policy, Int> {
        var policyCostInput = numberOfAdoptedPolicies

        val policyMap = mutableMapOf<Policy, Int>()

        for (policy in policiesToRemove) {
            if (policy.policyBranchType == PolicyBranchType.BranchComplete)
                continue
            policyCostInput--
            policyMap[policy] = if (policyCostInput < 0) FREE_POLICY_MARKER
                else (getPolicyCultureCost(policyCostInput) * refundPercentage/100f).roundToInt()
        }

        return policyMap
    }

    @Readonly
    fun getPolicyCultureCost(numberOfAdoptedPolicies: Int): Int {
        var policyCultureCost = 25 + (numberOfAdoptedPolicies * 6).toDouble().pow(1.7)
        val worldSizeModifier = civInfo.gameInfo.tileMap.mapParameters.mapSize.getPredefinedOrNextSmaller().policyCostPerCityModifier
        var cityModifier = worldSizeModifier * (civInfo.cities.count { !it.isPuppet } - 1)

        for (unique in civInfo.getMatchingUniques(UniqueType.LessPolicyCostFromCities)) cityModifier *= 1 - unique.params[0].toFloat() / 100
        for (unique in civInfo.getMatchingUniques(UniqueType.LessPolicyCost)) policyCultureCost *= unique.params[0].toPercent()
        if (civInfo.isHuman()) policyCultureCost *= civInfo.getDifficulty().policyCostModifier
        policyCultureCost *= civInfo.gameInfo.speed.cultureCostModifier
        val cost: Int = (policyCultureCost * (1 + cityModifier)).roundToInt()
        return cost - (cost % 5)
    }

    @Readonly fun getAdoptedPolicies(): HashSet<String> = adoptedPolicies

    @Readonly
    /**
     *  Gets a Sequence of those adopted policies as [Policy] objects that match [policyFilter]
     *
     *  Uncached, use carefully
     *
     *  @param gameContext Passed to [Policy.matchesFilter]
     *  @param forRemoval When `true` sorts the result by json position descending,
     *      so a removal in order won't remove a branch start before its members.
     *      Also, `BranchComplete` policies are skipped - you shouldn't try to remove them explicitly.
     */
    fun getAdoptedPoliciesMatching(
        policyFilter: String,
        gameContext: GameContext,
        forRemoval: Boolean = false
    ): Sequence<Policy> {
        val rulesetPolicies = getRulesetPolicies()
        val matchingPolicies = adoptedPolicies.asSequence()
            .mapNotNull { rulesetPolicies[it] }
            .filter { it.matchesFilter(policyFilter, gameContext) }
        if (!forRemoval) return matchingPolicies
        return matchingPolicies
            .filterNot { it.policyBranchType == PolicyBranchType.BranchComplete }
            .sortedByDescending { rulesetPolicies.values.indexOf(it) }
    }

    @Readonly fun isAdopted(policyName: String): Boolean = adoptedPolicies.contains(policyName)

    /**
     * Test whether a policy is adoptable according to the RuleSet (ignoring cost).
     * Note: branch completion policies are automatic and therefore not adoptable in this test.
     * @param policy The Policy to check
     * @param checkEra Include era test (with false the function returns whether the policy is adoptable now or in the future)
     * @return `true` if the policy can be adopted, `false` if some rule prevents it (including when it's already adopted)
     */
    @Readonly
    fun isAdoptable(policy: Policy, checkEra: Boolean = true): Boolean {
        if (isAdopted(policy.name)) return false
        if (policy.policyBranchType == PolicyBranchType.BranchComplete) return false
        val requiredPolicies = policy.requires ?: emptyList()
        if (!getAdoptedPolicies().containsAll(requiredPolicies)) return false
        if (checkEra) {
            val requiredEraNumber = civInfo.gameInfo.ruleset.eras[policy.branch.era]?.eraNumber
                ?: return true
            if (requiredEraNumber > civInfo.getEraNumber()) return false
        }
        if (policy.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                .any { !it.conditionalsApply(civInfo.state) }) return false
        if (policy.hasUnique(UniqueType.Unavailable, civInfo.state)) return false
        return true
    }

    @Readonly
    fun canAdoptPolicy(): Boolean {
        if (civInfo.isSpectator()) return false
        if (freePolicies == 0 && storedCulture < getCultureNeededForNextPolicy()) return false
        if (allPoliciesAdopted(true)) return false
        return true
    }


    fun adopt(policy: Policy, branchCompletion: Boolean = false) {

        if (!branchCompletion) {
            if (freePolicies > 0) freePolicies--
            else if (!civInfo.gameInfo.gameParameters.godMode) {
                val cultureNeededForNextPolicy = getCultureNeededForNextPolicy()
                if (cultureNeededForNextPolicy > storedCulture) throw Exception(
                    "Trying to adopt a policy without enough culture????"
                )
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

        //todo Can this be mapped downstream to a PolicyAction:NotificationAction?
        val triggerNotificationText = "due to adopting [${policy.name}]"
        for (unique in policy.uniqueObjects) {
            if (!unique.isTriggerable || unique.hasTriggerConditional() || !unique.conditionalsApply(civInfo.state))
                continue
            repeat(unique.getUniqueMultiplier(civInfo.state)) {
                UniqueTriggerActivation.triggerUnique(unique, civInfo, triggerNotificationText = triggerNotificationText)
            }
        }

        for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponAdoptingPolicyOrBelief) { it.params[0] == policy.name })
            UniqueTriggerActivation.triggerUnique(unique, civInfo, triggerNotificationText = triggerNotificationText)

        civInfo.cache.updateCivResources()

        // This ALSO has the side-effect of updating the CivInfo statForNextTurn so we don't need to call it explicitly
        for (city in civInfo.cities) {
            city.cityStats.update()
            city.reassignPopulationDeferred()
        }

        if (!canAdoptPolicy()) shouldOpenPolicyPicker = false
    }

    /**
     * @param branchCompletion Internal! Do not use in normal calls (used to recursively remove the "complete" object too)
     * @param assumeWasFree If set, removal does not touch culture progression (numberOfAdoptedPolicies not decremented)
     * @throws IllegalStateException when the given Policy is not adopted or when the removal would leave numberOfAdoptedPolicies negative
     */
    // Note: A policy gained as a free one is not marked as such, therefore we need the parameter
    // Note: a negative numberOfAdoptedPolicies would later throw in getCultureNeededForNextPolicy: -1.pow() gives NaN, which throws on toInt... Autosaved!
    fun removePolicy(policy: Policy, branchCompletion: Boolean = false, assumeWasFree: Boolean = false) {
        if (!adoptedPolicies.remove(policy.name))
            throw IllegalStateException("Attempt to remove non-adopted Policy ${policy.name}")

        if (!assumeWasFree && numberOfAdoptedPolicies > 0) {
            numberOfAdoptedPolicies -= 1
        }

        removePolicyFromTransients(policy)

        // if a branch is already marked as complete, revert it to incomplete
        if (!branchCompletion) {
            val branch = policy.branch
            if (branch.policies.count { isAdopted(it.name) } == branch.policies.size - 1) {
                removePolicy(branch.policies.last(), true)
            }
        }

        civInfo.cache.updateCivResources()

        // This ALSO has the side-effect of updating the CivInfo statForNextTurn so we don't need to call it explicitly
        for (city in civInfo.cities) {
            city.cityStats.update()
            city.reassignPopulationDeferred()
        }
    }

    /**
     * Return the highest priority ([Int]) among the given [Set] of [PolicyBranch]es.
     * Would return null if the given [Set] is empty.
     */
    @Readonly
    fun getMaxPriority(branchesToCompare: Set<PolicyBranch>): Int? {
        val filteredMap = priorityMap.filterKeys { branch -> branch in branchesToCompare }
        return filteredMap.values.maxOrNull()
    }

    @Readonly
    fun getCultureFromGreatWriter(): Int {
        return (cultureOfLast8Turns.sum() * civInfo.gameInfo.speed.cultureCostModifier).toInt()
    }

    private fun addCurrentCultureToCultureOfLast8Turns(culture: Int) {
        ensureCultureHistoryInitialized()
        cultureOfLast8Turns[civInfo.gameInfo.turns % 8] = culture
    }

    private fun ensureCultureHistoryInitialized() {
        if (cultureOfLast8Turns.size != 8) cultureOfLast8Turns = IntArray(8)
    }

    @Readonly
    fun allPoliciesAdopted(checkEra: Boolean) =
        getRulesetPolicies().values.none { isAdoptable(it, checkEra) }
}

package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.RuinReward
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit

/**
 * Expresses which RulesetObject types a UniqueType is applicable to, and manages automated checking of compliance.
 *
 * Compliance check is done through [isAllowedOnObject] and appropriate overrides.
 *
 * @param documentationString Copied to uniques.md by `UniqueDocsWriter`
 * @param inheritsFrom means that all such uniques are acceptable as well. For example, all Global uniques are acceptable for Nations, Eras, etc.
 * @param isModifier Marks types that are allowed _exclusively_ in a "<conditional>" style modifier, as standalone it is applicable to nothing.
 *
 * //todo implement "other half" of isModifier -> conditionals and triggers should always have it
 */
enum class UniqueTarget(
    val documentationString:String = "",
    val inheritsFrom: UniqueTarget? = null,
    val isModifier: Boolean = false
) {

    /** Only includes uniques that have immediate effects, caused by UniqueTriggerActivation */
    Triggerable("Uniques that have immediate, one-time effects. " +
        "These can be added to techs to trigger when researched, to policies to trigger when adpoted, " +
        "to eras to trigger when reached, to buildings to trigger when built. " +
        "Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event." +
        "They can also be added to units to grant them the ability to trigger this effect as an action, " +
        "which can be modified with UnitActionModifier and UnitTriggerCondition conditionals."),

    UnitTriggerable("Uniques that have immediate, one-time effects on a unit." +
        "They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, " +
        "which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.",
        inheritsFrom = Triggerable),

    Global("Uniques that apply globally. " +
        "Civs gain the abilities of these uniques from nation uniques, reached eras, researched techs, adopted policies, " +
        "built buildings, religion 'founder' uniques, owned resources, and ruleset-wide global uniques.",
        inheritsFrom = Triggerable),

    // Civilization-specific
    Nation(inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.nation.Nation
    },
    Era(inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.tech.Era
    },
    Tech(inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is Technology
    },
    Policy(inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.Policy
    },
    FounderBelief("Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion",
        inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is Belief && target.type.isFounder
    },
    FollowerBelief("Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion") {
        override fun isAllowedOnObject(target: IHasUniques) = target is Belief && target.type.isFollower
    },

    // City-specific
    Building(inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.Building
            // && !target.isAnyWonder() is redundant as long as UniqueTarget.Wonder inherits from this.
            // For the same reason we "should" call super.isAllowedOnObject but don't actually need to.
    },
    Wonder(inheritsFrom = Building) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.Building && target.isAnyWonder()
    },

    // Unit-specific
    Unit("Uniques that can be added to units, unit types, or promotions"
        , inheritsFrom = UnitTriggerable) {
        override fun isAllowedOnObject(target: IHasUniques) = target is BaseUnit || super.isAllowedOnObject(target)
    },
    UnitType(inheritsFrom = Unit) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.unit.UnitType
    },
    Promotion(inheritsFrom = Unit) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.unit.Promotion
    },

    // Tile-specific
    Terrain {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.tile.Terrain
    },
    Improvement {
        override fun isAllowedOnObject(target: IHasUniques) = target is TileImprovement
    },
    Resource(inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is TileResource
    },
    Ruins(inheritsFrom = UnitTriggerable) {
        override fun isAllowedOnObject(target: IHasUniques) = target is RuinReward
    },

    // Other
    Speed {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.Speed
    },
    Tutorial {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.Tutorial
    },
    CityState(inheritsFrom = Global) {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.nation.Nation && target.isCityState
    },
    ModOptions {
        override fun isAllowedOnObject(target: IHasUniques) = target is com.unciv.models.ruleset.ModOptions
    },

    // Modifiers (using Conditionals syntax)
    Conditional("Modifiers that can be added to other uniques to limit when they will be active",
        isModifier = true),
    TriggerCondition("Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.",
        inheritsFrom = Global, isModifier = true),
    UnitTriggerCondition("Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.",
        inheritsFrom = TriggerCondition, isModifier = true),
    UnitActionModifier("Modifiers that can be added to unit action uniques as conditionals",
        isModifier = true),
    ;

    fun canAcceptUniqueTarget(uniqueTarget: UniqueTarget): Boolean {
        if (this == uniqueTarget) return true
        if (inheritsFrom != null) return inheritsFrom.canAcceptUniqueTarget(uniqueTarget)
        return false
    }

    /** To be used in RulesetValidator:
     *  A Unique with a target type `this` was found on [target] - is that OK?
     *  (false is not necessarily final - the UniqueType may list other possible targets)
     *
     *  Note: Overrides **should** call (own decision) `|| super.isAllowedOnObject` - but that is only
     *  necessary if there actually **are** any UniqueTarget entries that [inherit from][inheritsFrom] the current instance.
     */
    open fun isAllowedOnObject(target: IHasUniques): Boolean {
        return !isModifier && values()
            .filter { it.inheritsFrom == this }
            .any { it.isAllowedOnObject(target) }
    }
}

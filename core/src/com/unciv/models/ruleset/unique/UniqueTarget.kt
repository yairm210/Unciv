package com.unciv.models.ruleset.unique

/**
 * Expresses which RulesetObject types a UniqueType is applicable to.
 *
 * @param documentationString Copied to uniques.md by `UniqueDocsWriter`
 * @param inheritsFrom means that all such uniques are acceptable as well. For example, all Global uniques are acceptable for Nations, Eras, etc.
 */
enum class UniqueTarget(val documentationString:String = "", val inheritsFrom: UniqueTarget? = null) {

    /** Only includes uniques that have immediate effects, caused by UniqueTriggerActivation */
    Triggerable("Uniques that have immediate, one-time effects. " +
        "These can be added to techs to trigger when researched, to policies to trigger when adopted, " +
        "to eras to trigger when reached, to buildings to trigger when built. " +
        "Alternatively, you can add a TriggerCondition to them to make them into Global uniques that activate upon a specific event." +
        "They can also be added to units to grant them the ability to trigger this effect as an action, " +
        "which can be modified with UnitActionModifier and UnitTriggerCondition conditionals."),

    UnitTriggerable("Uniques that have immediate, one-time effects on a unit." +
        "They can be added to units (on unit, unit type, or promotion) to grant them the ability to trigger this effect as an action, " +
        "which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.", Triggerable),

    Global("Uniques that apply globally. " +
        "Civs gain the abilities of these uniques from nation uniques, reached eras, researched techs, adopted policies, " +
        "built buildings, religion 'founder' uniques, owned resources, and ruleset-wide global uniques.", Triggerable),

    // Civilization-specific
    Nation(inheritsFrom = Global),
    Era(inheritsFrom = Global),
    Tech(inheritsFrom = Global),
    Policy(inheritsFrom = Global),
    FounderBelief("Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion", inheritsFrom = Global),
    FollowerBelief("Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion"),

    // City-specific
    Building(inheritsFrom = Global),
    Wonder(inheritsFrom = Building),

    // Unit-specific
    Unit("Uniques that can be added to units, unit types, or promotions", inheritsFrom = UnitTriggerable),
    UnitType(inheritsFrom = Unit),
    Promotion(inheritsFrom = Unit),

    // Tile-specific
    Terrain,
    Improvement,
    Resource(inheritsFrom = Global),
    Ruins(inheritsFrom = UnitTriggerable),

    // Other
    Speed,
    Tutorial,
    CityState(inheritsFrom = Global),
    ModOptions,

    // Modifiers
    Conditional("Modifiers that can be added to other uniques to limit when they will be active"),
    TriggerCondition("Special conditionals that can be added to Triggerable uniques, to make them activate upon specific actions.", inheritsFrom = Global),
    UnitTriggerCondition("Special conditionals that can be added to UnitTriggerable uniques, to make them activate upon specific actions.", inheritsFrom = TriggerCondition),
    UnitActionModifier("Modifiers that can be added to unit action uniques as conditionals"),
    ;

    /** Checks whether a specific UniqueTarget `this` as e.g. given by [IHasUniques.getUniqueTarget] works with [uniqueTarget] as e.g. declared in UniqueType */
    // Building.canAcceptUniqueTarget(Global) == true
    // Global.canAcceptUniqueTarget(Building) == false
    fun canAcceptUniqueTarget(uniqueTarget: UniqueTarget): Boolean {
        if (this == uniqueTarget) return true
        if (inheritsFrom != null) return inheritsFrom.canAcceptUniqueTarget(uniqueTarget)
        return false
    }
}

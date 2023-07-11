package com.unciv.models.ruleset.unique

import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.PolicyBranch
import com.unciv.models.ruleset.RuinReward
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import kotlin.reflect.KClass

/**
 * Expresses which RulesetObject types a UniqueType is applicable to, and manages automated checking of compliance.
 *
 * @param documentationString Copied to uniques.md by `UniqueDocsWriter`
 * @param inheritsFrom means that all such uniques are acceptable as well. For example, all Global uniques are acceptable for Nations, Eras, etc.
 * @param classes All (kotlin) classes this directly applies to. Indirect applicability is done through inheritsFrom.
 * @param additionalFilter If there's a match in classes, this can still forbid a target (e.g. uniques working on founder Beliefs won't on follower ones)
 * @param isModifier Marks types that are allowed _exclusively_ in a "<conditional>" style modifier, as standalone it is applicable to nothing.
 *
 * //todo implement "other half" of isModifier -> conditionals and triggers should always have it
 */
enum class UniqueTarget(
    val documentationString:String = "",
    val inheritsFrom: UniqueTarget? = null,
    val classes: List<KClass<out IHasUniques>> = emptyList(),
    val additionalFilter: ((target: IHasUniques) -> Boolean)? = null,
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
        "which can be modified with UnitActionModifier and UnitTriggerCondition conditionals.", Triggerable),

    Global("Uniques that apply globally. " +
        "Civs gain the abilities of these uniques from nation uniques, reached eras, researched techs, adopted policies, " +
        "built buildings, religion 'founder' uniques, owned resources, and ruleset-wide global uniques.", Triggerable),

    // Civilization-specific
    Nation(inheritsFrom = Global, classes = listOf(com.unciv.models.ruleset.nation.Nation::class)),
    Era(inheritsFrom = Global, classes = listOf(com.unciv.models.ruleset.tech.Era::class)),
    Tech(inheritsFrom = Global, classes = listOf(Technology::class)),
    Policy(inheritsFrom = Global, classes = listOf(com.unciv.models.ruleset.Policy::class, PolicyBranch::class)),
    FounderBelief("Uniques for Founder and Enhancer type Beliefs, that will apply to the founder of this religion",
        inheritsFrom = Global, classes = listOf(Belief::class),
        additionalFilter = { belief -> (belief as Belief).type.isFounder } ),
    FollowerBelief("Uniques for Pantheon and Follower type beliefs, that will apply to each city where the religion is the majority religion",
        classes = listOf(Belief::class),
        additionalFilter = { belief -> (belief as Belief).type.isFollower }),

    // City-specific
    Building(inheritsFrom = Global, classes = listOf(com.unciv.models.ruleset.Building::class),
        additionalFilter = { building -> !(building as com.unciv.models.ruleset.Building).isAnyWonder() }),
    Wonder(inheritsFrom = Building, classes = listOf(com.unciv.models.ruleset.Building::class),
        additionalFilter = { building -> (building as com.unciv.models.ruleset.Building).isAnyWonder() }),

    // Unit-specific
    Unit("Uniques that can be added to units, unit types, or promotions"
        , inheritsFrom = UnitTriggerable, classes = listOf(BaseUnit::class)),
    UnitType(inheritsFrom = Unit, classes = listOf(com.unciv.models.ruleset.unit.UnitType::class)),
    Promotion(inheritsFrom = Unit, classes = listOf(com.unciv.models.ruleset.unit.Promotion::class)),

    // Tile-specific
    Terrain(classes = listOf(com.unciv.models.ruleset.tile.Terrain::class)),
    Improvement(classes = listOf(TileImprovement::class)),
    Resource(inheritsFrom = Global, classes = listOf(TileResource::class)),
    Ruins(inheritsFrom = UnitTriggerable, classes = listOf(RuinReward::class)),

    // Other
    Speed(classes = listOf(com.unciv.models.ruleset.Speed::class)),
    Tutorial(classes = listOf(com.unciv.models.ruleset.Tutorial::class)),
    CityState(inheritsFrom = Global, classes = listOf(com.unciv.models.ruleset.nation.Nation::class)),
    ModOptions(classes = listOf(com.unciv.models.ruleset.ModOptions::class)),

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
     *  (false is not necessarily final - the UniqueType may list other possible targets) */
    fun isAllowedOnObject(target: IHasUniques): Boolean {
        if (isModifier) return false
        if (target::class in classes)
            // Don't collapse to one return: If additionalFilter says "No", inheritsFrom should be next!
            if (additionalFilter == null) return true
            else if (additionalFilter.invoke(target)) return true
        return values()
            .filter { it.inheritsFrom == this }
            .any { it.isAllowedOnObject(target) }
    }
}

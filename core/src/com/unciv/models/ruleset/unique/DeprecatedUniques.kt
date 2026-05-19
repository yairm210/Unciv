package com.unciv.models.ruleset.unique

import com.unciv.models.translations.getPlaceholderText
import yairm210.purity.annotations.Readonly

/**
 * Fully deprecated uniques that no longer work and have been removed from [UniqueType].
 *
 * These are kept here so that:
 * 1. [com.unciv.models.ruleset.validation.UniqueValidator] can still warn mod authors to update them.
 * 2. [com.unciv.models.ruleset.validation.UniqueAutoUpdater] can still auto-update them to current equivalents.
 *
 * Unlike [UniqueType] entries annotated with [DeprecationLevel.WARNING] ("half-deprecated" — still
 * functional), entries here no longer match any game logic and produce
 * [com.unciv.models.ruleset.validation.RulesetErrorSeverity.ErrorOptionsOnly] errors in the mod checker.
 *
 * ## Migrating an entry from UniqueType
 *
 * When a [UniqueType] entry reaches [DeprecationLevel.ERROR], cut it from UniqueType and paste it here
 * verbatim — the constructor signature is identical, so no edits are needed.
 *
 * ## Upgrade chains
 *
 * A deprecated unique's `@Deprecated.replaceWith.expression` may itself match another
 * [DeprecatedUniqueType] (or half-deprecated [UniqueType]) entry.
 * [com.unciv.models.ruleset.validation.UniqueAutoUpdater] must follow such chains to their end.
 * Example real chain:
 *
 *   [DecreasedBuildingMaintenanceDeprecated] → [BuildingMaintenanceOld] → [UniqueType.BuildingMaintenance]
 *
 * ## Integration requirements (this file alone is insufficient)
 *
 * ### Unique.kt
 * - Add `val deprecatedType: DeprecatedUniqueType?` looked up via [uniqueTypeMap].
 * - Extend `getDeprecationAnnotation()` to also return [getDeprecationAnnotation] from `deprecatedType`
 *   when `type` (the [UniqueType]) is null.
 * - Extend `getReplacementText()` to fall back to `deprecatedType!!.text` for placeholder-parameter
 *   extraction when `type` is null.
 *
 * ### UniqueAutoUpdater.kt
 * - The initial deprecated-unique filter must include uniques matched by [uniqueTypeMap].
 * - The upgrade-chain while-loop must also continue when the replacement text resolves to a
 *   [DeprecatedUniqueType] entry.
 *
 * ### UniqueValidator.kt
 * - `getDeprecationAnnotationErrors` must fire for [DeprecatedUniqueType] matches in addition to
 *   [UniqueType] `@Deprecated` matches.
 */
enum class DeprecatedUniqueType(
    val text: String,
    vararg targets: UniqueTarget, // retained for copy-paste compatibility; unused at runtime
    val flags: Set<UniqueFlag> = emptySet(), // retained for copy-paste compatibility; unused at runtime
    val docDescription: String? = null // retained for copy-paste compatibility; unused at runtime
) {

    ///////////////////////////////////////////// region 99 DEPRECATED AND REMOVED /////////////////////////////////////////////
    @Deprecated("As of 4.18.15", ReplaceWith("Receive [+200]% Gold from Barbarian encampments and pillaging Cities"), DeprecationLevel.ERROR)
    TripleGoldFromEncampmentsAndCities("Receive triple Gold from Barbarian encampments and pillaging Cities", UniqueTarget.Global),
    @Deprecated("As of 4.18.15", ReplaceWith("[+100]% Gold given to enemy if city is captured <in this city>"), DeprecationLevel.ERROR)
    DoublesGoldFromCapturingCity("Doubles Gold given to enemy if city is captured", UniqueTarget.Building),
    @Deprecated("As of 4.17.12", ReplaceWith("upon declaring war on [Major] Civilizations"), DeprecationLevel.ERROR)
    TriggerUponDeclaringWar("upon declaring war with a major Civilization", UniqueTarget.TriggerCondition),
    @Deprecated("As of 4.17.4", ReplaceWith("May Paradrop to [Land] tiles up to [positiveAmount] tiles away <in [{Friendly} {Land}] tiles>"), DeprecationLevel.ERROR)
    MayParadropOld("May Paradrop up to [positiveAmount] tiles from inside friendly territory", UniqueTarget.Unit),
    @Deprecated("As of 4.16.18", ReplaceWith("[relativeAmount]% [Strategic] resource production"), DeprecationLevel.ERROR)
    StrategicResourcesIncrease("Quantity of strategic resources produced by the empire +[relativeAmount]%", UniqueTarget.Global),  // used by Policies
    @Deprecated("As of 4.16.18", ReplaceWith("[+100]% [resource] resource production"), DeprecationLevel.ERROR)
    DoubleResourceProduced("Double quantity of [resource] produced", UniqueTarget.Global),
    @Deprecated(message = "as of 4.16.13", ReplaceWith("[relativeAmount]% maintenance cost for [all] buildings [cityFilter]"), level = DeprecationLevel.ERROR)
    BuildingMaintenanceOld("[relativeAmount]% maintenance cost for buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 4.16.14", ReplaceWith("Removes extra unhappiness from annexed cities"), DeprecationLevel.ERROR)
    RemoveAnnexUnhappiness("Remove extra unhappiness from annexed cities", UniqueTarget.Building),
    @Deprecated("As of 4.16.14", ReplaceWith("[relativeAmount]% Strength <when stacked with a [mapUnitFilter] unit>"), DeprecationLevel.ERROR)
    StrengthWhenStacked("[relativeAmount]% Strength when stacked with [mapUnitFilter]", UniqueTarget.Unit),  // candidate for conditional!
    @Deprecated("As of 4.16.18", ReplaceWith("when between [amount] and [amount] [Happiness]"), DeprecationLevel.ERROR)
    ConditionalBetweenHappiness("when between [amount] and [amount] Happiness", UniqueTarget.Conditional,
        docDescription = " 'Between' is inclusive - so 'between 1 and 5' includes 1 and 5."),
    @Deprecated("As of 4.16.18", ReplaceWith("when above [amount] [Happiness]"), DeprecationLevel.ERROR)
    ConditionalAboveHappiness("when above [amount] Happiness", UniqueTarget.Conditional),
    @Deprecated("As of 4.16.18", ReplaceWith("when below [amount] [Happiness]"), DeprecationLevel.ERROR)
    ConditionalBelowHappiness("when below [amount] Happiness", UniqueTarget.Conditional),
    @Deprecated("As of 4.16.0", ReplaceWith("Unavailable <when number of [Completed Policy branches] is less than [amount]>"), DeprecationLevel.ERROR)
    HiddenBeforeAmountPolicies("Hidden until [amount] social policy branches have been completed", UniqueTarget.Building, UniqueTarget.Unit),
    @Deprecated("As of 4.15.11", ReplaceWith("New [baseUnitFilter] units start with [amount] XP [cityFilter]"), DeprecationLevel.ERROR)
    UnitStartingExperienceOld("New [baseUnitFilter] units start with [amount] Experience [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("As of 4.15.2", ReplaceWith("Can spend Gold to annex or puppet a City-State that has been your Ally for [amount] turns"), DeprecationLevel.ERROR)
    CityStateCanBeBoughtForGoldOld("Can spend Gold to annex or puppet a City-State that has been your ally for [amount] turns.", UniqueTarget.Global),
    @Deprecated("As of 4.14.6", ReplaceWith("[+100]% Strength <when defending> <when [Embarked]>"), DeprecationLevel.ERROR)
    DefenceBonusWhenEmbarked("Defense bonus when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("As of 4.13.18", ReplaceWith("Only available <when [victoryType] Victory is enabled>"), DeprecationLevel.ERROR)
    HiddenWithoutVictoryType("Hidden when [victoryType] Victory is disabled", UniqueTarget.Building, UniqueTarget.Unit, flags = UniqueFlag.setOfHiddenToUsers),
    @Deprecated("As of 4.13.18", ReplaceWith("Only available <when religion is enabled>"), DeprecationLevel.ERROR)
    HiddenWithoutReligion("Hidden when religion is disabled", UniqueTarget.Unit, UniqueTarget.Building, UniqueTarget.Ruins, UniqueTarget.Tutorial, flags = UniqueFlag.setOfHiddenToUsers),
    @Deprecated("As of 4.13.19", ReplaceWith("Only available <when espionage is enabled>"), DeprecationLevel.ERROR)
    HiddenWithoutEspionage("Hidden when espionage is disabled", UniqueTarget.Building, flags = UniqueFlag.setOfHiddenToUsers),
    @Deprecated("As of 4.13.15", ReplaceWith("for [civFilter] Civilizations"), DeprecationLevel.ERROR)
    ConditionalCivFilterOld("for [civFilter]", UniqueTarget.Conditional),
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] gains [amount] movement"), DeprecationLevel.ERROR)
    OneTimeUnitGainMovementOld("This Unit gains [amount] movement", UniqueTarget.UnitTriggerable),
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] loses the [promotion] promotion"), DeprecationLevel.ERROR)
    OneTimeUnitRemovePromotionOld("This Unit loses the [promotion] promotion", UniqueTarget.UnitTriggerable),
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] gains the [promotion] promotion"), DeprecationLevel.ERROR)
    OneTimeUnitGainPromotionOld("This Unit gains the [promotion] promotion", UniqueTarget.UnitTriggerable),  // Not used in Vanilla
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] upgrades for free including special upgrades"), DeprecationLevel.ERROR)
    OneTimeUnitSpecialUpgradeOld("This Unit upgrades for free including special upgrades", UniqueTarget.UnitTriggerable),
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] heals [positiveAmount] HP"), DeprecationLevel.ERROR)
    OneTimeUnitHealOld("Heal this unit by [positiveAmount] HP", UniqueTarget.UnitTriggerable),
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] takes [positiveAmount] damage"), DeprecationLevel.ERROR)
    OneTimeUnitDamageOld("This Unit takes [positiveAmount] damage", UniqueTarget.UnitTriggerable),
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] gains [amount] XP"), DeprecationLevel.ERROR)
    OneTimeUnitGainXPOld("This Unit gains [amount] XP", UniqueTarget.UnitTriggerable),
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] upgrades for free"), DeprecationLevel.ERROR)
    OneTimeUnitUpgradeOld("This Unit upgrades for free", UniqueTarget.UnitTriggerable),  // Not used in Vanilla
    @Deprecated("As of 4.13.2", ReplaceWith("[This Unit] loses [amount] movement"), DeprecationLevel.ERROR)
    OneTimeUnitLoseMovementOld("This Unit loses [amount] movement", UniqueTarget.UnitTriggerable),
    @Deprecated("As of 4.12.19", ReplaceWith("Neighboring tiles will convert to [baseTerrain/terrainFeature] <in tiles without [simpleTerrain]>"), DeprecationLevel.ERROR)
    NaturalWonderConvertNeighborsExcept("Neighboring tiles except [simpleTerrain] will convert to [baseTerrain/terrainFeature]", UniqueTarget.Terrain, flags = UniqueFlag.setOfHiddenToUsers,
        docDescription = "Supports conditionals that need only a Tile as context and nothing else, like `<with [n]% chance>`, and applies them per neighbor." +
                "\nIf your mod renames Coast or Lakes, do not use this with one of these as parameter, as the code preventing artifacts won't work."),
    @Deprecated("As of 4.12.16", ReplaceWith("in tiles adjacent to [tileFilter] tiles"), DeprecationLevel.ERROR)
    ConditionalAdjacentToOld("in tiles adjacent to [tileFilter]", UniqueTarget.Conditional),
    @Deprecated("As of 4.12.16", ReplaceWith("in tiles not adjacent to [tileFilter] tiles"), DeprecationLevel.ERROR)
    ConditionalNotAdjacentToOld("in tiles not adjacent to [tileFilter]", UniqueTarget.Conditional),
    @Deprecated("As of 4.12.15", ReplaceWith("Gain control over [all] tiles in a [1]-tile radius"), DeprecationLevel.ERROR)
    TakesOverAdjacentTiles("Constructing it will take over the tiles around it and assign them to your closest city", UniqueTarget.Improvement),

    @Deprecated("As of 4.12.4", ReplaceWith("No damage penalty for wounded units"), DeprecationLevel.ERROR)
    NoDamagePenalty("Damage is ignored when determining unit Strength", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("As of 4.12.4", ReplaceWith("Withdraws before melee combat <with [amount]% chance>"), DeprecationLevel.ERROR)
    MayWithdraw("May withdraw before melee ([amount]%)", UniqueTarget.Unit),
    @Deprecated("As of 4.12.3", ReplaceWith("when number of [countable] is more than [countable]"), DeprecationLevel.ERROR)
    ConditionalCountableGreaterThan("when number of [countable] is greater than [countable]", UniqueTarget.Conditional),
    @Deprecated("As of 4.12.3", ReplaceWith("before turn number [amount]"), DeprecationLevel.ERROR)
    ConditionalBeforeTurnsOld("before [amount] turns", UniqueTarget.Conditional),
    @Deprecated("As of 4.12.3", ReplaceWith("after turn number [amount]"), DeprecationLevel.ERROR)
    ConditionalAfterTurnsOld("after [amount] turns", UniqueTarget.Conditional),
    @Deprecated("As of 4.11.19", ReplaceWith("Gain [100] [Gold] <upon expending a [Great Person] unit> <(modified by game speed)>"), DeprecationLevel.ERROR)
    ProvidesGoldWheneverGreatPersonExpended("Provides a sum of gold each time you spend a Great Person", UniqueTarget.Global),
    @Deprecated("As of 4.11.19", ReplaceWith("Gain [amount] [stat] <upon expending a [Great Person] unit> <(modified by game speed)>"), DeprecationLevel.ERROR)
    ProvidesStatsWheneverGreatPersonExpended("[stats] whenever a Great Person is expended", UniqueTarget.Global),
    @Deprecated("As of 4.11.18", ReplaceWith("Gain [amount] [stat] <(modified by game speed)>"), DeprecationLevel.ERROR)
    OneTimeGainStatSpeed("Gain [amount] [stat] (modified by game speed)", UniqueTarget.Triggerable),
    @Deprecated("As of 4.11.18", ReplaceWith("when above [amount] [stat/resource] <(modified by game speed)>"), DeprecationLevel.ERROR)
    ConditionalWhenAboveAmountStatResourceSpeed("when above [amount] [stat/resource] (modified by game speed)", UniqueTarget.Conditional),
    @Deprecated("As of 4.11.18", ReplaceWith("when below [amount] [stat/resource] <(modified by game speed)>"), DeprecationLevel.ERROR)
    ConditionalWhenBelowAmountStatResourceSpeed("when below [amount] [stat/resource] (modified by game speed)", UniqueTarget.Conditional),
    @Deprecated("As of 4.11.18", ReplaceWith("when between [amount] and [amount] [stat/resource] <(modified by game speed)>"), DeprecationLevel.ERROR)
    ConditionalWhenBetweenStatResourceSpeed("when between [amount] and [amount] [stat/resource] (modified by game speed)", UniqueTarget.Conditional),
    @Deprecated("As of 4.11.18", ReplaceWith("[stats] when a city adopts this religion for the first time <(modified by game speed)>"), DeprecationLevel.ERROR)
    StatsWhenAdoptingReligionSpeed("[stats] when a city adopts this religion for the first time (modified by game speed)", UniqueTarget.Global),
    @Deprecated("As of 4.10.17", ReplaceWith("Grants [+500 Gold] to the first civilization to discover it"), DeprecationLevel.ERROR)
    GrantsGoldToFirstToDiscover("Grants 500 Gold to the first civilization to discover it", UniqueTarget.Terrain),
    @Deprecated("as of 4.10.17", ReplaceWith("[+100 Gold] for discovering a Natural Wonder (bonus enhanced to [+500 Gold] if first to discover it)"), DeprecationLevel.ERROR)
    GoldWhenDiscoveringNaturalWonder("100 Gold for discovering a Natural Wonder (bonus enhanced to 500 Gold if first to discover it)", UniqueTarget.Global),
    @Deprecated("as of 4.10.17", ReplaceWith("Only available <if [buildingFilter] is constructed in all [non-[Puppeted]] cities>"), DeprecationLevel.ERROR)
    RequiresBuildingInAllCities("Requires a [buildingFilter] in all cities", UniqueTarget.Building),
    @Deprecated("as of 4.10.17", ReplaceWith("Only available <if [buildingFilter] is constructed in at least [positiveAmount] of [All] cities>"), DeprecationLevel.ERROR)
    RequiresBuildingInSomeCities("Requires a [buildingFilter] in at least [positiveAmount] cities", UniqueTarget.Building),
    @Deprecated("as of 4.10.18", ReplaceWith("Can only be built <in [cityFilter] cities>"), DeprecationLevel.ERROR)
    CanOnlyBeBuiltInCertainCities("Can only be built [cityFilter]", UniqueTarget.Building),
    @Deprecated("as of 4.10.15", ReplaceWith("upon discovering [tech] technology"), DeprecationLevel.ERROR)
    TriggerUponResearchOld("upon discovering [tech]", UniqueTarget.TriggerCondition),
    @Deprecated("as of 4.10.3", ReplaceWith("[+30]% Strength <vs [City-States]>"), DeprecationLevel.ERROR)
    StrengthBonusVsCityStates("+30% Strength when fighting City-State units and cities", UniqueTarget.Global),
    @Deprecated("as of 4.10.3", ReplaceWith("with [amount] to [amount] neighboring [{tileFilter} {tileFilter}] tiles"), DeprecationLevel.ERROR)
    ConditionalNeighborTilesAnd("with [amount] to [amount] neighboring [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),
    @Deprecated("as of 4.10.3", ReplaceWith("in [{tileFilter} {tileFilter}] tiles"), DeprecationLevel.ERROR)
    ConditionalInTilesAnd("in [tileFilter] [tileFilter] tiles", UniqueTarget.Conditional),
    @Deprecated("as of 4.10.3", ReplaceWith("Unavailable <after generating a Great Prophet>"), DeprecationLevel.ERROR)
    HiddenAfterGreatProphet("Hidden after generating a Great Prophet", UniqueTarget.Ruins),
    @Deprecated("as of 4.9.0",ReplaceWith("[relativeAmount]% construction time for [All] improvements"), DeprecationLevel.ERROR)
    TileImprovementTime("[relativeAmount]% tile improvement construction time", UniqueTarget.Global, UniqueTarget.Unit),
    @Deprecated("as of 4.8.9", ReplaceWith("Can Spread Religion <[amount] times> <after which this unit is consumed>\" OR \"Can remove other religions from cities <in [Friendly] tiles> <once> <after which this unit is consumed>"), DeprecationLevel.ERROR)
    CanActionSeveralTimes("Can [action] [amount] times", UniqueTarget.Unit),
    @Deprecated("as of 4.8.9", ReplaceWith("All newly-trained [baseUnitFilter] units [cityFilter] receive the [Devout] promotion"), DeprecationLevel.ERROR)
    UnitStartingActions("[baseUnitFilter] units built [cityFilter] can [action] [amount] extra times", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 4.8.5", ReplaceWith("Free [unit] appears <upon discovering [tech]>"), DeprecationLevel.ERROR)
    ReceiveFreeUnitWhenDiscoveringTech("Receive free [unit] when you discover [tech]", UniqueTarget.Global),
    @Deprecated("as of 4.7.3", ReplaceWith("[+100]% unhappiness from the number of cities"), DeprecationLevel.ERROR)
    UnhappinessFromCitiesDoubled("Unhappiness from number of Cities doubled", UniqueTarget.Global),
    @Deprecated("as of 4.6.4", ReplaceWith("[+1] Sight <for [Embarked] units>\" OR \"[+1] Sight <when [Embarked]>"), DeprecationLevel.ERROR)
    NormalVisionWhenEmbarked("Normal vision when embarked", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 4.5.3", ReplaceWith("Empire enters a [amount]-turn Golden Age <by consuming this unit>"), DeprecationLevel.ERROR)
    StartGoldenAge("Can start an [amount]-turn golden age", UniqueTarget.Unit),
    @Deprecated("as of 4.5.2", ReplaceWith("Can instantly construct a [improvementName] improvement <by consuming this unit>"), DeprecationLevel.ERROR)
    ConstructImprovementConsumingUnit("Can construct [improvementName]", UniqueTarget.Unit),
    @Deprecated("as of 4.3.9", ReplaceWith("Costs [amount] [stats] per turn when in your territory"), DeprecationLevel.ERROR)
    OldImprovementMaintenance("Costs [amount] gold per turn when in your territory", UniqueTarget.Improvement),
    @Deprecated("as of 4.3.4", ReplaceWith("[+1 Happiness] per [2] social policies adopted"), DeprecationLevel.ERROR)
    HappinessPer2Policies("Provides 1 happiness per 2 additional social policies adopted", UniqueTarget.Global),
    @Deprecated("as of 4.3.6", ReplaceWith("[+1 Happiness] for every known Natural Wonder"), DeprecationLevel.ERROR)
    DoubleHappinessFromNaturalWonders("Double Happiness from Natural Wonders", UniqueTarget.Global),
    @Deprecated("as of 4.2.18", ReplaceWith("Only available <after [amount] turns>"), DeprecationLevel.ERROR)
    AvailableAfterCertainTurns("Only available after [amount] turns", UniqueTarget.Ruins),
    @Deprecated("as of 4.2.18", ReplaceWith("Only available <before founding a Pantheon>"), DeprecationLevel.ERROR)
    HiddenBeforePantheon("Hidden before founding a Pantheon", UniqueTarget.Ruins),
    @Deprecated("as of 4.2.18", ReplaceWith("Only available <before founding a Pantheon>"), DeprecationLevel.ERROR)
    HiddenAfterPantheon("Hidden after founding a Pantheon", UniqueTarget.Ruins),
    @Deprecated("as of 4.3.4", ReplaceWith("[stats]"), DeprecationLevel.ERROR)
    CityStateStatsPerTurn("Provides [stats] per turn", UniqueTarget.CityState), // Should not be Happiness!
    @Deprecated("as of 4.3.4", ReplaceWith("[stats] [cityFilter]"), DeprecationLevel.ERROR)
    CityStateStatsPerCity("Provides [stats] [cityFilter] per turn", UniqueTarget.CityState),
    @Deprecated("as of 4.3.4", ReplaceWith("[+amount Happiness]"), DeprecationLevel.ERROR)
    CityStateHappiness("Provides [amount] Happiness", UniqueTarget.CityState),
    @Deprecated("as of 4.2.4", ReplaceWith("Enemy [Land] units must spend [1] extra movement points when inside your territory <before discovering [Dynamite]>"), DeprecationLevel.ERROR)
    EnemyLandUnitsSpendExtraMovementDepreciated("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)", UniqueTarget.Global),
    @Deprecated("as of 4.1.7", ReplaceWith("Can construct [improvementName] <if it hasn't used other actions yet>"), DeprecationLevel.ERROR)
    CanConstructIfNoOtherActions("Can construct [improvementName] if it hasn't used other actions yet", UniqueTarget.Unit),
    @Deprecated("as of 4.1.14", ReplaceWith("Production to [Science] conversion in cities changed by [33]%"), DeprecationLevel.ERROR)
    ProductionToScienceConversionBonus("Production to science conversion in cities increased by 33%", UniqueTarget.Global),
    @Deprecated("as of 4.1.19", ReplaceWith("[+100]% Yield from every [Natural Wonder]"), DeprecationLevel.ERROR)
    DoubleStatsFromNaturalWonders("Tile yields from Natural Wonders doubled", UniqueTarget.Global),
    @Deprecated("as of 4.1.14", ReplaceWith("Enables conversion of city production to [Gold]"), DeprecationLevel.ERROR)
    EnablesGoldProduction("Enables conversion of city production to gold", UniqueTarget.Global),
    @Deprecated("as of 4.1.14", ReplaceWith("Enables conversion of city production to [Science]"), DeprecationLevel.ERROR)
    EnablesScienceProduction("Enables conversion of city production to science", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("Damage is ignored when determining unit Strength <for [All] units>"), DeprecationLevel.ERROR)
    UnitsFightFullStrengthWhenDamaged("Units fight as though they were at full strength even when damaged", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("[+amount]% Strength <within [amount2] tiles of a [tileFilter]>"), DeprecationLevel.ERROR)
    StrengthWithinTilesOfTile("+[amount]% Strength if within [amount2] tiles of a [tileFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.19.7", ReplaceWith("[stats] <with [resource]>"), DeprecationLevel.ERROR)
    StatsWithResource("[stats] with [resource]", UniqueTarget.Building),
    @Deprecated("as of 3.19.16", ReplaceWith("Can only be built <in [Annexed] cities>"), DeprecationLevel.ERROR)
    CanOnlyBeBuiltInAnnexedCities("Can only be built in annexed cities", UniqueTarget.Building),
    @Deprecated("as of 4.0.3", ReplaceWith("Defense bonus when embarked <for [All] units>"), DeprecationLevel.ERROR)
    DefenceBonusWhenEmbarkedCivwide("Embarked units can defend themselves", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("[1] Movement point cost to disembark <for [All] units>"), DeprecationLevel.ERROR)
    DisembarkCostDeprecated("Units pay only 1 movement point to disembark", UniqueTarget.Global),

    @Deprecated("as of 4.0.3", ReplaceWith("When conquering an encampment, earn [25] Gold and recruit a Barbarian unit <with [67]% chance>"), DeprecationLevel.ERROR)
    ChanceToRecruitBarbarianFromEncampment("67% chance to earn 25 Gold and recruit a Barbarian unit from a conquered encampment", UniqueTarget.Global),
    @Deprecated("as of 4.0.3", ReplaceWith("When defeating a [{Barbarian} {Water}] unit, earn [25] Gold and recruit it <with [50]% chance>"), DeprecationLevel.ERROR)
    ChanceToRecruitNavalBarbarian("50% chance of capturing defeated Barbarian naval units and earning 25 Gold", UniqueTarget.Global),

    @Deprecated("as of 3.19.8", ReplaceWith("Eliminates combat penalty for attacking across a coast"), DeprecationLevel.ERROR)
    AttackFromSea("Eliminates combat penalty for attacking from the sea", UniqueTarget.Unit),
    @Deprecated("as of 3.19.19", ReplaceWith("[+4] Sight\", \"Can see over obstacles"), DeprecationLevel.ERROR)
    SixTilesAlwaysVisible("6 tiles in every direction always visible", UniqueTarget.Unit),
    @Deprecated("as of 3.19.19", ReplaceWith("[25]% Chance to be destroyed by nukes"), DeprecationLevel.ERROR)
    ResistsNukes("Resistant to nukes", UniqueTarget.Terrain),
    @Deprecated("as of 3.19.19", ReplaceWith("[50]% Chance to be destroyed by nukes"), DeprecationLevel.ERROR)
    DestroyableByNukes("Can be destroyed by nukes", UniqueTarget.Terrain),
    @Deprecated("as of 3.19.19", ReplaceWith("in cities with at least [amount] [Specialists]"), DeprecationLevel.ERROR)
    ConditionalSpecialistCount("if this city has at least [amount] specialists", UniqueTarget.Conditional),
    @Deprecated("as of 3.19.19", ReplaceWith("in cities with at least [amount] [Followers of the Majority Religion]"), DeprecationLevel.ERROR)
    ConditionalFollowerCount("in cities where this religion has at least [amount] followers", UniqueTarget.Conditional),
    @Deprecated("as of 3.19.8", ReplaceWith("[+amount]% Strength <when attacking> <for [mapUnitFilter] units> <for [amount2] turns>"), DeprecationLevel.ERROR)
    TimedAttackStrength("+[amount]% attack strength to all [mapUnitFilter] units for [amount2] turns", UniqueTarget.Global),  // used in Policy
    @Deprecated("as of 3.19.13", ReplaceWith("Enables [Embarked] units to enter ocean tiles <starting from the [Ancient era]>"), DeprecationLevel.ERROR)
    EmbarkedUnitsMayEnterOcean("Enables embarked units to enter ocean tiles", UniqueTarget.Global),
    @Deprecated("as of 3.19.9", ReplaceWith("Enables embarkation for land units <starting from the [Ancient era]>\", \"Enables [All] units to enter ocean tiles <starting from the [Ancient era]>"), DeprecationLevel.ERROR)
    EmbarkAndEnterOcean("Can embark and move over Coasts and Oceans immediately", UniqueTarget.Global),
    @Deprecated("as of 3.19.19", ReplaceWith("[relativeAmount]% Unhappiness from [Population] [cityFilter]"), DeprecationLevel.ERROR)
    UnhappinessFromPopulationPercentageChange("[relativeAmount]% unhappiness from population [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.19", ReplaceWith("[relativeAmount]% Unhappiness from [Specialists] [cityFilter]"), DeprecationLevel.ERROR)
    UnhappinessFromSpecialistsPercentageChange("[relativeAmount]% unhappiness from specialists [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.19", ReplaceWith("[relativeAmount]% Great Person generation [cityFilter]"), DeprecationLevel.ERROR)
    GreatPersonPointPercentageDeprecated("[relativeAmount]% great person generation [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.19", ReplaceWith("[+25]% [Gold] from Trade Routes"), DeprecationLevel.ERROR)
    GoldBonusFromTradeRoutesDeprecated("Gold from all trade routes +25%", UniqueTarget.Global),
    @Deprecated("as of 3.19.19", ReplaceWith("[stats] <in cities with at least [amount] [Population]>"), DeprecationLevel.ERROR)
    StatsFromXPopulation("[stats] in cities with [amount] or more population", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.8", ReplaceWith("Only available <before adopting [policy/tech/promotion]>" +
            "\" OR \"Only available <before discovering [policy/tech/promotion]>" +
            "\" OR \"Only available <for units without [policy/tech/promotion]>"), DeprecationLevel.ERROR)
    IncompatibleWith("Incompatible with [policy/tech/promotion]", UniqueTarget.Policy, UniqueTarget.Tech, UniqueTarget.Promotion),
    @Deprecated("as of 3.19.8", ReplaceWith("Only available <after adopting [buildingName/tech/resource/policy]>\"" +
            " OR \"Only available <with [buildingName/tech/resource/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/resource/policy] is constructed>\"" +
            " OR \"Only available <after discovering [buildingName/tech/resource/policy]>"), DeprecationLevel.ERROR)
    NotDisplayedWithout("Not displayed as an available construction without [buildingName/tech/resource/policy]", UniqueTarget.Building, UniqueTarget.Unit),

    @Deprecated("as of 3.19.12", ReplaceWith("Only available <after adopting [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/era/policy] is constructed>\"" +
            " OR \"Only available <starting from the [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <after discovering [buildingName/tech/era/policy]>"), DeprecationLevel.ERROR)
    UnlockedWith("Unlocked with [buildingName/tech/era/policy]", UniqueTarget.Building, UniqueTarget.Unit),


    @Deprecated("as of 3.19.12", ReplaceWith("Only available <after adopting [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <if [buildingName/tech/era/policy] is constructed>\"" +
            " OR \"Only available <starting from the [buildingName/tech/era/policy]>\"" +
            " OR \"Only available <after discovering [buildingName/tech/era/policy]>"), DeprecationLevel.ERROR)
    Requires("Requires [buildingName/tech/era/policy]", UniqueTarget.Building, UniqueTarget.Unit),
    @Deprecated("as of 3.19.9", ReplaceWith("Only available <in cities without a [buildingName]>"), DeprecationLevel.ERROR)
    CannotBeBuiltWith("Cannot be built with [buildingName]", UniqueTarget.Building),
    @Deprecated("as of 3.19.9", ReplaceWith("Only available <in cities with a [buildingName]>"), DeprecationLevel.ERROR)
    RequiresAnotherBuilding("Requires a [buildingName] in this city", UniqueTarget.Building),


    @Deprecated("as of 4.1.0", ReplaceWith("[+15]% Strength bonus for [Military] units within [2] tiles"), DeprecationLevel.ERROR)
    BonusForUnitsInRadius("Bonus for units in 2 tile radius 15%", UniqueTarget.Unit),
    @Deprecated("as of 4.0.15", ReplaceWith("Irremovable"), DeprecationLevel.ERROR)
    Indestructible("Indestructible", UniqueTarget.Improvement),

    @Deprecated("as of 3.19.1", ReplaceWith("[stats] from every [Wonder]"), DeprecationLevel.ERROR)
    StatsFromWondersDeprecated("[stats] from every Wonder", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.3", ReplaceWith("[stats] from every [buildingFilter] <in cities where this religion has at least [amount] followers>"), DeprecationLevel.ERROR)
    StatsForBuildingsWithFollowers("[stats] from every [buildingFilter] in cities where this religion has at least [amount] followers", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.3", ReplaceWith("[+25]% Production towards any buildings that already exist in the Capital"), DeprecationLevel.ERROR)
    PercentProductionBuildingsInCapitalDeprecated("+25% Production towards any buildings that already exist in the Capital", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.2", ReplaceWith("[amount]% Food is carried over after population increases [in this city]"), DeprecationLevel.ERROR)
    CarryOverFoodDeprecated("[amount]% of food is carried over after population increases", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.2", ReplaceWith("[amount]% Food is carried over after population increases [cityFilter]"), DeprecationLevel.ERROR)
    CarryOverFoodAlsoDeprecated("[amount]% of food is carried over [cityFilter] after population increases", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.2", ReplaceWith("[amount]% Culture cost of natural border growth [cityFilter]"), DeprecationLevel.ERROR)
    BorderGrowthPercentageWithoutPercentageSign("[amount] Culture cost of natural border growth [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[-amount]% Culture cost of natural border growth [cityFilter]"), DeprecationLevel.ERROR)
    DecreasedAcquiringTilesCost("-[amount]% Culture cost of acquiring tiles [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[amount]% Culture cost of natural border growth [in all cities]"), DeprecationLevel.ERROR)
    CostOfNaturalBorderGrowth("[amount]% cost of natural border growth", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[-amount]% Gold cost of acquiring tiles [cityFilter]"), DeprecationLevel.ERROR)
    TileCostPercentageDiscount("-[amount]% Gold cost of acquiring tiles [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.3", ReplaceWith("[stat] cost of purchasing [baseUnitFilter] units [amount]%"), DeprecationLevel.ERROR)
    BuyUnitsDiscountDeprecated("[stat] cost of purchasing [baseUnitFilter] units in cities [amount]%", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.1", ReplaceWith("[+amount]% Strength for cities <with a garrison> <when attacking>"), DeprecationLevel.ERROR)
    StrengthForGarrisonedCitiesAttacking("+[amount]% attacking strength for cities with garrisoned units", UniqueTarget.Global),
    @Deprecated("as of 3.19.2", ReplaceWith("Population loss from nuclear attacks [-amount]% [in this city]"), DeprecationLevel.ERROR)
    PopulationLossFromNukesDeprecated("Population loss from nuclear attacks -[amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.19.3", ReplaceWith("[amount]% Natural religion spread [cityFilter] <after discovering [tech/policy]>\"" +
            " OR \"[amount]% Natural religion spread [cityFilter] <after adopting [tech/policy]>"), DeprecationLevel.ERROR)
    NaturalReligionSpreadStrengthWith("[amount]% Natural religion spread [cityFilter] with [tech/policy]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.19.4", ReplaceWith("[amount] HP when healing <in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    HealInTiles("[amount] HP when healing in [tileFilter] tiles", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("No movement cost to pillage <for [Melee] units>"), DeprecationLevel.ERROR)
    NoMovementToPillageMelee("Melee units pay no movement cost to pillage", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.19.3", ReplaceWith("All adjacent units heal [+15] HP when healing"), DeprecationLevel.ERROR)
    HealAdjacentUnitsDeprecated("Heal adjacent units for an additional 15 HP per turn", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("Adjacent enemy units ending their turn take [amount] damage"), DeprecationLevel.ERROR)
    DamagesAdjacentEnemyUnitsOld("Deal [amount] damage to adjacent enemy units", UniqueTarget.Improvement),


    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Golden Age length"), DeprecationLevel.ERROR)
    GoldenAgeLengthIncreased("Golden Age length increased by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Strength for cities <when defending>"), DeprecationLevel.ERROR)
    StrengthForCitiesDefending("+[amount]% Defensive Strength for cities", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Strength for cities <when attacking>"), DeprecationLevel.ERROR)
    StrengthForCitiesAttacking("[amount]% Attacking Strength for cities", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units> <when adjacent to a [mapUnitFilter] unit>"), DeprecationLevel.ERROR)
    StrengthFromAdjacentUnits("[amount]% Strength for [mapUnitFilter] units which have another [mapUnitFilter] unit in an adjacent tile", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% Gold cost of upgrading <for [baseUnitFilter] units>"), DeprecationLevel.ERROR)
    ReducedUpgradingGoldCost("Gold cost of upgrading [baseUnitFilter] units reduced by [amount]%", UniqueTarget.Unit, UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+100]% Gold from Great Merchant trade missions"), DeprecationLevel.ERROR)
    DoubleGoldFromTradeMissions("Double gold from Great Merchant trade missions", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+25]% City Strength from defensive buildings"), DeprecationLevel.ERROR)
    DefensiveBuilding25("Defensive buildings in all cities are 25% more effective", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% maintenance on road & railroads"), DeprecationLevel.ERROR)
    DecreasedRoadMaintenanceDeprecated("Maintenance on roads & railroads reduced by [amount]%", UniqueTarget.Global),
    // DecreasedBuildingMaintenanceDeprecated → BuildingMaintenanceOld is a real two-hop upgrade chain:
    // the replacement placeholder matches BuildingMaintenanceOld.text → BuildingMaintenanceOld → BuildingMaintenance.
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% maintenance cost for buildings [cityFilter]"), DeprecationLevel.ERROR)
    DecreasedBuildingMaintenanceDeprecated("-[amount]% maintenance cost for buildings [cityFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount] Happiness from each type of luxury resource"), DeprecationLevel.ERROR)
    BonusHappinessFromLuxuryDeprecated("+[amount] happiness from each type of luxury resource", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% Culture cost of adopting new Policies"), DeprecationLevel.ERROR)
    LessPolicyCostDeprecated("Culture cost of adopting new Policies reduced by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.19.1", ReplaceWith("[amount]% Culture cost of adopting new Policies"), DeprecationLevel.ERROR)
    LessPolicyCostDeprecated2("[amount]% Culture cost of adopting new policies", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% resources gifted by City-States"), DeprecationLevel.ERROR)
    CityStateResourcesDeprecated("Quantity of Resources gifted by City-States increased by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[-amount]% City-State Influence degradation"), DeprecationLevel.ERROR)
    CityStateInfluenceDegradationDeprecated("City-State Influence degrades [amount]% slower", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Happiness from luxury resources gifted by City-States"), DeprecationLevel.ERROR)
    CityStateLuxuryHappinessDeprecated("Happiness from Luxury Resources gifted by City-States increased by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% [stat] from every [tileFilter/specialist/buildingName]"), DeprecationLevel.ERROR)
    StatPercentSignedFromObject("+[amount]% [stat] from every [tileFilter/specialist/buildingName]", UniqueTarget.Global, UniqueTarget.FollowerBelief),
    @Deprecated("as of 3.18.17", ReplaceWith("[+amount]% Yield from every [tileFilter]"), DeprecationLevel.ERROR)
    AllStatsSignedPercentFromObject("+[amount]% yield from every [tileFilter]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    @Deprecated("as of 3.18.14", ReplaceWith("[stats] [in all cities] <before discovering [tech]>\" OR \"[stats] [in all cities] <before adopting [policy]>"), DeprecationLevel.ERROR)
    StatsFromCitiesBefore("[stats] per turn from cities before [tech/policy]", UniqueTarget.Global, UniqueTarget.FollowerBelief),

    @Deprecated("as of 3.18.2", ReplaceWith("[50]% of excess happiness converted to [Culture]"), DeprecationLevel.ERROR)
    ExcessHappinessToCultureDeprecated("50% of excess happiness added to culture towards policies", UniqueTarget.Global),
    @Deprecated("as of 3.16.11", ReplaceWith("Not displayed as an available construction without [buildingName]"), DeprecationLevel.ERROR)
    NotDisplayedUnlessOtherBuildingBuilt("Not displayed as an available construction unless [buildingName] is built", UniqueTarget.Building),
    @Deprecated("as of 3.18.2", ReplaceWith("[-amount]% Food consumption by specialists [cityFilter]"), DeprecationLevel.ERROR)
    FoodConsumptionBySpecialistsDeprecated("-[amount]% food consumption by specialists [cityFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.18.6", ReplaceWith("Cannot enter ocean tiles <before discovering [Astronomy]>"), DeprecationLevel.ERROR)
    CannotEnterOceanUntilAstronomy("Cannot enter ocean tiles until Astronomy", UniqueTarget.Unit),
    @Deprecated("as of 3.18.5", ReplaceWith("Cannot be built on [tileFilter] tiles <before discovering [tech]>"), DeprecationLevel.ERROR)
    RequiresTechToBuildOnTile("Cannot be built on [tileFilter] tiles until [tech] is discovered", UniqueTarget.Improvement),

    @Deprecated("as of 3.17.9 - removed 3.19.3", ReplaceWith ("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] at an increasing price ([amount]) <starting from the [era]>"), DeprecationLevel.ERROR)
    BuyUnitsIncreasingCostEra("May buy [baseUnitFilter] units for [amount] [stat] [cityFilter] starting from the [era] at an increasing price ([amount])", UniqueTarget.Global),

    @Deprecated("as of 3.17.10 - removed 3.18.19", ReplaceWith("[stats] from [tileFilter] tiles <after discovering [tech]>"), DeprecationLevel.ERROR)
    StatsOnTileWithTech("[stats] on [tileFilter] tiles once [tech] is discovered", UniqueTarget.Improvement),
    @Deprecated("as of 3.17.10 - removed 3.18.19", ReplaceWith("[stats] <after discovering [tech]>"), DeprecationLevel.ERROR)
    StatsWithTech("[stats] once [tech] is discovered", UniqueTarget.Improvement, UniqueTarget.Building),
    @Deprecated("as of 3.17.10 - removed 3.18.19", ReplaceWith("Adjacent enemy units ending their turn take [30] damage"), DeprecationLevel.ERROR)
    DamagesAdjacentEnemyUnitsForExactlyThirtyDamage("Deal 30 damage to adjacent enemy units", UniqueTarget.Improvement),
    @Deprecated("as of 3.17.7 - removed 3.18.19", ReplaceWith("Gain a free [buildingName] [cityFilter]"), DeprecationLevel.ERROR)
    ProvidesFreeBuildings("Provides a free [buildingName] [cityFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.18", ReplaceWith("[+amount]% [stat] [cityFilter]"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecated("+[amount]% [stat] [cityFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.18", ReplaceWith("[+amount]% [stat] [in all cities]"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecated2("+[amount]% [stat] in all cities", UniqueTarget.Global),
    // type added 3.18.5
    @Deprecated("as of 3.17.1 - removed 3.18.18", ReplaceWith("[amount]% [stat] [in all cities] <while the empire is happy>"), DeprecationLevel.ERROR)
    StatPercentBonusCitiesDeprecatedWhileEmpireHappy("[amount]% [stat] while the empire is happy", UniqueTarget.Global),

    @Deprecated("as of 3.16.15 - removed 3.18.4", ReplaceWith("Provides the cheapest [stat] building in your first [amount] cities for free"), DeprecationLevel.ERROR)
    FreeStatBuildingsDeprecated("Immediately creates the cheapest available cultural building in each of your first [amount] cities for free", UniqueTarget.Global),
    @Deprecated("as of 3.16.15 - removed 3.18.4", ReplaceWith("Provides a [buildingName] in your first [amount] cities for free"), DeprecationLevel.ERROR)
    FreeSpecificBuildingsDeprecated("Immediately creates a [buildingName] in each of your first [amount] cities for free", UniqueTarget.Global),



    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount]% Strength <when attacking>"), DeprecationLevel.ERROR)
    StrengthAttacking("+[amount]% Strength when attacking", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount]% Strength <when defending>"), DeprecationLevel.ERROR)
    StrengthDefending("+[amount]% Strength when defending", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when defending> <vs [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    StrengthDefendingUnitFilter("[amount]% Strength when defending vs [mapUnitFilter] units", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount]% Strength <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    DamageForUnits("[mapUnitFilter] units deal +[amount]% damage", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+10]% Strength <for [All] units> <during a Golden Age>"), DeprecationLevel.ERROR)
    StrengthGoldenAge("+10% Strength for all units during Golden Age", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles> <when defending>"), DeprecationLevel.ERROR)
    StrengthDefenseTiles("+[amount]% defence in [tileFilter] tiles", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <when fighting in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    StrengthIn("+[amount]% Strength in [tileFilter]", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Strength <for [mapUnitFilter] units> <when fighting in [tileFilter] tiles>"), DeprecationLevel.ERROR)
    StrengthUnitsTiles("[amount]% Strength for [mapUnitFilter] units in [tileFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+15]% Strength <for [All] units> <vs cities> <when attacking>"), DeprecationLevel.ERROR)
    StrengthVsCities("+15% Combat Strength for all units when attacking Cities", UniqueTarget.Global),


    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+amount] Movement <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    MovementUnits("+[amount] Movement for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[+1] Movement <for [All] units> <during a Golden Age>"), DeprecationLevel.ERROR)
    MovementGoldenAge("+1 Movement for all units during Golden Age", UniqueTarget.Global),

    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Sight <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    SightUnits("[amount] Sight for all [mapUnitFilter] units", UniqueTarget.Global),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount] Sight"), DeprecationLevel.ERROR)
    VisibilityRange("[amount] Visibility Range", UniqueTarget.Unit),
    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[-1] Sight"), DeprecationLevel.ERROR)
    LimitedVisibility("Limited Visibility", UniqueTarget.Unit),

    @Deprecated("as of 3.17.5 - removed 3.18.5", ReplaceWith("[amount]% Spread Religion Strength <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    SpreadReligionStrengthUnits("[amount]% Spread Religion Strength for [mapUnitFilter] units", UniqueTarget.Global),

    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[+amount]% Production when constructing [baseUnitFilter] units [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionUnitsDeprecated("+[amount]% Production when constructing [baseUnitFilter] units [cityFilter]", UniqueTarget.Global),

    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[+amount]% Production when constructing [stat] buildings [in all cities]"), DeprecationLevel.ERROR)
    PercentProductionStatBuildings("+[amount]% Production when constructing [stat] buildings", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[+amount]% Production when constructing [constructionFilter] buildings [in all cities]"), DeprecationLevel.ERROR)
    PercentProductionConstructions("+[amount]% Production when constructing [constructionFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [buildingName] buildings [in all cities]"), DeprecationLevel.ERROR)
    PercentProductionBuildingName("+[amount]% Production when constructing a [buildingName]", UniqueTarget.Global),
    @Deprecated("as of 3.17.10 - removed 3.18.5", ReplaceWith("[amount]% Production when constructing [constructionFilter] buildings [cityFilter]"), DeprecationLevel.ERROR)
    PercentProductionConstructionsCities("+[amount]% Production when constructing [constructionFilter] [cityFilter]", UniqueTarget.Global),


    @Deprecated("as of 3.17.1 - removed 3.17.13", ReplaceWith("Double movement in [Coast]"), DeprecationLevel.ERROR)
    DoubleMovementCoast("Double movement in coast", UniqueTarget.Unit),
    @Deprecated("as of 3.17.1 - removed 3.17.13", ReplaceWith("Double movement in [terrainFilter]"), DeprecationLevel.ERROR)
    DoubleMovementForestJungle("Double movement rate through Forest and Jungle", UniqueTarget.Unit),
    @Deprecated("as of 3.17.1 - removed 3.17.13", ReplaceWith("Double movement in [terrainFilter]"), DeprecationLevel.ERROR)
    DoubleMovementSnowTundraHill("Double movement in Snow, Tundra and Hills", UniqueTarget.Unit),


    @Deprecated("as of 3.17.3 - removed 3.17.13", ReplaceWith("[+amount]% Strength"), DeprecationLevel.ERROR)
    StrengthPlus("+[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("as of 3.17.3 - removed 3.17.13", ReplaceWith("[-amount]% Strength"), DeprecationLevel.ERROR)
    StrengthMin("-[amount]% Strength", UniqueTarget.Unit),
    @Deprecated("as of 3.17.3 - removed 3.17.13", ReplaceWith("[+amount]% Strength <vs [combatantFilter] units>\" OR \"[+amount]% Strength <vs cities>"), DeprecationLevel.ERROR)
    StrengthPlusVs("+[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("as of 3.17.3 - removed 3.17.13", ReplaceWith("[-amount]% Strength <vs [combatantFilter] units>\" OR \"[+amount]% Strength <vs cities>"), DeprecationLevel.ERROR)
    StrengthMinVs("-[amount]% Strength vs [combatantFilter]", UniqueTarget.Unit),
    @Deprecated("as of 3.17.3 - removed 3.17.13", ReplaceWith("[+amount]% Strength"), DeprecationLevel.ERROR)
    CombatBonus("+[amount]% Combat Strength", UniqueTarget.Unit),

    @Deprecated("as of 3.16.11 - removed 3.17.11", ReplaceWith("[+1] Movement <for [Embarked] units>"), DeprecationLevel.ERROR)
    EmbarkedUnitMovement1("Increases embarked movement +1", UniqueTarget.Global),
    @Deprecated("as of 3.16.11 - removed 3.17.11", ReplaceWith("[+1] Movement <for [Embarked] units>"), DeprecationLevel.ERROR)
    EmbarkedUnitMovement2("+1 Movement for all embarked units", UniqueTarget.Global),

    @Deprecated("as of 3.16.11 - removed 3.17.11", ReplaceWith("[-amount]% unhappiness from population [in all cities]"), DeprecationLevel.ERROR)
    UnhappinessFromPopulationPercentageChangeOld1("Unhappiness from population decreased by [amount]%", UniqueTarget.Global),
    @Deprecated("as of 3.16.11 - removed 3.17.11", ReplaceWith("[-amount]% unhappiness from population [cityFilter]"), DeprecationLevel.ERROR)
    UnhappinessFromPopulationPercentageChangeOld2("Unhappiness from population decreased by [amount]% [cityFilter]", UniqueTarget.Global),

    @Deprecated("as of 3.16.14 - removed 3.17.11", ReplaceWith("[+amount]% growth [cityFilter]"), DeprecationLevel.ERROR)
    GrowthPercentBonusPositive("+[amount]% growth [cityFilter]", UniqueTarget.Global),
    @Deprecated("as of 3.16.14 - removed 3.17.11", ReplaceWith("[+amount]% growth [cityFilter] <when not at war>"), DeprecationLevel.ERROR)
    GrowthPercentBonusWhenNotAtWar("+[amount]% growth [cityFilter] when not at war", UniqueTarget.Global),
    @Deprecated("as of 3.16.16 - removed 3.17.11", ReplaceWith("[-amount]% maintenance costs <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    DecreasedUnitMaintenanceCostsByFilter("-[amount]% [mapUnitFilter] unit maintenance costs", UniqueTarget.Global),
    @Deprecated("as of 3.16.16 - removed 3.17.11", ReplaceWith("[amount]% maintenance costs <for [All] units>"), DeprecationLevel.ERROR)
    DecreasedUnitMaintenanceCostsGlobally("-[amount]% unit upkeep costs", UniqueTarget.Global),

    @Deprecated("as of 3.16.16 - removed 3.17.11", ReplaceWith("[stats] from every specialist [in all cities]"), DeprecationLevel.ERROR)
    StatsFromSpecialistDeprecated("[stats] from every specialist", UniqueTarget.Global),
    @Deprecated("as of 3.16.16 - removed 3.17.11", ReplaceWith("[stats] <if this city has at least [amount] specialists>"), DeprecationLevel.ERROR)
    StatBonusForNumberOfSpecialists("[stats] if this city has at least [amount] specialists", UniqueTarget.Global),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+1] Sight"), DeprecationLevel.ERROR)
    Plus1SightForAutoupdates("+1 Visibility Range", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+amount] Sight"), DeprecationLevel.ERROR)
    PlusSightForAutoupdates("+[amount] Visibility Range", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+amount] Sight <for [mapUnitFilter] units>"), DeprecationLevel.ERROR)
    PlusSightForAutoupdates2("+[amount] Sight for all [mapUnitFilter] units", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+2] Sight"), DeprecationLevel.ERROR)
    Plus2SightForAutoupdates("+2 Visibility Range", UniqueTarget.Unit),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("Can build [Land] improvements on tiles"), DeprecationLevel.ERROR)
    CanBuildImprovementsOnTiles("Can build improvements on tiles", UniqueTarget.Unit),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+1] Happiness from each type of luxury resource"), DeprecationLevel.ERROR)
    BonusHappinessFromLuxuryDeprecated2("+1 happiness from each type of luxury resource", UniqueTarget.Global),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("Science gained from research agreements [+50]%"), DeprecationLevel.ERROR)
    ScienceGainedResearchAgreementsDeprecated("Science gained from research agreements +50%", UniqueTarget.Unit),

    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[-33]% maintenance costs <for [All] units>"), DeprecationLevel.ERROR)
    DecreasedUnitMaintenanceCostsGlobally2("-33% unit upkeep costs", UniqueTarget.Global),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[-50]% Food consumption by specialists [in all cities]"), DeprecationLevel.ERROR)
    FoodConsumptionBySpecialistsDeprecated2("-50% food consumption by specialists", UniqueTarget.Global),
    @Deprecated("Extremely old - used for auto-updates only", ReplaceWith("[+50]% Strength for cities <with a garrison> <when attacking>"), DeprecationLevel.ERROR)
    StrengthForGarrisonedCitiesAttackingDeprecated("+50% attacking strength for cities with garrisoned units", UniqueTarget.Global),

    // Keep the endregion after the semicolon or it won't work
    ;
    // endregion

    val placeholderText: String = text.getPlaceholderText()

    /** Mirrors [UniqueType.getDeprecationAnnotation] — uses reflection to read the [Deprecated] annotation. */
    @Readonly
    fun getDeprecationAnnotation(): Deprecated? = declaringJavaClass.getField(name)
        .getAnnotation(Deprecated::class.java)

    /** The replacement expression from this entry's [Deprecated.replaceWith] annotation. */
    val replacementText: String by lazy { getDeprecationAnnotation()!!.replaceWith.expression }

    companion object {
        /** Lookup from [placeholderText] to [DeprecatedUniqueType], mirroring [UniqueType.uniqueTypeMap]. */
        val uniqueTypeMap: Map<String, DeprecatedUniqueType> = entries.associateBy { it.placeholderText }
    }
}

package com.unciv.models.ruleset.construction

// Note: Translation will need templates for new messages -
//       IF and only if `shouldShow` is true AND the corresponding toInstance call copies them. The others are entirely unused.

enum class RejectionReasonType(
    private val shouldShow: Boolean, // Only the 'instance' copy should be public
    private val errorMessage: String, // Only the 'instance' copy should be public
    internal val isTechPolicyEraWonderRequirement: Boolean = false,
    internal val isReasonToDefinitivelyRemoveFromQueue: Boolean = false,
    /** Used for units spawned, not built */
    internal val isConstructionRejection: Boolean = false,
    /** Ordered precedence, with lower numbers being more important. Default -1 means no precedence and no display in CityScreen construction offers. */
    internal val rejectionPrecedence: Int = -1
) {
    //todo Should all or none of the messages end in '!'?

    AlreadyBuilt(false, "Building already built in this city"),
    Unbuildable(false, "Unbuildable",
        isConstructionRejection = true),
    //todo unused - why?
    CanOnlyBePurchased(true, "Can only be purchased",
        rejectionPrecedence = 9),
    ShouldNotBeDisplayed(false, "Should not be displayed"),

    DisabledBySetting(false, "Disabled by setting"),
    HiddenWithoutVictory(false, "Hidden because a victory type has been disabled"),

    MustBeOnTile(false, "Must be on a specific tile"),
    MustNotBeOnTile(false, "Must not be on a specific tile"),
    MustBeNextToTile(false, "Must be next to a specific tile"),
    MustNotBeNextToTile(false, "Must not be next to a specific tile"),
    MustOwnTile(false, "Must own a specific tile close by"),
    WaterUnitsInCoastalCities(false, "May only build water units in coastal cities"),
    CanOnlyBeBuiltInSpecificCities(false, "Can only be built in specific cities"),
    MaxNumberBuildable(false, "Maximum number have been built or are being constructed",
        isReasonToDefinitivelyRemoveFromQueue = true,
        rejectionPrecedence = 10),

    UniqueToOtherNation(false, "Unique to another nation"),
    ReplacedByOurUnique(false, "Our unique replaces this"),
    CannotBeBuilt(false, "Cannot be built by this nation",
        isConstructionRejection = true),
    CannotBeBuiltUnhappiness(true, "Unhappiness",
        isConstructionRejection = true,
        rejectionPrecedence = 6),

    Obsoleted(false, "Obsolete",
        isTechPolicyEraWonderRequirement = true,
        isReasonToDefinitivelyRemoveFromQueue = true),
    RequiresTech(false, "Required tech not researched",
        isTechPolicyEraWonderRequirement = true),
    //todo unused - why?
    RequiresPolicy(false, "Requires a specific policy!",
        isTechPolicyEraWonderRequirement = true),
    //todo unused - why?
    UnlockedWithEra(false, "Unlocked when reaching a specific era"),
    MorePolicyBranches(false, "Hidden until more policy branches are fully adopted",
        isTechPolicyEraWonderRequirement = true),

    RequiresNearbyResource(false, "Requires a certain resource being exploited nearby"),
    //todo unused - why? The UniqueType is deprecated and mapped to OnlyAvailableWhen + ConditionalCityWithoutBuilding
    CannotBeBuiltWith(false, "Cannot be built at the same time as another building already built",
        isReasonToDefinitivelyRemoveFromQueue = true),

    RequiresBuildingInThisCity(true, "Requires a specific building in this city!",
        rejectionPrecedence = 3),
    RequiresBuildingInAllCities(true, "Requires a specific building in all cities!",
        rejectionPrecedence = 2),
    RequiresBuildingInSomeCities(true, "Requires a specific building in more cities!",
        rejectionPrecedence = 5),
    RequiresBuildingInSomeCity(true, "Requires a specific building anywhere in your empire!",
        isTechPolicyEraWonderRequirement = true,
        rejectionPrecedence = 4),

    WonderAlreadyBuilt(false, "Wonder already built",
        isReasonToDefinitivelyRemoveFromQueue = true),
    NationalWonderAlreadyBuilt(false, "National Wonder already built",
        isReasonToDefinitivelyRemoveFromQueue = true),
    WonderBeingBuiltElsewhere(true, "Wonder is being built elsewhere",
        rejectionPrecedence = 0),
    NationalWonderBeingBuiltElsewhere(true, "National Wonder is being built elsewhere",
        rejectionPrecedence = 1),
    CityStateWonder(false, "No Wonders for city-states"),
    CityStateNationalWonder(false, "No National Wonders for city-states"),
    WonderDisabledEra(false, "This Wonder is disabled when starting in this era"),

    ConsumesResources(true, "Consumes resources which you are lacking",
        rejectionPrecedence = 8),

    PopulationRequirement(true, "Requires more population",
        rejectionPrecedence = 7),

    NoSettlerForOneCityPlayers(false, "No settlers for city-states or one-city challengers"),
    NoPlaceToPutUnit(true, "No space to place this unit",
        rejectionPrecedence = 11),
    ;

    fun toInstance(errorMessage: String = this.errorMessage, shouldShow: Boolean = this.shouldShow) =
        RejectionReason(this, errorMessage, shouldShow)
}

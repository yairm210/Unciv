package com.unciv.models.ruleset.construction

class RejectionReason(val type: RejectionReasonType,
                      val errorMessage: String = type.errorMessage,
                      val shouldShow: Boolean = type.shouldShow) {

    fun techPolicyEraWonderRequirements(): Boolean = type in techPolicyEraWonderRequirements

    fun hasAReasonToBeRemovedFromQueue(): Boolean = type in reasonsToDefinitivelyRemoveFromQueue

    fun isImportantRejection(): Boolean = type in orderedImportantRejectionTypes

    fun isConstructionRejection(): Boolean = type in constructionRejectionReasonType

    /** Returns the index of [orderedImportantRejectionTypes] with the smallest index having the
     * highest precedence */
    fun getRejectionPrecedence(): Int {
        return orderedImportantRejectionTypes.indexOf(type)
    }

    // Used for constant variables in the functions above
    private val techPolicyEraWonderRequirements = hashSetOf(
        RejectionReasonType.Obsoleted,
        RejectionReasonType.RequiresTech,
        RejectionReasonType.RequiresPolicy,
        RejectionReasonType.MorePolicyBranches,
        RejectionReasonType.RequiresBuildingInSomeCity,
    )
    private val reasonsToDefinitivelyRemoveFromQueue = hashSetOf(
        RejectionReasonType.Obsoleted,
        RejectionReasonType.WonderAlreadyBuilt,
        RejectionReasonType.NationalWonderAlreadyBuilt,
        RejectionReasonType.CannotBeBuiltWith,
        RejectionReasonType.MaxNumberBuildable,
    )
    private val orderedImportantRejectionTypes = listOf(
        RejectionReasonType.WonderBeingBuiltElsewhere,
        RejectionReasonType.NationalWonderBeingBuiltElsewhere,
        RejectionReasonType.RequiresBuildingInAllCities,
        RejectionReasonType.RequiresBuildingInThisCity,
        RejectionReasonType.RequiresBuildingInSomeCity,
        RejectionReasonType.CannotBeBuiltUnhappiness,
        RejectionReasonType.PopulationRequirement,
        RejectionReasonType.ConsumesResources,
        RejectionReasonType.CanOnlyBePurchased,
        RejectionReasonType.MaxNumberBuildable,
        RejectionReasonType.NoPlaceToPutUnit,
    )
    // Used for units spawned, not built
    private val constructionRejectionReasonType = listOf(
        RejectionReasonType.Unbuildable,
        RejectionReasonType.CannotBeBuiltUnhappiness,
        RejectionReasonType.CannotBeBuilt,
    )
}

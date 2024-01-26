package com.unciv.models.ruleset.construction

class RejectionReason(
    val type: RejectionReasonType,
    val errorMessage: String,
    val shouldShow: Boolean
) {
    //todo so far the "Used" documentation below is a comprehensive inventory - map to code simplification and proper Kdoc

    // Used in Battle.addXp - true means it's _not_ blocking a unit from being awarded for Great General points
    // Used in UnitUpgradeManager.canUpgrade - true means the `ignoreRequirements` parameter will filter these away
    // true means it's limited by tech, policy, or era in some manner
    fun techPolicyEraWonderRequirements(): Boolean = type.isTechPolicyEraWonderRequirement

    // Used in CityConstructions.validateInProgressConstructions - true means remove+refund or replace with upgrade + transfer points
    fun hasAReasonToBeRemovedFromQueue(): Boolean = type.isReasonToDefinitivelyRemoveFromQueue

    // Used in CityConstructionsTable.getConstructionButtonDTOs to filter reasons from being displayed on the button
    fun isImportantRejection(): Boolean = type.rejectionPrecedence >= 0

    // Used in Battle.addXp - true means it's _not_ blocking a unit from being awarded for Great General points
    // true means the reason is specific to the city construction screen. Contains only the ones for units, not buildings 
    fun isConstructionRejection(): Boolean = type.isConstructionRejection

    /** Smaller numbers have the highest precedence (unless it's -1 in which case [isImportantRejection] returns `false`) */
    // Used in CityConstructionsTable.getConstructionButtonDTOs to decide which of the reasons to be displayed on the button
    fun getRejectionPrecedence(): Int = type.rejectionPrecedence
}

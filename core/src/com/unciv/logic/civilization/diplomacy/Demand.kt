package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.AlertType

/** After creating the required flags, modifiers, and alert type, the only remaining work should be
 * - Adding the new alerts in the when() of AlertPopup.kt
 * - Triggering the violation (adding the violationOccurred flag) from somewhere in the code
 */

enum class Demand(
    /** All these are set on the promisee, not the promiser */
    val agreedToDemand: DiplomacyFlags,
    val violationOccurred: DiplomacyFlags,
    val willIgnoreViolation: DiplomacyFlags,
    val refusedDiplomaticModifier: DiplomaticModifiers,
    val betrayedPromiseDiplomacyMpodifier: DiplomaticModifiers,
    val fulfilledPromiseDiplomacyModifier: DiplomaticModifiers,
    val demandAlert: AlertType,
    val violationDiscoveredAlert: AlertType,
    val demandText: String,
    val acceptDemandText: String,
    val refuseDemandText: String,
    val violationNoticedText: String,
    /** Must contain 1 parameter, to be replaced with civ name */
    val agreedToDemandText: String,
    /** Must contain 1 parameter, to be replaced with civ name */
    val refusedDemandText: String,
    /** Must contain 1 parameter, to be replaced with turns left */
    val wePromisedText: String,
    /** Must contain 1 parameter, to be replaced with turns left */
    val theyPromisedText: String) {
    DontSpyOnUs(
        agreedToDemand = DiplomacyFlags.AgreedToNotSendSpies,
        violationOccurred = DiplomacyFlags.DiscoveredSpiesInOurCities,
        willIgnoreViolation = DiplomacyFlags.IgnoreThemSendingSpies,
        refusedDiplomaticModifier = DiplomaticModifiers.RefusedToNotSendingSpiesToUs,
        betrayedPromiseDiplomacyMpodifier = DiplomaticModifiers.BetrayedPromiseToNotSendingSpiesToUs,
        fulfilledPromiseDiplomacyModifier = DiplomaticModifiers.FulfilledPromiseToNotSpy,
        demandAlert = AlertType.DemandToStopSpyingOnUs,
        violationDiscoveredAlert = AlertType.SpyingOnUsDespiteOurPromise,
        demandText = "Stop spying on us.",
        acceptDemandText = "We see our people are not welcome in your lands... we will take our attention elsewhere.",
        refuseDemandText = "I'll do what's necessary for my empire to survive.",
        agreedToDemandText = "[civName] agreed to stop spying on us!",
        refusedDemandText = "[civName] refused to stop spying on us!",
        violationNoticedText = "Take back your spy and your broken promises.",
        wePromisedText = "We promised not to send spies to them ([turns] turns remaining)",
        theyPromisedText = "They promised not to send spies to us ([turns] turns remaining)"
    ),
    DoNotSpreadReligion(
        agreedToDemand = DiplomacyFlags.AgreedToNotSpreadReligion,
        violationOccurred = DiplomacyFlags.SpreadReligionInOurCities,
        willIgnoreViolation = DiplomacyFlags.IgnoreThemSpreadingReligion,
        refusedDiplomaticModifier = DiplomaticModifiers.RefusedToNotSpreadReligionToUs,
        betrayedPromiseDiplomacyMpodifier = DiplomaticModifiers.BetrayedPromiseToNotSpreadReligionToUs,
        fulfilledPromiseDiplomacyModifier = DiplomaticModifiers.FulfilledPromiseToNotSpreadReligion,
        demandAlert = AlertType.DemandToStopSpreadingReligion,
        violationDiscoveredAlert = AlertType.ReligionSpreadDespiteOurPromise,
        demandText = "Please don't spread your religion to us.",
        acceptDemandText = "Very well, we shall spread our faith elsewhere.",
        refuseDemandText = "We shall do as we please.",
        agreedToDemandText = "[civName] agreed to stop spreading religion to us!",
        refusedDemandText = "[civName] refused to stop spreading religion to us!",
        violationNoticedText = "We noticed you have continued spreading your religion to us, despite your promise. This will have....consequences.",
        wePromisedText = "We promised not to spread religion to them ([turns] turns remaining)",
        theyPromisedText = "They promised not to spread religion to us ([turns] turns remaining)",
    ),
    DoNotSettleNearUs(
        agreedToDemand = DiplomacyFlags.AgreedToNotSettleNearUs,
        violationOccurred = DiplomacyFlags.SettledCitiesNearUs,
        willIgnoreViolation = DiplomacyFlags.IgnoreThemSettlingNearUs,
        refusedDiplomaticModifier = DiplomaticModifiers.RefusedToNotSettleCitiesNearUs,
        betrayedPromiseDiplomacyMpodifier = DiplomaticModifiers.BetrayedPromiseToNotSettleCitiesNearUs,
        fulfilledPromiseDiplomacyModifier = DiplomaticModifiers.FulfilledPromiseToNotSettleCitiesNearUs,
        demandAlert = AlertType.DemandToStopSettlingCitiesNear,
        violationDiscoveredAlert = AlertType.CitySettledNearOtherCivDespiteOurPromise,
        demandText = "Please don't settle new cities near us.",
        acceptDemandText = "Very well, we shall look for new lands to settle.",
        refuseDemandText = "We shall do as we please.",
        agreedToDemandText = "[civName] agreed to stop settling cities near us!",
        refusedDemandText = "[civName] refused to stop settling cities near us!",
        violationNoticedText = "We noticed your new city near our borders, despite your promise. This will have....implications.",
        wePromisedText = "We promised not to settle near them ([turns] turns remaining)",
        theyPromisedText = "They promised not to settle near us ([turns] turns remaining)"
    )
    ;
}

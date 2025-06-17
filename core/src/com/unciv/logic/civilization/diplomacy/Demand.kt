package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.AlertType

enum class Demand(
    /** All these are set on the promisee, not the promiser */
    val agreedToDemand: DiplomacyFlags,
    val violationOccurred: DiplomacyFlags,
    val willIgnoreViolation:DiplomacyFlags,
    val refusedDiplomaticModifier: DiplomaticModifiers,
    val betrayedPromiseDiplomacyMpodifier: DiplomaticModifiers,
    val fulfilledPromiseDiplomacyModifier: DiplomaticModifiers,
    val demandAlert: AlertType,
    val violationDiscoveredAlert: AlertType,
    val demandText: String,
    val agreedToDemandText: String,
    val refusedDemandText: String) {
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
        agreedToDemandText = "[civName] agreed to stop spying on us!",
        refusedDemandText = "[civName] refused to stop spying on us!"
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
        agreedToDemandText = "[civName] agreed to stop spreading religion to us!",
        refusedDemandText = "[civName] refused to stop spreading religion to us!",
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
        agreedToDemandText = "[civName] agreed to stop settling cities near us!",
        refusedDemandText = "[civName] refused to stop settling cities near us!"
    )
    ;
}

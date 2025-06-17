package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.civilization.AlertType

enum class Demand(
    val agreedToDemand: DiplomacyFlags,
    val violationOccurred: DiplomacyFlags,
    val willIgnoreViolation:DiplomacyFlags,
    val refusedDiplomaticModifier: DiplomaticModifiers,
    val betrayedPromiseDiplomacyMpodifier: DiplomaticModifiers,
    val demandAlert: AlertType,
    val violationDiscoveredAlert: AlertType) {
    DontSpyOnUs(
        agreedToDemand = DiplomacyFlags.AgreedToNotSendSpies,
        violationOccurred = DiplomacyFlags.DiscoveredSpiesInOurCities,
        willIgnoreViolation = DiplomacyFlags.IgnoreThemSendingSpies,
        refusedDiplomaticModifier = DiplomaticModifiers.RefusedToNotSendingSpiesToUs,
        betrayedPromiseDiplomacyMpodifier = DiplomaticModifiers.BetrayedPromiseToNotSendingSpiesToUs,
        demandAlert = AlertType.DemandToStopSpyingOnUs,
        violationDiscoveredAlert = AlertType.SpyingOnUsDespiteOurPromise
    ),
    DoNotSpreadReligion(
        agreedToDemand = DiplomacyFlags.AgreedToNotSpreadReligion,
        violationOccurred = DiplomacyFlags.SpreadReligionInOurCities,
        willIgnoreViolation = DiplomacyFlags.IgnoreThemSpreadingReligion,
        refusedDiplomaticModifier = DiplomaticModifiers.RefusedToNotSpreadReligionToUs,
        betrayedPromiseDiplomacyMpodifier = DiplomaticModifiers.BetrayedPromiseToNotSpreadReligionToUs,
        demandAlert = AlertType.DemandToStopSpreadingReligion,
        violationDiscoveredAlert = AlertType.ReligionSpreadDespiteOurPromise
    ),
    DoNotSettleNearUs(
        agreedToDemand = DiplomacyFlags.AgreedToNotSettleNearUs,
        violationOccurred = DiplomacyFlags.SettledCitiesNearUs,
        willIgnoreViolation = DiplomacyFlags.IgnoreThemSettlingNearUs,
        refusedDiplomaticModifier = DiplomaticModifiers.RefusedToNotSettleCitiesNearUs,
        betrayedPromiseDiplomacyMpodifier = DiplomaticModifiers.BetrayedPromiseToNotSettleCitiesNearUs,
        demandAlert = AlertType.DemandToStopSettlingCitiesNear,
        violationDiscoveredAlert = AlertType.CitySettledNearOtherCivDespiteOurPromise
    )
    ;
}
package com.unciv.logic.civilization

enum class AlertType{
    Defeated,
    WonderBuilt,
    WarDeclaration,
    FirstContact,
    CityConquered,
    BorderConflict,
    @Deprecated("As of 2.19.0 - replaced with DemandToStopSettlingCitiesNear")
    CitiesSettledNearOtherCiv,
    DemandToStopSettlingCitiesNear,
    CitySettledNearOtherCivDespiteOurPromise,
}

class PopupAlert {
    lateinit var type: AlertType
    lateinit var value: String

    constructor(type: AlertType, value: String) {
        this.type = type
        this.value = value
    }

    constructor() // for json serialization
}

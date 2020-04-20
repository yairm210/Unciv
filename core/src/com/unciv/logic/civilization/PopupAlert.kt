package com.unciv.logic.civilization

enum class AlertType {
    Defeated,
    WonderBuilt,
    TechResearched,
    WarDeclaration,
    FirstContact,
    CityConquered,
    BorderConflict,
    DemandToStopSettlingCitiesNear,
    CitySettledNearOtherCivDespiteOurPromise,
    GoldenAge,
    DeclarationOfFriendship,
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

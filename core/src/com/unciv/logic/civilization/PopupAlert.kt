package com.unciv.logic.civilization

enum class AlertType{
    WarDeclaration,
    Defeated,
    FirstContact,
    CityConquered,
    BorderConflict
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

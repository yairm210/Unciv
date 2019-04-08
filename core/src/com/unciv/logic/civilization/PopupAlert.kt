package com.unciv.logic.civilization

enum class AlertType{
    WarDeclaration,
    Defeated,
    FirstContact,
    CityConquered
}

class PopupAlert (val type:AlertType, val value:String)

package com.unciv.logic.civilization

enum class AlertType{
    WarDeclaration,
    Defeated,
    FirstContact
}

class PopupAlert (val type:AlertType, val value:String)

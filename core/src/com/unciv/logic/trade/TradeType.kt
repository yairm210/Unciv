package com.unciv.logic.trade

enum class TradeType{
    Gold,
    Gold_Per_Turn,
    /** Treaties are shared by both sides - like peace treaty and defensive pact */
    Treaty,
    /** Agreements are one-sided, like open borders */
    Agreement,
    Luxury_Resource,
    Strategic_Resource,
    Technology,
    Introduction,
    WarDeclaration,
    City
}
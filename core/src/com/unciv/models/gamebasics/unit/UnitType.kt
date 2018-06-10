package com.unciv.models.gamebasics.unit

enum class UnitType{
    City,
    Civilian,
    Melee,
    Ranged,
    Scout,
    Mounted,
    Siege;

    fun isMelee(): Boolean {
        return this in listOf(Melee, Mounted, Scout)
    }
    fun isRanged(): Boolean {
        return this in listOf(Ranged, Siege)
    }
}
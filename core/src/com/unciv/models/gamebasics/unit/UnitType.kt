package com.unciv.models.gamebasics.unit

enum class UnitType{
    City,
    Civilian,
    Melee,
    Ranged,
    Scout,
    Mounted,
    WaterCivilian,
    Siege;

    fun isMelee(): Boolean {
        return this == Melee
                || this == Mounted
                || this == Scout
    }
    fun isRanged(): Boolean {
        return this == Ranged
                || this == Siege
    }

    fun isLandUnit(): Boolean {
        return this == Civilian
                || this == Melee
                || this == Mounted
                || this == Scout
                || this == Ranged
                || this == Siege
    }
    fun isWaterUnit(): Boolean {
        return this == WaterCivilian
    }
}
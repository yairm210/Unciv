package com.unciv.models.gamebasics.unit

enum class UnitType{
    City,
    Civilian,
    Melee,
    Ranged,
    Scout,
    Mounted,
    WaterCivilian,
    WaterMelee,
    Siege;

    fun isMelee(): Boolean {
        return this == Melee
                || this == Mounted
                || this == Scout
                || this==WaterMelee
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

    fun isCivilian(): Boolean {
        return this == Civilian
            || this == WaterCivilian
    }

    fun isWaterUnit(): Boolean {
        return !isLandUnit() // if we ever get air units, this'll have to change
    }
}
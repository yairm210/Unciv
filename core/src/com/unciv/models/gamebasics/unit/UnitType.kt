package com.unciv.models.gamebasics.unit

enum class UnitType{
    City,
    Civilian,
    Melee,
    Ranged,
    Scout,
    Mounted,
    Armor,
    Siege,

    WaterCivilian,
    WaterMelee,
    WaterRanged,
    WaterSubmarine,

    AirFighter,
    AirBomber;

    fun isMelee(): Boolean {
        return this == Melee
                || this == Mounted
                || this == Armor
                || this == Scout
                || this == WaterMelee
    }
    fun isRanged(): Boolean {
        return this == Ranged
                || this == Siege
                || this == WaterRanged
                || this == WaterSubmarine
                || this == City
    }

    fun isLandUnit(): Boolean {
        return this == Civilian
                || this == Melee
                || this == Mounted
                || this == Armor
                || this == Scout
                || this == Ranged
                || this == Siege
    }

    fun isCivilian(): Boolean {
        return this == Civilian
            || this == WaterCivilian
    }

    fun isWaterUnit(): Boolean {
        return this==WaterSubmarine
                || this==WaterRanged
                || this==WaterMelee
                || this==WaterCivilian
    }

    fun isAirUnit():Boolean{
        return this==AirBomber
                || this==AirFighter
    }
}
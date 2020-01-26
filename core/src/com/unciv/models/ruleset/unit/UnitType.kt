package com.unciv.models.ruleset.unit

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
    WaterAircraftCarrier,
    WaterMissileCarrier,

    Fighter,
    Bomber,
    Missile;

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
                || this.isAirUnit()
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

    fun isMilitary(): Boolean {
        return this != Civilian
                && this != WaterCivilian
    }

    fun isWaterUnit(): Boolean {
        return this==WaterSubmarine
                || this==WaterRanged
                || this==WaterMelee
                || this==WaterCivilian
                || this==WaterAircraftCarrier
                || this==WaterMissileCarrier
    }

    fun isAirUnit():Boolean{
        return this==Bomber
                || this==Fighter
                || this==Missile
    }


    fun isAircraftCarrierUnit():Boolean{
        return this == WaterAircraftCarrier
    }

    fun isMissileCarrierUnit():Boolean{
        return this == WaterMissileCarrier
    }
}
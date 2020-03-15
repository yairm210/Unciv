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

    fun isMelee() =
                this == Melee
                || this == Mounted
                || this == Armor
                || this == Scout
                || this == WaterMelee

    fun isRanged() =
                this == Ranged
                || this == Siege
                || this == WaterRanged
                || this == WaterSubmarine
                || this == City
                || this.isAirUnit()

    fun isLandUnit() =
                this == Civilian
                || this == Melee
                || this == Mounted
                || this == Armor
                || this == Scout
                || this == Ranged
                || this == Siege

    fun isCivilian() = this == Civilian || this == WaterCivilian

    fun isMilitary() = this != Civilian && this != WaterCivilian

    fun isWaterUnit() =
                this == WaterSubmarine
                || this == WaterRanged
                || this == WaterMelee
                || this == WaterCivilian
                || this == WaterAircraftCarrier
                || this == WaterMissileCarrier

    fun isAirUnit() =
                this == Bomber
                || this == Fighter
                || this == Missile

    fun isMissile() = this == Missile

    fun isAircraftCarrierUnit() = this == WaterAircraftCarrier

    fun isMissileCarrierUnit() = this == WaterMissileCarrier
}
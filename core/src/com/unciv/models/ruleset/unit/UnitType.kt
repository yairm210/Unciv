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

    Fighter,
    Bomber,
    AtomicBomber,
    Missile;

    fun isLandUnit() =
                this == Civilian
                || this == Melee
                || this == Mounted
                || this == Armor
                || this == Scout
                || this == Ranged
                || this == Siege

    fun isWaterUnit() =
                this == WaterSubmarine
                || this == WaterRanged
                || this == WaterMelee
                || this == WaterCivilian
                || this == WaterAircraftCarrier

    fun isAirUnit() =
                this == Bomber
                || this == Fighter
                || this == AtomicBomber

}

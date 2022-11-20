package com.unciv.logic.civilization

enum class CityStateType(val color: String = "") {
    Cultured("#8b60ff"),
    Maritime("#38ff70"),
    Mercantile("#ffd800"),
    Militaristic("#ff0000"),
    Religious("#FFFFFF")
}

enum class CityStatePersonality {
    Friendly,
    Neutral,
    Hostile,
    Irrational
}

package com.unciv.logic.civilization.diplomacy

enum class CityStatePersonality {
    Friendly,
    Neutral,
    Hostile,
    Irrational
    ;
    companion object {
        fun safeValueOf(name: String?) = entries.firstOrNull { it.name == name }
    }
}

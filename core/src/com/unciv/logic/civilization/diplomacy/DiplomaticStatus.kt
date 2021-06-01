package com.unciv.logic.civilization.diplomacy

enum class DiplomaticStatus{
    Peace,
    Protector,  //city state's diplomacy for major civ can be marked as Protector, not vice versa.
    War
}
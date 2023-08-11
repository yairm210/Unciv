package com.unciv.logic.civilization.diplomacy

import com.unciv.logic.IsPartOfGameInfoSerialization

enum class DiplomaticStatus : IsPartOfGameInfoSerialization {
    Peace,
    Protector,  //city state's diplomacy for major civ can be marked as Protector, not vice versa.
    War,
    DefensivePact,
}

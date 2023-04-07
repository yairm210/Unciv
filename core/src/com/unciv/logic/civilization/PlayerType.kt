package com.unciv.logic.civilization

import com.unciv.logic.IsPartOfGameInfoSerialization

enum class PlayerType : IsPartOfGameInfoSerialization {
    AI,
    Human;
    fun toggle() = if (this == AI) Human else AI
}

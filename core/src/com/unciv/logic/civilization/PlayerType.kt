package com.unciv.logic.civilization

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.utils.JsonSerialized

@JsonSerialized
enum class PlayerType : IsPartOfGameInfoSerialization {
    AI,
    Human;
}

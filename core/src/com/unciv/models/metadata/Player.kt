package com.unciv.models.metadata

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.PlayerType

class Player(var chosenCiv: String = Constants.random) : IsPartOfGameInfoSerialization {
    var playerType: PlayerType = PlayerType.AI
    var playerId=""
}

package com.unciv.models.metadata

import com.unciv.Constants
import com.unciv.logic.civilization.PlayerType

class Player {
    var playerType: PlayerType = PlayerType.AI
    var chosenCiv = Constants.random
    var playerId=""
}
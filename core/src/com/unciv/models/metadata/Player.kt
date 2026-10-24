package com.unciv.models.metadata

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation

class Player(
    var chosenCiv: String = Constants.random,
    var playerType: PlayerType = PlayerType.AI,
    var playerId: String = ""
) : IsPartOfGameInfoSerialization {
    constructor() : this(Constants.random, PlayerType.AI, "")
    constructor(chosenNation: Nation, playerType: PlayerType = PlayerType.AI, playerId: String = ""):
        this(chosenNation.name, playerType, playerId) {
            this.chosenNation = chosenNation 
        }
    @Transient
    lateinit var chosenNation: Nation
    fun setNationTransient(ruleset: Ruleset) {
        chosenNation = ruleset.nations[chosenCiv]!!
    }
}

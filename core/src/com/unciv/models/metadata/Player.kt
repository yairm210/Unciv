package com.unciv.models.metadata

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.nation.Nation

class Player(
    var chosenCiv: String = Constants.random,
    var playerType: PlayerType = PlayerType.AI,
    var playerId: String = "",
    /**
     * Permanent setup team. Players sharing the same positive [teamId] are teammates.
     * `0` means unassigned (JSON default for old saves / new Player()); New Game / GameStarter
     * assign unique positive ids when all remain `0`.
     */
    var teamId: Int = 0
) : IsPartOfGameInfoSerialization {
    constructor() : this(Constants.random, PlayerType.AI, "")
    constructor(chosenNation: Nation, playerType: PlayerType = PlayerType.AI, playerId: String = "", teamId: Int = 0):
        this(chosenNation.name, playerType, playerId, teamId) {
            this.chosenNation = chosenNation 
        }
    @Transient
    lateinit var chosenNation: Nation

    fun setNationTransient(ruleset: Ruleset) {
        chosenNation = ruleset.nations[chosenCiv]!!
    }
}

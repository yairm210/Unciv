package com.unciv.logic.map

import com.unciv.models.metadata.GameParameters

class Scenario {
    lateinit var tileMap: TileMap
    lateinit var gameParameters: GameParameters

    /** for json parsing, we need to have a default constructor */
    constructor()

    constructor(tileMap:TileMap, gameParameters: GameParameters) {
        this.tileMap = tileMap
        this.gameParameters = gameParameters
    }
}

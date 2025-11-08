package com.unciv.models.metadata

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape

class GameSetupInfo(
    val gameParameters: GameParameters = GameParameters(),
    val mapParameters: MapParameters = MapParameters()
) {
    @Transient
    var mapFile: FileHandle? = null

    // This constructor is used for starting a new game from a running one, cloning the setup, including map seed
    constructor(gameInfo: GameInfo) : this(gameInfo.gameParameters.clone(), gameInfo.tileMap.mapParameters.clone())
    // Cloning constructor used for [fromSettings], reseeds map
    constructor(setup: GameSetupInfo): this(setup.gameParameters.clone(), setup.mapParameters.clone())

    companion object {
        /**
         * Get a cloned and reseeded [GameSetupInfo] from saved settings if present, otherwise a default instance.
         * @param defaultDifficulty Overrides difficulty only when no saved settings found, so a virgin
         *          Unciv installation can QuickStart with a different difficulty than New Game defaults to.
         */
        fun fromSettings(defaultDifficulty: String? = null) = UncivGame.Current.settings.run {
            if (lastGameSetup == null) GameSetupInfo().apply {
                if (defaultDifficulty != null) gameParameters.difficulty = defaultDifficulty
                mapParameters.shape = MapShape.rectangular
                mapParameters.worldWrap = true
                gameParameters.espionageEnabled = true
            }
            else GameSetupInfo(lastGameSetup!!).apply {
                mapParameters.reseed()
            }
        }
    }
}

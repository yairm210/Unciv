package com.unciv.view

import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.Civilization

/** View of a [GameInfo] from the perspective of [viewer]. */
class GameView(gameInfo: GameInfo, viewer: Civilization) {
    val civView: CivView = CivView(viewer, viewer)
    val tileMapView: TileMapView = TileMapView(gameInfo.tileMap, viewer)
}

package com.unciv.utils

import com.unciv.logic.map.HexCoord

object DebugUtils {

    /**
     * This exists so that when debugging we can see the entire map.
     * Remember to turn this to false before commit and upload!
     * Or use the "secret" debug page of the options popup instead.
     */
    var VISIBLE_MAP: Boolean = false

    /** This flag paints the tile coordinates directly onto the map tiles. */
    var SHOW_TILE_COORDS: Boolean = false
    
    var SHOW_TILE_IMAGE_LOCATIONS: Boolean = false

    /** This flag paints the last computed settler tile ranking onto the map tiles. */
    var SHOW_SETTLER_SCORES: Boolean = false

    /** Last settler tile ranking computed by CityLocationTileRanker, for [SHOW_SETTLER_SCORES]. */
    var SETTLER_SCORES: Map<HexCoord, Float> = emptyMap()

    /** For when you need to test something in an advanced game and don't have time to faff around */
    var SUPERCHARGED: Boolean = false

    /** Shows the current FPS in the top-left corner of every [BaseScreen][com.unciv.ui.screens.basescreen.BaseScreen]
     *  except [GameStartScreen][com.unciv.ui.screens.GameStartScreen] and
     *  [CrashScreen][com.unciv.ui.crashhandling.CrashScreen] (both are constructed before the skin
     *  is set up and so can't render it). */
    var SHOW_FPS: Boolean = false

    /** Simulate until this turn on the first "Next turn" button press.
     *  Does not update World View changes until finished.
     *  Set to 0 to disable.
     */
    var SIMULATE_UNTIL_TURN: Int = 0

    /** For A/B testing against the unchanged AI.
     *  Gate experimental code with `if (civInfo.civID in DebugUtils.CIV_IDS_IN_EXPERIMENT_GROUP)`.
     */
    var CIV_IDS_IN_EXPERIMENT_GROUP: Set<String> = emptySet()

}

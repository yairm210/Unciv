package com.unciv.logic.civilization.managers

import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import yairm210.purity.annotations.Readonly

object ImprovementFunctions {

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    @Readonly
    fun getImprovementBuildingProblems(improvement: TileImprovement, gameContext: GameContext, tile: Tile? = null): Sequence<ImprovementBuildingProblem> =
        ImprovementBuildingProblem.entries.asSequence().filter { it.isRelevant(improvement, gameContext, tile) }

    /** Returns true if nothing prevents building [improvement] - stops at the first problem found */
    @Readonly
    fun canBuildImprovement(improvement: TileImprovement, gameContext: GameContext, tile: Tile? = null): Boolean =
        ImprovementBuildingProblem.entries.none { it.isRelevant(improvement, gameContext, tile) }
}

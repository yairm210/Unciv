package com.unciv.logic.civilization.managers

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

object ImprovementFunctions {

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    @Readonly
    fun getImprovementBuildingProblems(improvement: TileImprovement, gameContext: GameContext, tile: Tile? = null): Sequence<ImprovementBuildingProblem> = sequence {
        if (gameContext.civInfo != null) {
            val civInfo: Civilization = gameContext.civInfo

            if (improvement.uniqueTo != null && !civInfo.matchesFilter(improvement.uniqueTo!!))
                yield(ImprovementBuildingProblem.WrongCiv)
            if (civInfo.cache.uniqueImprovements.any { it.replaces == improvement.name })
                yield(ImprovementBuildingProblem.Replaced)
            if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!))
                yield(ImprovementBuildingProblem.MissingTech)
            if (improvement.getMatchingUniques(UniqueType.Unbuildable, GameContext.IgnoreConditionals)
                    .any { it.modifiers.isEmpty() })
                yield(ImprovementBuildingProblem.Unbuildable)
            else if (improvement.hasUnique(UniqueType.Unbuildable, gameContext))
                yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

            if (improvement.hasUnique(UniqueType.Unavailable, gameContext))
                yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

            if (improvement.getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals)
                    .any { !it.conditionalsApply(gameContext) })
                yield(ImprovementBuildingProblem.UnmetConditional)

            if (improvement.getMatchingUniques(UniqueType.ObsoleteWith, gameContext)
                    .any { civInfo.tech.isResearched(it.params[0]) })
                yield(ImprovementBuildingProblem.Obsolete)

            if (improvement.getMatchingUniques(UniqueType.ConsumesResources, gameContext)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() })
                yield(ImprovementBuildingProblem.MissingResources)

            if (improvement.getMatchingUniques(UniqueType.CostsResources)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() *
                            (if (it.isModifiedByGameSpeed()) civInfo.gameInfo.speed.modifier else 1f) })
                yield(ImprovementBuildingProblem.MissingResources)
            
            if (tile != null) {
                if (tile.getOwner() != civInfo
                    && !improvement.hasUnique(UniqueType.CanBuildOutsideBorders, gameContext)
                    && (!improvement.hasUnique(UniqueType.CanBuildJustOutsideBorders, gameContext)
                            || tile.neighbors.none { it.getOwner() == civInfo })
                ) 
                    yield(ImprovementBuildingProblem.OutsideBorders)
                
                val knownFeatureRemovals = tile.ruleset.nonRoadTileRemovals
                    .filter { rulesetImprovement ->
                        rulesetImprovement.techRequired == null || civInfo.tech.isResearched(rulesetImprovement.techRequired!!)
                    }
        
                if (!tile.improvementFunctions.canImprovementBeBuiltHere(improvement, tile.hasViewableResource(civInfo), knownFeatureRemovals, gameContext))
                // There are way too many conditions in that functions, besides, they are not interesting
                // at least for the current usecases. Improve if really needed.
                    yield(ImprovementBuildingProblem.Other)
            }
        }
        else {
                yield(ImprovementBuildingProblem.WrongCiv)
        }
    }
}

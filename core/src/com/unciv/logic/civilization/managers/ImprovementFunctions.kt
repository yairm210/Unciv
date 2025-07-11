package com.unciv.logic.civilization.managers

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.ImprovementBuildingProblem
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType

object ImprovementFunctions {

    /** Generates a sequence of reasons that prevent building given [improvement].
     *  If the sequence is empty, improvement can be built immediately.
     */
    fun getImprovementBuildingProblems(improvement: TileImprovement, stateForConditionals: StateForConditionals, tile: Tile? = null): Sequence<ImprovementBuildingProblem> = sequence {
        if (stateForConditionals.civInfo != null) {
            val civInfo: Civilization = stateForConditionals.civInfo

            if (improvement.uniqueTo != null && !civInfo.matchesFilter(improvement.uniqueTo!!))
                yield(ImprovementBuildingProblem.WrongCiv)
            if (civInfo.cache.uniqueImprovements.any { it.replaces == improvement.name })
                yield(ImprovementBuildingProblem.Replaced)
            if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!))
                yield(ImprovementBuildingProblem.MissingTech)
            if (improvement.getMatchingUniques(UniqueType.Unbuildable, StateForConditionals.IgnoreConditionals)
                    .any { it.modifiers.isEmpty() })
                yield(ImprovementBuildingProblem.Unbuildable)
            else if (improvement.hasUnique(UniqueType.Unbuildable, stateForConditionals))
                yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

            if (improvement.hasUnique(UniqueType.Unavailable, stateForConditionals))
                yield(ImprovementBuildingProblem.ConditionallyUnbuildable)

            if (improvement.getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals)
                    .any { !it.conditionalsApply(stateForConditionals) })
                yield(ImprovementBuildingProblem.UnmetConditional)

            if (improvement.getMatchingUniques(UniqueType.ObsoleteWith, stateForConditionals)
                    .any { civInfo.tech.isResearched(it.params[0]) })
                yield(ImprovementBuildingProblem.Obsolete)

            if (improvement.getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() })
                yield(ImprovementBuildingProblem.MissingResources)

            if (improvement.getMatchingUniques(UniqueType.CostsResources)
                    .any { civInfo.getResourceAmount(it.params[1]) < it.params[0].toInt() *
                            (if (it.isModifiedByGameSpeed()) civInfo.gameInfo.speed.modifier else 1f) })
                yield(ImprovementBuildingProblem.MissingResources)
            
            if (tile != null) {
                if (tile.getOwner() != civInfo && !improvement.hasUnique(UniqueType.CanBuildOutsideBorders, stateForConditionals)) {
                    if (!improvement.hasUnique(UniqueType.CanBuildJustOutsideBorders, stateForConditionals))
                        yield(ImprovementBuildingProblem.OutsideBorders)
                    else if (tile.neighbors.none { it.getOwner() == civInfo })
                        yield(ImprovementBuildingProblem.NotJustOutsideBorders)
                }
                val knownFeatureRemovals = tile.ruleset.nonRoadTileRemovals
                    .filter { rulesetImprovement ->
                        rulesetImprovement.techRequired == null || civInfo.tech.isResearched(rulesetImprovement.techRequired!!)
                    }
        
                if (!tile.improvementFunctions.canImprovementBeBuiltHere(improvement, tile.hasViewableResource(civInfo), knownFeatureRemovals, stateForConditionals))
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

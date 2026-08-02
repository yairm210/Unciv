package com.unciv.view

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.RejectionReason
import com.unciv.models.ruleset.tile.TileImprovement
import yairm210.purity.annotations.Readonly

class CityConstructionsView(private val cityConstructions: CityConstructions) {
    val constructionQueue: List<String> get() = cityConstructions.constructionQueue

    @Readonly fun currentConstructionName(): String = cityConstructions.currentConstructionName()
    @Readonly fun isQueueFull(): Boolean = cityConstructions.isQueueFull()
    @Readonly fun isBeingConstructedOrEnqueued(name: String): Boolean = cityConstructions.isBeingConstructedOrEnqueued(name)
    @Readonly fun getConstruction(name: String): IConstruction = cityConstructions.getConstruction(name)
    @Readonly fun isFirstConstructionOfItsKind(index: Int, name: String): Boolean = cityConstructions.isFirstConstructionOfItsKind(index, name)
    @Readonly fun isBuilt(name: String): Boolean = cityConstructions.isBuilt(name)
    @Readonly fun getTurnsToConstructionString(construction: IConstruction, isFirst: Boolean = true): String = cityConstructions.getTurnsToConstructionString(construction, isFirst)
    @Readonly fun getWorkDone(name: String): Int = cityConstructions.getWorkDone(name)
    @Readonly fun shouldBeDisplayed(construction: IConstruction): Boolean = construction.shouldBeDisplayed(cityConstructions)
    @Readonly fun getRejectionReasons(construction: INonPerpetualConstruction): Sequence<RejectionReason> = construction.getRejectionReasons(cityConstructions)
    @Readonly fun isBuildable(construction: IConstruction): Boolean = construction.isBuildable(cityConstructions)
    @Readonly fun canPlaceCreateOneImprovementOn(improvement: TileImprovement, tile: Tile): Boolean =
        cityConstructions.canPlaceCreateOneImprovementOn(improvement, tile)
    @Readonly fun getTileForImprovement(improvementName: String): Tile? =
        cityConstructions.getTileForImprovement(improvementName)
    @Readonly fun canAddToQueue(construction: IConstruction): Boolean = cityConstructions.canAddToQueue(construction)
    @Readonly fun isEnqueuedForLater(name: String): Boolean = cityConstructions.isEnqueuedForLater(name)

    fun getCityConstructions(): CityConstructions = cityConstructions
}

package com.unciv.view

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.RejectionReason
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.Readonly

class CityConstructionsView(private val cityConstructions: CityConstructions, private val gameView: GameView,
                             viewer: Civilization, spectatorMode: Boolean = false) : GameBasedView<CityConstructions>(cityConstructions, viewer, spectatorMode) {
    val constructionQueue: List<String> get() = cityConstructions.constructionQueue

    // Navigation
    @Readonly fun getCityConstructions(): CityConstructions = cityConstructions

    // Data retrieval
    @Readonly fun currentConstructionName(): String = cityConstructions.currentConstructionName()
    @Readonly fun getConstruction(name: String): IConstruction = cityConstructions.getConstruction(name)
    @Readonly fun isFirstConstructionOfItsKind(index: Int, name: String): Boolean = cityConstructions.isFirstConstructionOfItsKind(index, name)
    @Readonly fun isBuilt(name: String): Boolean = cityConstructions.isBuilt(name)
    @Readonly fun getTurnsToConstructionString(construction: IConstruction, isFirst: Boolean = true): String = cityConstructions.getTurnsToConstructionString(construction, isFirst)
    @Readonly fun getWorkDone(name: String): Int = cityConstructions.getWorkDone(name)
    @Readonly fun shouldBeDisplayed(construction: IConstruction): Boolean = construction.shouldBeDisplayed(cityConstructions)
    @Readonly fun getRejectionReasons(construction: INonPerpetualConstruction): Sequence<RejectionReason> = construction.getRejectionReasons(cityConstructions)
    @Readonly fun isBuildable(construction: IConstruction): Boolean = construction.isBuildable(cityConstructions)
    
    @Readonly fun canPlaceCreateOneImprovementOn(improvement: TileImprovement, tileView: TileView): Boolean =
        cityConstructions.canPlaceCreateOneImprovementOn(improvement, tileView.unwrap())
    @Readonly fun getTileForImprovement(improvementName: String): TileView? =
        cityConstructions.getTileForImprovement(improvementName)?.let { gameView.tileMapView.getTile(it) }

    @Readonly fun isQueueFull(): Boolean = cityConstructions.isQueueFull()
    @Readonly fun isBeingConstructedOrEnqueued(name: String): Boolean = cityConstructions.isBeingConstructedOrEnqueued(name)
    @Readonly fun canAddToQueue(construction: IConstruction): Boolean = cityConstructions.canAddToQueue(construction)
    @Readonly fun isEnqueuedForLater(name: String): Boolean = cityConstructions.isEnqueuedForLater(name)
    
    @Readonly fun getCurrentConstruction(): IConstruction = cityConstructions.getCurrentConstruction()
    @Readonly fun getStatBuyCost(construction: INonPerpetualConstruction, stat: Stat): Int? =
        construction.getStatBuyCost(cityConstructions.city, stat)
    @Readonly fun isConstructionPurchaseAllowed(construction: INonPerpetualConstruction, stat: Stat, cost: Int): Boolean =
        cityConstructions.isConstructionPurchaseAllowed(construction, stat, cost)
    @Readonly fun isConstructionPurchaseBlockedByUnit(construction: INonPerpetualConstruction): Boolean =
        cityConstructions.isConstructionPurchaseBlockedByUnit(construction)

    // Actions
    fun purchaseConstruction(construction: INonPerpetualConstruction, queuePosition: Int, stat: Stat, tileView: TileView?): Boolean =
        cityConstructions.purchaseConstruction(construction, queuePosition, automatic = false, stat, tileView?.unwrap())
}

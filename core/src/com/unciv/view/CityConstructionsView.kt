package com.unciv.view

import com.unciv.models.ruleset.Building
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.RejectionReason
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.stats.Stat
import yairm210.purity.annotations.Readonly

class CityConstructionsView(private val cityConstructions: CityConstructions, gameView: GameView,
                             viewer: Civilization, spectatorMode: Boolean = false) : OwnedView<CityConstructions>(cityConstructions, viewer, spectatorMode, gameView) {
    @Readonly internal override fun owner(): Civilization = cityConstructions.city.civ

    val constructionQueue: List<String> get() = cityConstructions.constructionQueue

    // Navigation
    @Deprecated("Scheduled for removal - views should unwrap() instead")
    @Readonly fun getCityConstructions(): CityConstructions = cityConstructions

    // Data retrieval
    @Readonly fun currentConstructionName(): String = cityConstructions.currentConstructionName()
    @Readonly fun getConstruction(name: String): IConstruction = cityConstructions.getConstruction(name)
    @Readonly fun isFirstConstructionOfItsKind(index: Int, name: String): Boolean = cityConstructions.isFirstConstructionOfItsKind(index, name)
    /** For a caller that walks a [constructionQueue] snapshot it took once */
    @Readonly fun isFirstConstructionOfItsKind(queue: List<String>, index: Int, name: String): Boolean =
        cityConstructions.isFirstConstructionOfItsKind(queue, index, name)
    @Readonly fun isBuilt(name: String): Boolean = cityConstructions.isBuilt(name)
    @Readonly fun getWorkDone(name: String): Int = cityConstructions.getWorkDone(name)
    @Readonly fun turnsToConstruction(name: String, useStoredProduction: Boolean = true): Int = cityConstructions.turnsToConstruction(name, useStoredProduction)
    // The optional [queue] is a snapshot of [constructionQueue] for callers that check many constructions in a row - see INonPerpetualConstruction.getRejectionReasons
    @Readonly fun shouldBeDisplayed(construction: IConstruction, queue: List<String> = constructionQueue): Boolean = construction.shouldBeDisplayed(cityConstructions, queue)
    @Readonly fun getRejectionReasons(construction: INonPerpetualConstruction, queue: List<String> = constructionQueue): Sequence<RejectionReason> =
        construction.getRejectionReasons(cityConstructions, queue)
    @Readonly fun isBuildable(construction: IConstruction): Boolean = construction.isBuildable(cityConstructions)
    
    @Readonly fun canPlaceCreateOneImprovementOn(improvement: TileImprovement, tileView: TileView): Boolean =
        cityConstructions.canPlaceCreateOneImprovementOn(improvement, tileView.unwrap())
    @Readonly fun getTileForImprovement(improvementName: String): TileView? =
        cityConstructions.getTileForImprovement(improvementName)?.let { gameView.tileMapView.getTile(it) }

    @Readonly fun getBuildableBuildings(): Sequence<Building> = cityConstructions.getBuildableBuildings()
    @Readonly fun getRemainingWork(name: String): Int = cityConstructions.getRemainingWork(name)
    @Readonly fun isQueueFull(): Boolean = cityConstructions.isQueueFull()
    @Readonly fun canAddToQueue(construction: IConstruction, queue: List<String> = constructionQueue): Boolean = cityConstructions.canAddToQueue(construction, queue)
    
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

// Helpers built on top of the view API, rather than being part of it
@Readonly fun CityConstructionsView.isBeingConstructedOrEnqueued(name: String): Boolean = name in constructionQueue
@Readonly fun CityConstructionsView.getCurrentConstruction(): IConstruction = getConstruction(currentConstructionName())

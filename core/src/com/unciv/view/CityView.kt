package com.unciv.view

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

/** View of a [City] from the perspective of [viewer]. UI should use this and not city directly. */
class CityView(internal val city: City, internal val viewer: Civilization) {
    val name: String get() = city.name

    @Readonly fun civ(): CivView = CivView(city.civ, viewer)
    @Readonly fun centerTile(): TileView = TileView(city.getCenterTile(), viewer)
    @Readonly fun getTiles(): Sequence<TileView> = city.getTiles().map { TileView(it, viewer) }
    @Readonly fun tileView(tile: Tile): TileView = TileView(tile, viewer)

    @Readonly fun getWorkRange(): Int = city.getWorkRange()
    @Readonly fun isWorked(tileView: TileView): Boolean = city.isWorked(tileView.tile)
    @Readonly fun canBuyTile(tileView: TileView): Boolean = city.expansion.canBuyTile(tileView.tile)
    @Readonly fun getGoldCostOfTile(tileView: TileView, extraTiles: Int = 0): Int =
        city.expansion.getGoldCostOfTile(tileView.tile, extraTiles)
    @Readonly fun isSameCivAs(other: CityView): Boolean = city.civ === other.city.civ

    // ACTIONS
    private fun canChangeState() = city.civ === viewer && viewer.isCurrentPlayer()

    fun tryLockTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        if (!isWorked(tileView)) return false
        return city.lockTile(tileView.tile)
    }
    fun tryUnlockTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        return city.unlockTile(tileView.tile)
    }
    fun tryBuyTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        if (!city.expansion.canBuyTile(tileView.tile)) return false
        city.expansion.buyTile(tileView.tile)
        return true
    }
    fun tryWorkTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        return city.workTile(tileView.tile)
    }
    fun tryStopWorkingTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        return city.stopWorkingTile(tileView.tile)
    }

    override fun equals(other: Any?) = other is CityView && city === other.city
    override fun hashCode() = city.hashCode()
}

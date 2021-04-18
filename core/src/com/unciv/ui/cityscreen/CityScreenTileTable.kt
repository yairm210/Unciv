package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.roundToInt

class CityScreenTileTable(private val cityScreen: CityScreen): Table() {
    private val innerTable = Table()
    val city = cityScreen.city

    init {
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f))
        add(innerTable).pad(2f).fill()
        background = ImageGetter.getBackground(Color.WHITE)
    }

    fun update(selectedTile: TileInfo?) {
        innerTable.clear()
        if (selectedTile == null) {
            isVisible = false
            return
        }
        isVisible = true
        innerTable.clearChildren()

        val stats = selectedTile.getTileStats(city, city.civInfo)
        innerTable.pad(5f)

        innerTable.add(selectedTile.toString(city.civInfo).toLabel()).colspan(2)
        innerTable.row()
        innerTable.add(getTileStatsTable(stats)).row()

        if (selectedTile.getOwner() == null && selectedTile.neighbors.any { it.getCity() == city }
                && selectedTile in city.tilesInRange) {
            addBuyTilesButton(setOf(selectedTile))

            val buyRing = getBuyTileRingSet(selectedTile)
            if (buyRing.size > 1) {
                addBuyTilesButton(buyRing)
            }
        }

        if (city.civInfo.cities.filterNot { it == city }.any { it.isWorked(selectedTile) })
            innerTable.add("Worked by [${selectedTile.getWorkingCity()!!.name}]".toLabel()).row()

        if (city.isWorked(selectedTile)) {
            if (selectedTile.isLocked()) {
                val unlockButton = "Unlock".toTextButton()
                unlockButton.onClick {
                    city.lockedTiles.remove(selectedTile.position)
                    update(selectedTile)
                    cityScreen.update()
                }
                if (!cityScreen.canChangeState) unlockButton.disable()
                innerTable.add(unlockButton).row()
            } else {
                val lockButton = "Lock".toTextButton()
                lockButton.onClick {
                    city.lockedTiles.add(selectedTile.position)
                    update(selectedTile)
                    cityScreen.update()
                }
                if (!cityScreen.canChangeState) lockButton.disable()
                innerTable.add(lockButton).row()
            }
        }
        if (selectedTile.isCityCenter() && selectedTile.getCity() != city && selectedTile.getCity()!!.civInfo == city.civInfo)
            innerTable.add("Move to city".toTextButton().onClick { cityScreen.game.setScreen(CityScreen(selectedTile.getCity()!!)) })

        innerTable.pack()
        pack()
    }

    private fun getTileStatsTable(stats: Stats): Table {
        val statsTable = Table()
        statsTable.defaults().pad(2f)
        for (entry in stats.toHashMap().filterNot { it.value == 0f }) {
            statsTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
            statsTable.add(entry.value.roundToInt().toString().toLabel()).padRight(5f)
        }
        return statsTable
    }

    /**
     * Create a buy tile(s) button
     * @param[tileSet] Set with one or more tiles to buy
     *      All tiles in set *must* have same distance from city center
     */
    private fun addBuyTilesButton(tileSet: Set<TileInfo>) {
        val tileCount = tileSet.size
        val goldCost = city.expansion.getGoldCostOfTile(tileSet.first(), tileCount)
        val buyButton = (
                if (tileCount==1) "Buy for [$goldCost] gold" else "Buy [$tileCount] tiles for [$goldCost] gold"
            ).toTextButton()
        val buyAction = {
            tileSet.forEach { city.expansion.buyTile(it) }
            UncivGame.Current.setScreen(CityScreen(city))
        }
        val purchasePrompt = "Currently you have [${city.civInfo.gold}] gold.".tr() + "\n" +
            (if (tileCount==1)
                "Would you like to purchase [Tile] for [$goldCost] gold?"
                else "Would you like to purchase [$tileCount] tiles for [$goldCost] gold?"
            ).tr()
        buyButton.onClick(UncivSound.Coin) {
            YesNoPopup(purchasePrompt, buyAction, cityScreen).open()
        }

        val canPurchase = goldCost == 0 || city.civInfo.gold >= goldCost
        if (!canPurchase && !city.civInfo.gameInfo.gameParameters.godMode
            || city.isPuppet
            || !cityScreen.canChangeState)
            buyButton.disable()

        innerTable.add(buyButton).padTop(5f).row()
    }

    /**
     * Simulates buying the passed tile and neighbours repeatedly until no further neighbour is buyable.
     * So if two other tiles of the ring are not buyable, this picks just the reachable contiguous
     * segment. This seemingly complicated approach ensures multi-buy can never reach tiles not
     * reachable by repeated single buy, e.g. in a complex pattern including enemy-owned tiles.
     * @param[tile] starting tile to determine ring/segment
     * @return Set of tiles that can be bought successively in the same ring
    */
    private fun getBuyTileRingSet(tile: TileInfo) : Set<TileInfo> {
        fun getBuyTileRingNext(tile: TileInfo, tileRing: MutableSet<TileInfo>): MutableSet<TileInfo> {
            // recursion step. Inner loop will iterate over at most 2 tiles, and that only
            // on first recursion. Max recursion depth = (full ring-3)/2+1 = 13: acceptable.
            tileRing.remove(tile)
            val nextSet = mutableSetOf(tile)
            tile.neighbors.filter { it in tileRing }.forEach {
                nextSet.addAll(getBuyTileRingNext(it, tileRing))
            }
            return nextSet
        }
        val distance = tile.aerialDistanceTo(city.getCenterTile())
        val tileRing = city.getCenterTile().getTilesAtDistance(distance)
            .filter { it.getOwner() == null }.toMutableSet()
        return getBuyTileRingNext(tile, tileRing)
    }

}

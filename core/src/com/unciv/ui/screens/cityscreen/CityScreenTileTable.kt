package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.TileDescription
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import kotlin.math.roundToInt

class CityScreenTileTable(private val cityScreen: CityScreen) : Table() {
    private val innerTable = Table()
    val city = cityScreen.city

    init {
        innerTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityScreenTileTable/InnerTable",
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
        add(innerTable).pad(2f).fill()
        background = BaseScreen.skinStrings.getUiBackground("CityScreen/CityScreenTileTable/Background", tintColor = Color.WHITE)
    }

    fun update(selectedTile: Tile?) {
        innerTable.clear()
        if (selectedTile == null) {
            isVisible = false
            return
        }
        isVisible = true
        innerTable.clearChildren()

        val stats = selectedTile.stats.getTileStats(city, city.civ)
        innerTable.pad(5f)

        innerTable.add(MarkupRenderer.render(TileDescription.toMarkup(
            selectedTile,
            city.civ,
            hideUnits = cityScreen.isSpying,
            spyCity = if (cityScreen.isSpying) cityScreen.city else null
        ), iconDisplay = IconDisplay.None) {
            cityScreen.openCivilopedia(it)
        })
        innerTable.row()
        innerTable.add(getTileStatsTable(stats)).row()

        if (city.expansion.canBuyTile(selectedTile)) {
            val goldCostOfTile = city.expansion.getGoldCostOfTile(selectedTile)
            val buyTileButton = "Buy for [$goldCostOfTile] gold".toTextButton()
            buyTileButton.onActivation(binding = KeyboardBinding.BuyTile) {
                buyTileButton.disable()
                cityScreen.askToBuyTile(selectedTile)
            }
            buyTileButton.isEnabled =  cityScreen.canChangeState && city.civ.hasStatToBuy(Stat.Gold, goldCostOfTile)
            innerTable.add(buyTileButton).padTop(5f).row()
        }

        // Add claim tile button if the tile is owned by another city of the same civilization
        if (canClaimTile(selectedTile)) {
            val claimTileButton = "Claim tile".toTextButton()
            claimTileButton.onActivation(binding = KeyboardBinding.ClaimTile) {
                claimTileButton.disable()
                cityScreen.claimTile(selectedTile)
            }
            claimTileButton.isEnabled = cityScreen.canChangeState
            innerTable.add(claimTileButton).padTop(5f).row()
        }

        if (selectedTile.owningCity != null)
            innerTable.add("Owned by [${selectedTile.owningCity!!.name}]".toLabel()).row()

        if (selectedTile.getWorkingCity() != null)
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
                innerTable.add(unlockButton).padTop(5f).row()
            } else {
                val lockButton = "Lock".toTextButton()
                lockButton.onClick {
                    city.lockedTiles.add(selectedTile.position)
                    update(selectedTile)
                    cityScreen.update()
                }
                if (!cityScreen.canChangeState) lockButton.disable()
                innerTable.add(lockButton).padTop(5f).row()
            }
        }
        if (selectedTile.isCityCenter()
            && selectedTile.getCity() != city
            && selectedTile.getCity()!!.civ == city.civ
            && !cityScreen.isSpying)
            innerTable.add("Move to city".toTextButton().onClick { cityScreen.game.replaceCurrentScreen(
                CityScreen(selectedTile.getCity()!!)
            ) })

        innerTable.pack()
        pack()
    }


    private fun getTileStatsTable(stats: Stats): Table {
        val statsTable = Table()
        statsTable.defaults().pad(2f)
        for ((key, value) in stats) {
            statsTable.add(ImageGetter.getStatIcon(key.name)).size(20f)
            statsTable.add(value.roundToInt().toLabel()).padRight(5f)
        }
        return statsTable
    }

    /** Checks if a tile can be claimed by this city.
     * A tile is claimable if:
     * - It is owned by another city of the same civilization
     * - It is adjacent to this city
     * - It is not a city center
     * - It is not in the first ring of either city (distance 1 from city center)
     * - It is within the claim range for both cities (both city work ranges)
     * - It does not have an improvement created by CreatesOneImprovement
     * - The ruleset allows tile claiming (ModOptions.AllowTileClaim)
     */
    @Readonly
    private fun canClaimTile(tile: Tile): Boolean {
        val owningCity = tile.getCity()
        return when {
            owningCity == null -> false
            owningCity.civ != city.civ -> false
            owningCity == city -> false
            tile.isCityCenter() -> false
            city.expansion.isFirstRingTile(tile) -> false
            owningCity.expansion.isFirstRingTile(tile) -> false
            !city.expansion.isWithinClaimRange(tile) -> false
            !owningCity.expansion.isWithinClaimRange(tile) -> false
            tile.improvementCreatedByCreatesOneImprovement != null -> false
            !city.civ.gameInfo.ruleset.modOptions.hasUnique(UniqueType.AllowTileClaim) -> false
            else -> tile.neighbors.any { it.getCity() == city }
        }
    }
}

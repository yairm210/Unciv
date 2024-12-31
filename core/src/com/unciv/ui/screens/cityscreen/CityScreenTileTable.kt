package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.tile.TileDescription
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import kotlin.math.roundToInt

class CityScreenTileTable(private val cityScreen: CityScreen) : Table() {
    private val innerTable = Table()
    val city = cityScreen.city

    init {
        innerTable.background = BaseScreen.skinStrings.getUiBackground(
            "CityScreen/CityScreenTileTable/InnerTable",
            tintColor = BaseScreen.skin.getColor("base-40")
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

        innerTable.add(MarkupRenderer.render(TileDescription.toMarkup(selectedTile, city.civ), iconDisplay = IconDisplay.None) {
            cityScreen.openCivilopedia(it)
        } )
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
        if (selectedTile.isCityCenter() && selectedTile.getCity() != city && selectedTile.getCity()!!.civ == city.civ)
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
}

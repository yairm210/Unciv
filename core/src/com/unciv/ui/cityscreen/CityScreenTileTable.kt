package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.UncivSound
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.civilopedia.FormattedLine.IconDisplay
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.ConfirmPopup
import com.unciv.ui.popup.closeAllPopups
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import kotlin.math.roundToInt

class CityScreenTileTable(private val cityScreen: CityScreen): Table() {
    private val innerTable = Table()
    val city = cityScreen.city

    init {
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().darken(0.5f))
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

        innerTable.add( MarkupRenderer.render(selectedTile.toMarkup(city.civInfo), iconDisplay = IconDisplay.None) {
            UncivGame.Current.pushScreen(CivilopediaScreen(city.getRuleset(), link = it))
        } )
        innerTable.row()
        innerTable.add(getTileStatsTable(stats)).row()

        if (isTilePurchaseShown(selectedTile)) {
            val goldCostOfTile = city.expansion.getGoldCostOfTile(selectedTile)
            val buyTileButton = "Buy for [$goldCostOfTile] gold".toTextButton()
            buyTileButton.onActivation {
                buyTileButton.disable()
                askToBuyTile(selectedTile)
            }
            buyTileButton.keyShortcuts.add('T')
            buyTileButton.isEnabled = isTilePurchaseAllowed(goldCostOfTile)
            buyTileButton.addTooltip('T')  // The key binding is done in CityScreen constructor
            innerTable.add(buyTileButton).padTop(5f).row()
        }

        if (selectedTile.owningCity != null)
            innerTable.add("Owned by [${selectedTile.owningCity!!.name}]".toLabel()).row()

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
        if (selectedTile.isCityCenter() && selectedTile.getCity() != city && selectedTile.getCity()!!.civInfo == city.civInfo)
            innerTable.add("Move to city".toTextButton().onClick { cityScreen.game.replaceCurrentScreen(CityScreen(selectedTile.getCity()!!)) })

        innerTable.pack()
        pack()
    }

    /** Ask whether user wants to buy [selectedTile] for gold.
     *
     * Used from onClick and keyboard dispatch, thus only minimal parameters are passed,
     * and it needs to do all checks and the sound as appropriate.
     */
    fun askToBuyTile(selectedTile: TileInfo) {
        // These checks are redundant for the onClick action, but not for the keyboard binding
        if (!isTilePurchaseShown(selectedTile)) return
        val goldCostOfTile = city.expansion.getGoldCostOfTile(selectedTile)
        if (!isTilePurchaseAllowed(goldCostOfTile)) return

        cityScreen.closeAllPopups()

        val purchasePrompt = "Currently you have [${city.civInfo.gold}] [Gold].".tr() + "\n\n" +
                "Would you like to purchase [Tile] for [$goldCostOfTile] [${Stat.Gold.character}]?".tr()
        ConfirmPopup(
            cityScreen,
            purchasePrompt,
            "Purchase",
            true,
            restoreDefault = { cityScreen.update() }
        ) {
            SoundPlayer.play(UncivSound.Coin)
            city.expansion.buyTile(selectedTile)
            // preselect the next tile on city screen rebuild so bulk buying can go faster
            UncivGame.Current.replaceCurrentScreen(CityScreen(city, initSelectedTile = city.expansion.chooseNewTileToOwn()))
        }.open()
    }

    /** This tests whether the buy button should be _shown_ */
    private fun isTilePurchaseShown(selectedTile: TileInfo) = when {
        selectedTile.getOwner() != null -> false
        selectedTile !in city.tilesInRange -> false
        else -> selectedTile.neighbors.any { it.getCity() == city }
    }
    /** This tests whether the buy button should be _enabled_ */
    private fun isTilePurchaseAllowed(goldCostOfTile: Int) = when {
        city.isPuppet -> false
        !cityScreen.canChangeState -> false
        city.civInfo.gameInfo.gameParameters.godMode -> true
        goldCostOfTile == 0 -> true
        else -> city.civInfo.gold >= goldCostOfTile
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

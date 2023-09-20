package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** A factory to create the "Unit Supply Breakdown" ExpanderTab */
object UnitSupplyTable {
    internal fun getUnitSupplyTable(parent: UnitOverviewTab): ExpanderTab {
        val overviewScreen = parent.overviewScreen
        val viewingPlayer = parent.viewingPlayer
        val stats = viewingPlayer.stats
        val supplyTableWidth = (overviewScreen.stage.width * 0.25f).coerceAtLeast(240f)

        val deficit = stats.getUnitSupplyDeficit()
        val icon = if (deficit <= 0) null else Group().apply {
            isTransform = false
            setSize(36f, 36f)
            val image = ImageGetter.getImage("OtherIcons/ExclamationMark")
            image.color = Color.FIREBRICK
            image.setSize(36f, 36f)
            image.center(this)
            addActor(image)
        }

        return ExpanderTab(
            title = "Unit Supply",
            fontSize = Constants.defaultFontSize,
            icon = icon,
            startsOutOpened = deficit > 0,
            defaultPad = 0f,
            expanderWidth = supplyTableWidth,
            onChange = {
                overviewScreen.resizePage(parent)
            }
        ) {
            it.defaults().pad(5f).fill(false)
            it.background = BaseScreen.skinStrings.getUiBackground(
                "OverviewScreen/UnitOverviewTab/UnitSupplyTable",
                tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.6f)
            )
            it.addLabeledValue("Base Supply", stats.getBaseUnitSupply())
            it.addLabeledValue("Cities", stats.getUnitSupplyFromCities())
            it.addLabeledValue("Population", stats.getUnitSupplyFromPop())
            it.addSeparator()
            it.addLabeledValue("Total Supply", stats.getUnitSupply())
            it.addLabeledValue("In Use", viewingPlayer.units.getCivUnitsSize())
            it.addSeparator()
            it.addLabeledValue("Supply Deficit", deficit)
            it.addLabeledValue("Production Penalty", "${stats.getUnitSupplyProductionPenalty().toInt()}%")
            if (deficit > 0) {
                val penaltyLabel = "Increase your supply or reduce the amount of units to remove the production penalty"
                    .toLabel(Color.FIREBRICK)
                penaltyLabel.wrap = true
                it.add(penaltyLabel).colspan(2).left()
                    .width(supplyTableWidth).row()
            }
        }
    }

    // Here overloads are simpler than a generic:
    private fun Table.addLabeledValue (label: String, value: Int) {
        add(label.toLabel()).left()
        add(value.toLabel()).right().row()
    }
    private fun Table.addLabeledValue (label: String, value: String) {
        add(label.toLabel()).left()
        add(value.toLabel()).right().row()
    }
}

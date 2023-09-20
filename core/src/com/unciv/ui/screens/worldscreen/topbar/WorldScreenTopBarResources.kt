package com.unciv.ui.screens.worldscreen.topbar

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.components.YearTextUtil
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.victoryscreen.VictoryScreen

internal class WorldScreenTopBarResources(topbar: WorldScreenTopBar) : Table() {
    private val turnsLabel = "Turns: 0/400".toLabel()
    private data class ResourceActors(val resource: TileResource, val label: Label, val icon: Group)
    private val resourceActors = ArrayList<ResourceActors>(12)
    private val resourcesWrapper = Table()

    init {
        resourcesWrapper.defaults().pad(5f, 5f, 10f, 5f)
        resourcesWrapper.touchable = Touchable.enabled

        val worldScreen = topbar.worldScreen

        turnsLabel.onClick {
            if (worldScreen.selectedCiv.isLongCountDisplay()) {
                val gameInfo = worldScreen.selectedCiv.gameInfo
                MayaCalendar.openPopup(worldScreen, worldScreen.selectedCiv, gameInfo.getYear())
            } else {
                worldScreen.game.pushScreen(VictoryScreen(worldScreen))
            }
        }
        resourcesWrapper.onClick {
            worldScreen.openEmpireOverview(EmpireOverviewCategories.Resources)
        }

        val strategicResources = worldScreen.gameInfo.ruleset.tileResources.values
            .filter { it.resourceType == ResourceType.Strategic && !it.hasUnique(UniqueType.CityResource) }
        for (resource in strategicResources) {
            val resourceImage = ImageGetter.getResourcePortrait(resource.name, 20f)
            val resourceLabel = "0".toLabel()
            resourceActors += ResourceActors(resource, resourceLabel, resourceImage)
        }

        // in case the icons are configured higher than a label, we add a dummy - height will be measured once before it's updated
        if (resourceActors.isNotEmpty()) {
            resourcesWrapper.add(resourceActors[0].icon)
            add(resourcesWrapper)
        }

        add(turnsLabel).pad(5f, 5f, 10f, 5f)
    }
    fun update(civInfo: Civilization) {
        val yearText = YearTextUtil.toYearText(
            civInfo.gameInfo.getYear(), civInfo.isLongCountDisplay()
        )
        turnsLabel.setText(Fonts.turn + "" + civInfo.gameInfo.turns + " | " + yearText)
        resourcesWrapper.clearChildren()
        var firstPadLeft = 20f  // We want a distance from the turns entry to the first resource, but only if any resource is displayed
        val civResources = civInfo.getCivResourcesByName()
        val civResourceSupply = civInfo.getCivResourceSupply()
        for ((resource, label, icon) in resourceActors) {
            if (resource.hasUnique(UniqueType.NotShownOnWorldScreen)) continue

            val amount = civResources[resource.name] ?: 0

            if (resource.revealedBy != null && !civInfo.tech.isResearched(resource.revealedBy!!)
                && amount == 0) // You can trade for resources you cannot process yourself yet
                continue

            resourcesWrapper.add(icon).padLeft(firstPadLeft).padRight(0f)
            firstPadLeft = 5f
            if (!resource.isStockpiled())
                label.setText(amount)
            else {
                val perTurn = civResourceSupply.firstOrNull { it.resource == resource }?.amount ?: 0
                if (perTurn == 0) label.setText(amount)
                else label.setText("$amount (${perTurn.toStringSigned()})")
            }
            resourcesWrapper.add(label).padTop(8f)  // digits don't have descenders, so push them down a little
        }

        pack()
    }
}

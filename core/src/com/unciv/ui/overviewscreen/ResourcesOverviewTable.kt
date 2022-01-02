package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

@Suppress("UNUSED_PARAMETER")       // Keep all OverviewScreen Pages compatible
class ResourcesOverviewTable (
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : Table() {

    init {
        defaults().pad(10f)

        val resourceDrilldown = viewingPlayer.detailedCivResources

        // First row of table has all the icons
        add()

        // Order of source ResourceSupplyList: by tiles, enumerating the map in that spiral pattern
        // UI should not surprise player, thus we need a deterministic and guessable order
        val resources = resourceDrilldown.map { it.resource }
            .filter { it.resourceType != ResourceType.Bonus }.distinct()
            .sortedWith(compareBy({ it.resourceType }, { it.name.tr() }))

        for (resource in resources) {
            // Create a group of label and icon for each resource.
            val resourceImage = ImageGetter.getResourceImage(resource.name, 50f)
            val labelPadding = 10f
            // Using a table here leads to spacing issues
            // due to different label lengths.
            val holder = Group()
            resourceImage.onClick {
                viewingPlayer.gameInfo.notifyExploredResources(viewingPlayer, resource.name, 0, true)
                overviewScreen.game.setWorldScreen()
            }
            holder.addActor(resourceImage)
            holder.setSize(resourceImage.width,
                resourceImage.height + labelPadding)
            // Center-align all labels, but right-align the last couple resources' labels
            // because they may get clipped otherwise. The leftmost label should be fine
            // center-aligned (if there are more than 2 resources), because the left side
            // has more padding.
            val alignFactor = when {
                (resources.indexOf(resource) + 2 >= resources.count()) -> 1
                else -> 2
            }
            add(holder)
        }
        addSeparator()

        val origins = resourceDrilldown.map { it.origin }.distinct()
        for (origin in origins) {
            add(origin.toLabel())
            for (resource in resources) {
                val resourceSupply = resourceDrilldown.firstOrNull { it.resource == resource && it.origin == origin }
                if (resourceSupply == null) add()
                else add(resourceSupply.amount.toString().toLabel())
            }
            row()
        }

        add("Total".toLabel())
        for (resource in resources) {
            val sum = resourceDrilldown.filter { it.resource == resource }.sumOf { it.amount }
            add(sum.toLabel())
        }
    }
}

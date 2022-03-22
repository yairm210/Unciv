package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip


class ResourcesOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class ResourcesTabPersistableData(
        var vertical: Boolean = false
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = !vertical
    }
    override val persistableData = (persistedData as? ResourcesTabPersistableData) ?: ResourcesTabPersistableData()

    companion object {
        private const val iconSize = 50f
        private const val defaultPad = 10f
    }

    private fun getTurnImage(vertical: Boolean) =
        ImageGetter.getImage("OtherIcons/Turn right")
            .apply {
                color = ImageGetter.getBlue()
                if (vertical)
                    rotateBy(90f)
            }
            .surroundWithCircle(iconSize, color = Color.LIGHT_GRAY)
    private val turnImageH = getTurnImage(false)
    private val turnImageV = getTurnImage(true)

    private val resourceDrilldown: ResourceSupplyList = viewingPlayer.detailedCivResources
    private val extraDrilldown: ResourceSupplyList = getExtraDrilldown()
    private val drilldownSequence = resourceDrilldown.asSequence() + extraDrilldown.asSequence()

    // Order of source ResourceSupplyList: by tiles, enumerating the map in that spiral pattern
    // UI should not surprise player, thus we need a deterministic and guessable order
    private val resources: List<TileResource> = drilldownSequence
        .map { it.resource }
        .filter { it.resourceType != ResourceType.Bonus }
        .distinct()
        .sortedWith(
            compareBy<TileResource> { it.resourceType }
                .thenBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr() }
        )
        .toList()
    private val origins = resourceDrilldown.asSequence().map { it.origin }.distinct().toList()
    private val extraOrigins = extraDrilldown.asSequence().map { it.origin }.distinct().toList()

    private fun ResourceSupplyList.getLabel(resource: TileResource, origin: String): Label? =
        firstOrNull { it.resource == resource && it.origin == origin }?.amount?.toLabel()
    private fun ResourceSupplyList.getTotalLabel(resource: TileResource): Label =
        filter { it.resource == resource }.sumOf { it.amount }.toLabel()
    private fun getResourceImage(name: String) =
        ImageGetter.getResourceImage(name, iconSize).apply {
            onClick {
                viewingPlayer.gameInfo.notifyExploredResources(viewingPlayer, name, 0, true)
                overviewScreen.game.setWorldScreen()
            }
            if (!persistableData.vertical)
                addTooltip(name)
        }

    private val fixedContent = Table()

    init {
        defaults().pad(defaultPad)
        fixedContent.defaults().pad(defaultPad)

        turnImageH.onClick {
            persistableData.vertical = true
            update()
        }
        turnImageV.onClick {
            persistableData.vertical = false
            update()
        }

        update()
    }

    override fun getFixedContent() = fixedContent

    private fun update() {
        clear()
        fixedContent.clear()
        if (persistableData.vertical) updateVertical()
        else updateHorizontal()
    }

    private fun updateHorizontal() {
        // First row of table has all the icons
        add(turnImageH)
        for (resource in resources) {
            add(getResourceImage(resource.name))
        }
        addSeparator()

        // One detail row per origin
        for (origin in origins) {
            add(origin.toLabel()).left()
            for (resource in resources) {
                add(resourceDrilldown.getLabel(resource, origin))
            }
            row()
        }
        addSeparator(Color.GRAY).pad(0f, defaultPad)

        // One row for the totals
        add("Total".toLabel()).left()
        for (resource in resources) {
            add(resourceDrilldown.getTotalLabel(resource))
        }
        addSeparator()

        // Separate rows for origins not part of the totals
        for (origin in extraOrigins) {
            add(origin.toLabel()).left()
            for (resource in resources) {
                add(extraDrilldown.getLabel(resource, origin))
            }
            row()
        }
    }

    private fun updateVertical() {
        // First row of table has all the origin labels
        fixedContent.apply {
            add(turnImageV)
            add()
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (origin in origins) {
                add(origin.toLabel())
            }
            add("Total".toLabel())
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (origin in extraOrigins) {
                when (origin) {
                    "We Love The King Day" ->
                        add("WLTK+".toLabel().apply { addTooltip(origin, 21f, tipAlign = Align.bottomLeft) })
                    "WLTK demand" ->
                        add("WLTK-".toLabel().apply { addTooltip(origin, 21f, tipAlign = Align.bottomLeft) })
                    else -> add(origin.toLabel())
                }
            }
            addSeparator().pad(0f, defaultPad)
        }

        // One detail row per resource
        for (resource in resources) {
            add(getResourceImage(resource.name))
            add(resource.name.toLabel())
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (origin in origins) {
                add(resourceDrilldown.getLabel(resource, origin))
            }
            add(resourceDrilldown.getTotalLabel(resource))
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (origin in extraOrigins) {
                add(extraDrilldown.getLabel(resource, origin))
            }
            row()
        }

        equalizeColumns(fixedContent, this)
    }

    private fun getExtraDrilldown(): ResourceSupplyList {
        val resourceSupplyList = ResourceSupplyList()
        for (city in viewingPlayer.cities) {
            if (city.demandedResource.isEmpty()) continue
            val wltkResource = gameInfo.ruleSet.tileResources[city.demandedResource] ?: continue
            if (city.isWeLoveTheKingDayActive()) {
                resourceSupplyList.add(wltkResource, 1, "We Love The King Day")
            } else {
                resourceSupplyList.add(wltkResource, -1, "WLTK demand")
            }
            for (tile in city.getTiles()) {
                if (tile.isCityCenter()) continue
                if (!tile.hasViewableResource(viewingPlayer)) continue
                val tileResource = tile.tileResource
                if (tileResource.resourceType == ResourceType.Bonus) continue
                if (tile.improvement == tileResource.improvement) continue
                if (tileResource.resourceType == ResourceType.Strategic && tile.getTileImprovement()?.isGreatImprovement() == true) continue
                resourceSupplyList.add(tileResource, -1, "unimproved")
            }
        }
        return resourceSupplyList
    }
}

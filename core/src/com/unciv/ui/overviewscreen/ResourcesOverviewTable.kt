package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*


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
    private val drilldownSequence = resourceDrilldown.asSequence()

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
            row()
        }

        equalizeColumns(fixedContent, this)
    }
}

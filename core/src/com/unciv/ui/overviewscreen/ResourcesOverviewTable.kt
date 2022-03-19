package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip


// TODO WLTK uses / demand (not in total)
// TODO Unimproved amount in territory (not in total)

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

    private val resourceDrilldown = viewingPlayer.detailedCivResources
    // Order of source ResourceSupplyList: by tiles, enumerating the map in that spiral pattern
    // UI should not surprise player, thus we need a deterministic and guessable order
    private val resources: List<TileResource> = resourceDrilldown.asSequence()
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
            if (!persistableData.vertical)
                addTooltip(name)
        }

    private val fixedContent = Table()

    private var fixScroll = true

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

    override fun activated(): Float? {
        //TODO kludge - this would be straightforward with a TabbedPager.addPage(initialScrollAlign) parameter
        if (!fixScroll) return null
        fixScroll = false
        (parent as? ScrollPane)?.scrollX = 0f
        return 0f
    }

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

        // Last row for the totals
        add("Total".toLabel()).left()
        for (resource in resources) {
            add(resourceDrilldown.getTotalLabel(resource))
        }
    }

    private fun updateVertical() {
        // First row of table has all the origin labels
        fixedContent.apply {
            add(turnImageV)
            add("".toLabel())  // equalizeColumns needs the Actor
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
            for (origin in origins) {
                add(resourceDrilldown.getLabel(resource, origin))
            }
            add(resourceDrilldown.getTotalLabel(resource))
            row()
        }

        fixedContent.pack()
        pack()
        equalizeColumns(fixedContent, this)
    }
}

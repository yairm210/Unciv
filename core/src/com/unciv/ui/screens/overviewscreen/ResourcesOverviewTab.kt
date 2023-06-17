package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen


class ResourcesOverviewTab(
    viewingPlayer: Civilization,
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
        private const val tooltipSize = 24f
    }

    private fun getTurnImage(vertical: Boolean) =
        ImageGetter.getImage("OtherIcons/Turn right")
            .apply {
                color = BaseScreen.skinStrings.skinConfig.baseColor
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
                .thenBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr(hideIcons = true) }
        )
        .toList()
    private val origins: List<String> = resourceDrilldown.asSequence()
        .map { it.origin }.distinct().toList()
    private val extraOrigins: List<ExtraInfoOrigin> = extraDrilldown.asSequence()
        .mapNotNull { ExtraInfoOrigin.safeValueOf(it.origin) }.distinct().toList()

    private fun ResourceSupplyList.getLabel(resource: TileResource, origin: String): Label? {
        val amount = get(resource, origin)?.amount ?: return null
        val label = if (resource.isStockpiled() && amount > 0) "+$amount".toLabel()
            else amount.toLabel()
        if (origin == ExtraInfoOrigin.Unimproved.name)
            label.onClick { showOneTimeNotification(
                gameInfo.getExploredResourcesNotification(viewingPlayer, resource.name) {
                    it.getOwner() == viewingPlayer && it.countAsUnimproved()
                }
            ) }
        return label
    }

    private fun ResourceSupplyList.getTotalLabel(resource: TileResource): Label {
        val total = filter { it.resource == resource }.sumOf { it.amount }
        return if (resource.isStockpiled() && total > 0) "+$total".toLabel()
        else total.toLabel()
    }

    private fun getResourceImage(name: String) =
        ImageGetter.getResourcePortrait(name, iconSize).apply {
            onClick { showOneTimeNotification(
                gameInfo.getExploredResourcesNotification(viewingPlayer, name)
            ) }
        }
    private fun TileResource.getLabel() = name.toLabel().apply {
        onClick {
            overviewScreen.game.pushScreen(CivilopediaScreen(gameInfo.ruleset, CivilopediaCategories.Resource, this@getLabel.name))
        }
    }

    private enum class ExtraInfoOrigin(
        val horizontalCaption: String,
        val verticalCaption: String,
        val tooltip: String
    ) {
        Unimproved("Unimproved", "Unimproved",
            "Number of tiles with this resource\nin your territory, without an\nappropriate improvement to use it"),
        CelebratingWLKT("We Love The King Day", "WLTK+",
            "Number of your cities celebrating\n'We Love The King Day' thanks\nto access to this resource"),
        DemandingWLTK("WLTK demand", "WLTK-",
            "Number of your cities\ndemanding this resource for\n'We Love The King Day'"),
        TradeOffer("Trade offer","Trade offer", "Resources we're offering in trades")
        ;
        companion object {
            fun safeValueOf(name: String) = values().firstOrNull { it.name == name }
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
            add(getResourceImage(resource.name).apply {
                addTooltip(resource.name, tipAlign = Align.topLeft, hideIcons = true)
            })
        }
        addSeparator()

        // One detail row per origin
        for (origin in origins) {
            add(origin.removeSuffix("+").toLabel()).left()
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
            add(origin.horizontalCaption.toLabel().apply {
                addTooltip(origin.tooltip, tooltipSize, tipAlign = Align.left)
            }).left()
            for (resource in resources) {
                add(extraDrilldown.getLabel(resource, origin.name))
            }
            row()
        }
    }

    private fun updateVertical() {
        val groupedOrigins = origins
            .groupBy { it.removeSuffix("+") }

        // First row of table has all the origin labels
        fixedContent.apply {
            add(turnImageV).size(iconSize)
            add()
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (origin in groupedOrigins) {
                add(origin.key.toLabel())
            }
            add("Total".toLabel())
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (origin in extraOrigins) {
                add(origin.verticalCaption.toLabel().apply {
                    addTooltip(origin.tooltip, tooltipSize, targetAlign = Align.bottom, tipAlign = Align.topRight)
                })
            }
            addSeparator().pad(0f, defaultPad)
        }

        // One detail row per resource
        for (resource in resources) {
            add(getResourceImage(resource.name))
            add(resource.getLabel())
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (groupedOrigin in groupedOrigins) {
                if (groupedOrigin.value.size == 1)
                    add(resourceDrilldown.getLabel(resource, groupedOrigin.key))
                else
                    add(Table().apply {
                        for (origin in groupedOrigin.value.withIndex())
                            add(resourceDrilldown.getLabel(resource, origin.value))
                                .padLeft(if (origin.index == 0) 0f else defaultPad)
                    })
            }
            add(resourceDrilldown.getTotalLabel(resource))
            addSeparatorVertical(Color.GRAY).pad(0f)
            for (origin in extraOrigins) {
                add(extraDrilldown.getLabel(resource, origin.name))
            }
            row()
        }

        equalizeColumns(fixedContent, this)
        overviewScreen.resizePage(this)  // Without the height is miscalculated - shouldn't be
    }

    private fun Tile.countAsUnimproved(): Boolean = resource != null &&
            tileResource.resourceType != ResourceType.Bonus &&
            hasViewableResource(viewingPlayer) &&
            !providesResources(viewingPlayer)

    private fun getExtraDrilldown(): ResourceSupplyList {
        val newResourceSupplyList = ResourceSupplyList()
        for (city in viewingPlayer.cities) {
            if (city.demandedResource.isNotEmpty()) {
                val wltkResource = gameInfo.ruleset.tileResources[city.demandedResource]!!
                if (city.isWeLoveTheKingDayActive()) {
                    newResourceSupplyList.add(wltkResource, ExtraInfoOrigin.CelebratingWLKT.name)
                } else {
                    newResourceSupplyList.add(wltkResource, ExtraInfoOrigin.DemandingWLTK.name)
                }
            }
            for (tile in city.getTiles())
                if (tile.countAsUnimproved())
                    newResourceSupplyList.add(tile.tileResource, ExtraInfoOrigin.Unimproved.name)
        }

        for (otherCiv in viewingPlayer.getKnownCivs())
            for (trade in otherCiv.tradeRequests.filter { it.requestingCiv == viewingPlayer.civName })
                for (offer in trade.trade.theirOffers.filter{it.type == TradeType.Strategic_Resource || it.type == TradeType.Luxury_Resource})
                    newResourceSupplyList.add(gameInfo.ruleset.tileResources[offer.name]!!, ExtraInfoOrigin.TradeOffer.name, offer.amount)

        return newResourceSupplyList
    }
}

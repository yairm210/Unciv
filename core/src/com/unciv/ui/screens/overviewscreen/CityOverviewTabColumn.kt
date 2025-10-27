package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.unciv.logic.GameInfo
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.ISortableGridContentProvider
import com.unciv.ui.components.ISortableGridContentProvider.Companion.collator
import com.unciv.ui.components.ISortableGridContentProvider.Companion.getCircledIcon
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.SortableGrid
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.cityscreen.CityScreen
import kotlin.math.roundToInt


/**
 * This defines all behaviour of the [CityOverviewTab] columns through overridable parts
 */

// Note: Using type hints on compareBy where explicitly typing the lambda `it` instead would be prettier.
// detekt would false-positive the typed `it`, see discussion in: https://github.com/detekt/detekt/pull/6367

enum class CityOverviewTabColumn : ISortableGridContentProvider<City, EmpireOverviewScreen> {
    //region Enum Instances
    CityColumn {
        override val headerTip = "Name"
        override val align = Align.left
        override val fillX = true
        override val defaultSort get() = SortableGrid.SortDirection.Ascending
        override fun getComparator() = compareBy<City, String>(collator) { it.name.tr(hideIcons = true) }
        override fun getHeaderActor(iconSize: Float) =
                ImageGetter.getImage("UnitIcons/Settler").apply { color = ImageGetter.CHARCOAL }
                .surroundWithCircle(iconSize)
        override fun getEntryValue(item: City) = 0  // make sure that `stat!!` in the super isn't used
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen) =
                item.name.toTextButton(hideIcons = true)
                .onClick {
                    actionContext.game.pushScreen(CityScreen(item))
                }
        override fun getTotalsActor(items: Iterable<City>) = "{Total} ${items.count()}".toLabel()
    },

    Status {
        override val headerTip = "Status\n(puppet, resistance or being razed)"
        override fun getHeaderActor(iconSize: Float) = ImageGetter.getImage("OtherIcons/CityStatus")
        override fun getEntryValue(item: City) = when {
            item.isBeingRazed -> 3
            item.isInResistance() -> 2
            item.isPuppet -> 1
            else -> 0
        }
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen): Actor? {
            val iconPath = when {
                item.isBeingRazed -> "OtherIcons/Fire"
                item.isInResistance() -> "StatIcons/Resistance"
                item.isPuppet -> "OtherIcons/Puppet"
                else -> return null
            }
            // getImage is an ImageWithCustomSize, but setting size here fails - width is not respected
            return ImageGetter.getImage(iconPath).surroundWithCircle(iconSize * 0.7f, color = Color.CLEAR)
        }
        override fun getTotalsActor(items: Iterable<City>) = null  // an intended empty space
    },

    ConstructionIcon {
        override fun getHeaderActor(iconSize: Float) = null
        override fun getEntryValue(item: City) =
                item.cityConstructions.run { turnsToConstruction(currentConstructionFromQueue) }
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen): Actor? {
            val construction = item.cityConstructions.currentConstructionFromQueue
            if (construction.isEmpty()) return null
            return ImageGetter.getConstructionPortrait(construction, iconSize * 0.8f)
        }
        override fun getTotalsActor(items: Iterable<City>) = null  // an intended empty space
    },

    Construction {
        override val align = Align.left
        override val expandX = false
        override val equalizeHeight = true
        override val headerTip = "Current construction"
        override val defaultSort get() = SortableGrid.SortDirection.Ascending
        override fun getComparator() =
            compareBy<City, String>(collator) { it.cityConstructions.currentConstructionFromQueue.tr(hideIcons = true) }
        override fun getHeaderActor(iconSize: Float) =
                getCircledIcon("OtherIcons/Settings", iconSize)
        override fun getEntryValue(item: City) = 0
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen) =
            item.cityConstructions.getCityProductionTextForCityButton().toLabel()
        override fun getTotalsActor(items: Iterable<City>) = null  // an intended empty space
    },

    Population {
        override fun getEntryValue(item: City) = item.population.population
    },

    Food,
    Gold,
    Science,
    Production,
    Culture,
    Happiness {
        override fun getEntryValue(item: City) =
            item.cityStats.happinessList.values.sum().roundToInt()
    },
    Faith {
        override fun isVisible(gameInfo: GameInfo) =
            gameInfo.isReligionEnabled()
    },

    WLTK {
        override val headerTip = "We Love The King Day"
        override val defaultSort get() = SortableGrid.SortDirection.Ascending
        override fun getComparator() =
            super.getComparator().thenBy { it.demandedResource.tr(hideIcons = true) }
        override fun getHeaderActor(iconSize: Float) =
                getCircledIcon("OtherIcons/WLTK 2", iconSize, Color.TAN)
        override fun getEntryValue(item: City) =
                if (item.isWeLoveTheKingDayActive()) 1 else 0
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen) = when {
            item.isWeLoveTheKingDayActive() -> {
                ImageGetter.getImage("OtherIcons/WLTK 1")
                    .surroundWithCircle(iconSize, color = Color.CLEAR)
                    .apply {
                        addTooltip("[${item.getFlag(CityFlags.WeLoveTheKing)}] turns", 18f, tipAlign = Align.topLeft)
                    }
            }
            item.demandedResource.isNotEmpty() -> {
                ImageGetter.getResourcePortrait(item.demandedResource, iconSize * 0.7f).apply {
                    addTooltip("Demanding [${item.demandedResource}]", 18f, tipAlign = Align.topLeft)
                    onClick { actionContext.showOneTimeNotification(
                        item.civ.gameInfo.getExploredResourcesNotification(item.civ, item.demandedResource)
                    ) }
                }
            }
            else -> null
        }
    },

    Garrison {
        override val headerTip = "Garrisoned by unit"
        override val defaultSort get() = SortableGrid.SortDirection.Ascending
        override fun getComparator() =
            compareBy<City, String>(collator) { it.getGarrison()?.name?.tr(hideIcons = true) ?: "" }
        override fun getHeaderActor(iconSize: Float) =
                getCircledIcon("OtherIcons/Shield", iconSize)
        override fun getEntryValue(item: City) =
                if (item.getGarrison() != null) 1 else 0
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen): Actor? {
            val unit = item.getGarrison() ?: return null
            val unitName = unit.displayName()
            val unitIcon = ImageGetter.getConstructionPortrait(unit.baseUnit.getIconName(), iconSize * 0.7f)
            unitIcon.addTooltip(unitName, 18f, tipAlign = Align.topLeft)
            unitIcon.onClick {
                actionContext.select(EmpireOverviewCategories.Units, unit.id.toString())
            }
            return unitIcon
        }
    },

    CityDefense {
        override val headerTip = "City defense"
        override val defaultSort get() = SortableGrid.SortDirection.Ascending
        override fun getComparator() = compareBy<City> { getEntryValue(it) }.thenBy { it.getMaxHealth() }
        override fun getHeaderActor(iconSize: Float) = getCircledIcon("BuildingIcons/Walls", iconSize)
        override fun getEntryValue(item: City) = CityCombatant(item).getDefendingStrength()
        override fun getEntryActor(item: City, iconSize: Float, actionContext: EmpireOverviewScreen) =
            "${getEntryValue(item)}/${item.getMaxHealth()}".toLabel()
        override fun getTotalsActor(items: Iterable<City>) = null  // an intended empty space
    };

    //endregion

    /** The Stat constant if this is a Stat column - helps the default getter methods */
    private val stat = Stat.safeValueOf(name)

    //region Overridable fields

    override val headerTip get() = name
    override val align = Align.center
    override val fillX = false
    override val expandX = false
    override val equalizeHeight = false
    override val defaultSort get() = SortableGrid.SortDirection.Descending
    //endregion
    //region Overridable methods

    /** Factory for the header cell [Actor]
     * - Must override unless a texture exists for "StatIcons/$name" - e.g. a [Stat] column or [Population].
     * - _Should_ be sized to [iconSize].
     */
    override fun getHeaderActor(iconSize: Float): Actor? =
            ImageGetter.getStatIcon(name)

    /** A getter for the numeric value to display in a cell
     * - The default implementation works only on [Stat] columns, so an override is mandatory unless
     *   it's a [Stat] _or_ all three methods mentioned below have overrides.
     * - By default this feeds [getComparator], [getEntryActor] _and_ [getTotalsActor],
     *   so an override may be useful for sorting and/or a total even if you do override [getEntryActor].
     */
    override fun getEntryValue(item: City): Int =
            item.cityStats.currentCityStats[stat!!].roundToInt()

    //endregion
}

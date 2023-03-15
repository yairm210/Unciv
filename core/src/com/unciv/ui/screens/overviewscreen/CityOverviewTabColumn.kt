package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.cityscreen.CityScreen
import kotlin.math.roundToInt


/**
 * This defines all behaviour of the [CityOverviewTab] columns through overridable parts:
 * - [isVisible][CityOverviewTabColumn.isVisible] can hide a column
 * - [align], [fillX], [expandX], [equalizeHeight] control geometry
 * - [getComparator] or [getEntryValue] control sorting
 * - [headerTip] and [getHeaderIcon] define how the header row looks
 * - [getEntryValue] or [getEntryActor] define what the cells display
 * - [getEntryValue] or [getTotalsActor] define what the totals row displays
 */
@Suppress("unused")
enum class CityOverviewTabColumn {
    //region Enum Instances
    CityColumn {
        //override val label = "City"
        override val headerTip = "Name"
        override val align = Align.left
        override val fillX = true
        override fun getComparator() = Comparator { city2: City, city1: City ->
            collator.compare(city2.name.tr(), city1.name.tr())
        }
        override fun getHeaderIcon() =
                ImageGetter.getUnitIcon("Settler")
                .surroundWithCircle(CityOverviewTab.iconSize)
        override fun getEntryValue(city: City) = 0  // make sure that `stat!!` in the super isn't used
        override fun getEntryActor(city: City, overviewScreen: EmpireOverviewScreen) =
                city.name.toTextButton()
                .onClick {
                    overviewScreen.game.pushScreen(CityScreen(city))
                }

        override fun getTotalsActor(cities: Iterable<City>) =
                "Total".toLabel()
    },

    ConstructionIcon {
        override fun getHeaderIcon() = null
        override fun getEntryValue(city: City) =
                city.cityConstructions.run { turnsToConstruction(currentConstructionFromQueue) }
        override fun getEntryActor(city: City, overviewScreen: EmpireOverviewScreen): Actor? {
            val construction = city.cityConstructions.currentConstructionFromQueue
            if (construction.isEmpty()) return null
            return ImageGetter.getConstructionPortrait(construction, CityOverviewTab.iconSize *0.8f)
        }
        override fun getTotalsActor(cities: Iterable<City>) = null
    },

    Construction {
        override val align = Align.left
        override val expandX = true
        override val equalizeHeight = true
        override val headerTip = "Current construction"
        override fun getComparator() = Comparator { city2: City, city1: City ->
            collator.compare(
                city2.cityConstructions.currentConstructionFromQueue.tr(),
                city1.cityConstructions.currentConstructionFromQueue.tr()
            )
        }
        override fun getHeaderIcon() =
                getCircledIcon("OtherIcons/Settings")
        override fun getEntryValue(city: City) = 0
        override fun getEntryActor(city: City, overviewScreen: EmpireOverviewScreen) =
            city.cityConstructions.getCityProductionTextForCityButton().toLabel()
        override fun getTotalsActor(cities: Iterable<City>) = null
    },

    Population {
        override fun getEntryValue(city: City) =
                city.population.population
    },

    Food {
        override fun getTotalsActor(cities: Iterable<City>) = null  // an intended empty space
    },
    Gold,
    Science,
    Production{
        override fun getTotalsActor(cities: Iterable<City>) = null  // an intended empty space
    },
    Culture,
    Happiness {
        override fun getEntryValue(city: City) =
                city.cityStats.happinessList.values.sum().roundToInt()
    },
    Faith {
        override fun isVisible(gameInfo: GameInfo) =
                gameInfo.isReligionEnabled()
    },

    WLTK {
        override val headerTip = "We Love The King Day"
        override fun getHeaderIcon() =
                getCircledIcon("OtherIcons/WLTK 2", Color.TAN)
        override fun getEntryValue(city: City) =
                if (city.isWeLoveTheKingDayActive()) 1 else 0
        override fun getEntryActor(city: City, overviewScreen: EmpireOverviewScreen) = when {
            city.isWeLoveTheKingDayActive() -> {
                ImageGetter.getImage("OtherIcons/WLTK 1")
                    .surroundWithCircle(CityOverviewTab.iconSize, color = Color.CLEAR)
                    .apply {
                        addTooltip("[${city.getFlag(CityFlags.WeLoveTheKing)}] turns", 18f, tipAlign = Align.topLeft)
                    }
            }
            city.demandedResource.isNotEmpty() -> {
                ImageGetter.getResourcePortrait(city.demandedResource, CityOverviewTab.iconSize *0.7f)
                    .apply {
                        addTooltip("Demanding [${city.demandedResource}]", 18f, tipAlign = Align.topLeft)
                    }
            }
            else -> null
        }
    },

    Garrison {
        override val headerTip = "Garrisoned by unit"
        override fun getComparator() = Comparator { city2: City, city1: City ->
            collator.compare(
                city2.getCenterTile().militaryUnit?.name?.tr() ?: "",
                city1.getCenterTile().militaryUnit?.name?.tr() ?: ""
            )
        }
        override fun getHeaderIcon() =
                getCircledIcon("OtherIcons/Shield")
        override fun getEntryValue(city: City) =
                if (city.getCenterTile().militaryUnit != null) 1 else 0
        override fun getEntryActor(city: City, overviewScreen: EmpireOverviewScreen): Actor? {
            val unit = city.getCenterTile().militaryUnit ?: return null
            val unitName = unit.displayName()
            val unitIcon = ImageGetter.getConstructionPortrait(unit.baseUnit.getIconName(), CityOverviewTab.iconSize * 0.7f)
            unitIcon.addTooltip(unitName, 18f, tipAlign = Align.topLeft)
            unitIcon.onClick {
                overviewScreen.select(EmpireOverviewCategories.Units, UnitOverviewTab.getUnitIdentifier(unit) )
            }
            return unitIcon
        }
    },
    ;

    //endregion

    /** The Stat constant if this is a Stat column - helps the default getter methods */
    private val stat = Stat.safeValueOf(name)

    //region Overridable fields

    /** tooltip for the column header, defaults to enum name, will be auto-translated */
    open val headerTip get() = name
    /** [Cell.align] - used on header, entry and total cells */
    open val align = Align.center
    /** [Cell.fillX] - used on header, entry and total cells */
    open val fillX = false
    /** [Cell.expandX] - used on header, entry and total cells */
    open val expandX = false
    /** When overridden `true`, the entry cells of this column will be equalized to their max height */
    open val equalizeHeight = false

    //endregion
    //region Overridable methods

    /** @return whether the column should be rendered */
    open fun isVisible(gameInfo: GameInfo) = true

    /** [Comparator] Factory used for sorting.
     * - Note: determine default ordering here by inverting the result if needed.
     * - The default will sort by [getEntryValue] descending.
     * @return positive to sort second lambda argument before first lambda argument
     */
    open fun getComparator() = Comparator { city2: City, city1: City ->
        getEntryValue(city1) - getEntryValue(city2)
    }

    /** Factory for the header cell [Actor]
     * - Must override unless a texture exists for "StatIcons/$name" - e.g. a [Stat] column or [Population].
     * - _Should_ be sized to [CityOverviewTab.iconSize].
     */
    open fun getHeaderIcon(): Actor? =
            ImageGetter.getStatIcon(name)

    /** A getter for the numeric value to display in a cell
     * - The default implementation works only on [Stat] columns, so an override is mandatory unless
     *   it's a [Stat] _or_ all three methods mentioned below have overrides.
     * - By default this feeds [getComparator], [getEntryActor] _and_ [getTotalsActor],
     *   so an override may be useful for sorting and/or a total even if you do override [getEntryActor].
     */
    open fun getEntryValue(city: City): Int =
            city.cityStats.currentCityStats[stat!!].roundToInt()

    /** Factory for entry cell [Actor]
     * - By default displays the (numeric) result of [getEntryValue].
     * - [overviewScreen] can be used to define `onClick` actions.
     */
    open fun getEntryActor(city: City, overviewScreen: EmpireOverviewScreen): Actor? =
            getEntryValue(city).toCenteredLabel()

    /** Factory for totals cell [Actor]
     * - By default displays the sum over [getEntryValue].
     * - Note a count may be meaningful even if entry cells display something other than a number,
     *   In that case _not_ overriding this and supply a meaningful [getEntryValue] may be easier.
     * - On the other hand, a sum may not be meaningful even if the cells are numbers - to leave
     *   the total empty override to return `null`.
     */
    open fun getTotalsActor(cities: Iterable<City>): Actor? =
            cities.sumOf { getEntryValue(it) }.toCenteredLabel()

    //endregion

    companion object {
        private val collator = UncivGame.Current.settings.getCollatorFromLocale()

        private fun getCircledIcon(path: String, circleColor: Color = Color.LIGHT_GRAY) =
                ImageGetter.getImage(path)
                .apply { color = Color.BLACK }
                .surroundWithCircle(CityOverviewTab.iconSize, color = circleColor)

        private fun Int.toCenteredLabel(): Label =
                this.toLabel().apply { setAlignment(Align.center) }
    }
}

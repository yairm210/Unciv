package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.translations.tr
import com.unciv.ui.components.ISortableGridContentProvider
import com.unciv.ui.components.ISortableGridContentProvider.Companion.toCenteredLabel
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.SortableGrid
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.pickerscreens.UnitRenamePopup
import yairm210.purity.annotations.Readonly

//todo Extending getEntryValue here to have a second String-based "channel" - could go into SortableGrid, possibly by defining a DataType per column???

enum class UnitOverviewTabColumn(
    private val headerLabel: String? = null,
    override val headerTip: String = "",
    private val isNumeric: Boolean = false
) : ISortableGridContentProvider<MapUnit, UnitOverviewTab> {
    //region Enum Instances
    Name {
        override val fillX = true
        override fun getEntryString(item: MapUnit) = item.displayName().tr(hideIcons = true)
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor {
            // Unit button column - name, health, fortified, sleeping, embarked are visible here
            val button = IconTextButton(
                item.displayName(),
                UnitIconGroup(item, 20f).apply { if (!unit.isIdle()) color.a = 0.5f },
                fontColor = if (item.isIdle()) Color.WHITE else Color.LIGHT_GRAY
            )
            button.name = getUnitIdentifier(item)  // Marker to find a unit in select()
            button.onClick {
                showWorldScreenAt(item)
            }
            return button
        }
        override fun getTotalsActor(items: Iterable<MapUnit>) = items.count().toCenteredLabel()
    },

    EditName("") {
        override val defaultSort get() = SortableGrid.SortDirection.None
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor {
            val selectKey = getUnitIdentifier(item)
            val editIcon = ImageGetter.getImage("OtherIcons/Pencil")
                .apply { this.color = Color.WHITE }
                .surroundWithCircle(30f, true, Color(0x000c31))
            editIcon.onClick {
                UnitRenamePopup(actionContext.overviewScreen, item) {
                    actionContext.update()
                    actionContext.overviewScreen.select(EmpireOverviewCategories.Units, selectKey)
                }
            }
            return editIcon
        }
    },

    Action {
        override fun getEntryString(item: MapUnit): String? = getActionText(item)?.tr() // to retain the icon for e.g. unit action
    },

    Strength(Fonts.strength.toString(), "Strength", true) {
        override val defaultSort get() = SortableGrid.SortDirection.Descending
        override fun getEntryValue(item: MapUnit) = item.baseUnit.strength
    },
    RangedStrength(Fonts.rangedStrength.toString(), "Ranged strength", true) {
        override val defaultSort get() = SortableGrid.SortDirection.Descending
        override fun getEntryValue(item: MapUnit) = item.baseUnit.rangedStrength
    },
    Movement(Fonts.movement.toString(), "Movement", true) {
        override val defaultSort get() = SortableGrid.SortDirection.Descending
        override fun getEntryString(item: MapUnit) = item.getMovementString()
        override fun getComparator() = compareBy<MapUnit> { it.getMaxMovement() }.thenBy { it.currentMovement }
    },

    ClosestCity("Closest city") {
        //todo these overrides call a getTilesInDistance(3).firstOrNull loop independently and possibly repeatedly - caching?
        override fun getEntryString(item: MapUnit) = getClosestCityTile(item)?.getCity()?.name?.tr(hideIcons = true)

        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor? {
            val closestCityTile = getClosestCityTile(item) ?: return null
            val cityColor = if (item.getTile() == closestCityTile) Color.FOREST.brighten(0.5f) else Color.WHITE
            val label = closestCityTile.getCity()!!.name.toLabel(fontColor = cityColor, alignment = Align.center)
            label.onClick { showWorldScreenAt(closestCityTile) }
            return label
        }
        @Readonly
        private fun getClosestCityTile(item: MapUnit) = item.getTile()
            .getTilesInDistance(3).firstOrNull { it.isCityCenter() }
    },

    Promotions(isNumeric = true) {
        override val defaultSort get() = SortableGrid.SortDirection.Descending
        override fun getEntryValue(item: MapUnit) =
            (if (item.promotions.canBePromoted()) 10000 else 0) +
            item.promotions.promotions.size // Not numberOfPromotions - DO count free ones. Or sort by totalXpProduced?
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab) = getPromotionsTable(item, actionContext)
    },

    Upgrade {
        //todo these overrides call UnitActionsUpgrade.getUpgradeActionAnywhere independently and possibly repeatedly - caching?
        override fun getEntryString(item: MapUnit) = getUpgradeSortString(item)
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab) = getUpgradeTable(item, actionContext)
        override fun getTotalsActor(items: Iterable<MapUnit>) = items.count { getUpgradeSortString(it) != null }.toCenteredLabel()
    },

    Health(isNumeric = true) {
        override fun getEntryValue(item: MapUnit) = item.health
        override fun getEntryString(item: MapUnit) = if (item.health == 100) null else item.health.tr()
        override fun getTotalsActor(items: Iterable<MapUnit>) = items.count { it.health < 100 }.toCenteredLabel()
    },

    XP {
        override fun getEntryValue(item: MapUnit) = item.promotions.XP
        override fun getEntryString(item: MapUnit) = if (item.isCivilian()) ""
            else "{${item.promotions.XP}}/{${item.promotions.xpForNextPromotion()}}"
        override fun getComparator() = compareBy<MapUnit> { it.promotions.xpForNextPromotion() }.thenBy { it.promotions.XP }
        override fun getTotalsActor(items: Iterable<MapUnit>) = items.sumOf { it.promotions.XP }.toLabel()
    },
    ;
    //endregion

    //region Overridden superclass fields
    override val align = Align.center
    override val fillX = false
    override val expandX = false
    override val equalizeHeight = false
    override val defaultSort get() = SortableGrid.SortDirection.Ascending
    //endregion

    @Readonly open fun getEntryString(item: MapUnit): String? = getEntryValue(item).takeIf { it > 0 }?.tr()

    //region Overridden superclass methods
    override fun getHeaderActor(iconSize: Float) = (headerLabel ?: name).toLabel()
    @Readonly override fun getEntryValue(item: MapUnit) = 0
    override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor? =
        getEntryString(item)?.toLabel(alignment = Align.center, hideIcons = true)
    override fun getComparator() = if (isNumeric) super.getComparator()
        // Sort empty cells to the end by faking a `String.MAX_VALUE` - to do it properly would be a far more verbose Comparator subclass
        else compareBy(ISortableGridContentProvider.collator) { getEntryString(it)?.tr(hideIcons = true) ?: "\uD83D\uDE00zzz" }
    override fun getTotalsActor(items: Iterable<MapUnit>): Actor? = null
    //endregion

    companion object : UnitOverviewTabHelpers()
}

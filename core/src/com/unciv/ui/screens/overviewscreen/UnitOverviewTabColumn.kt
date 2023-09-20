package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.translations.tr
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.ISortableGridContentProvider
import com.unciv.ui.components.UnitGroup
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.UnitUpgradeMenu
import com.unciv.ui.screens.pickerscreens.UnitRenamePopup
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import kotlin.math.abs

//todo Grid Framework does not respect header actor widths well
//todo Grid Framework has header-body column alignment issues when separateHeader is on
//todo Grid Framework has header-body column alignment issues when separateHeader is off, too!
//todo Grid Framework use of iconSize feels off here
//todo Grid Framework could learn string cells, and cache them for faster sort

/**
 * This defines all behaviour of the [UnitOverviewTab] columns
 */
enum class UnitOverviewTabColumn : ISortableGridContentProvider<MapUnit, UnitOverviewTab> {
    //region Enum Instances
    Name {
        // Unit button column - name, health, fortified, sleeping, embarked are visible here
        override val fillX = true
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab) =
            IconTextButton(
                item.displayName(),
                UnitGroup(item, 20f).apply { if (!item.isIdle()) color.a = 0.5f },
                fontColor = if (item.isIdle()) Color.WHITE else Color.LIGHT_GRAY
            ).apply {
                name = UnitOverviewTab.getUnitIdentifier(item)
                onClick { showWorldScreenAt(item) }
            }
        override fun getComparator() =
            compareBy<MapUnit, String>(collator) { it.displayName().tr(hideIcons = true) }
            .thenByDescending { it.due }
            .thenBy { it.currentMovement <= Constants.minimumMovementEpsilon }
            .thenBy { abs(it.currentTile.position.x) + abs(it.currentTile.position.y) }
    },

    EditName {
        override val headerTip = "Edit name"
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab) =
            ImageGetter.getImage("OtherIcons/Pencil")
                .apply { this.color = Color.WHITE }
                .surroundWithCircle(30f, true, Color.valueOf("000c31"))
                .onClick { UnitRenamePopup(actionContext.overviewScreen, item) {
                    actionContext.updateAndSelect(UnitOverviewTab.getUnitIdentifier(item))
                }}
        override fun getComparator() =
            compareBy<MapUnit, String>(collator) { it.instanceName.orEmpty() }
                .thenBy(collator) { it.name.tr(hideIcons = true) }
    },

    Action {
        private fun getWorkerActionText(unit: MapUnit): String? = when {
            // See UnitTurnManager.endTurn, if..workOnImprovement or UnitGroup.getActionImage: similar logic
            !unit.cache.hasUniqueToBuildImprovements -> null
            unit.currentMovement == 0f -> null
            unit.currentTile.improvementInProgress == null -> null
            !unit.canBuildImprovement(unit.getTile().getTileImprovementInProgress()!!) -> null
            else -> unit.currentTile.improvementInProgress
        }
        private fun getActionText(unit: MapUnit): String? {
            val workerText by lazy { getWorkerActionText(unit) }
            return when {
                unit.action == null -> workerText
                unit.isFortified() -> UnitActionType.Fortify.value
                unit.isMoving() -> "Moving"
                unit.isAutomated() && workerText != null -> "[$workerText] ${Fonts.automate}"
                else -> unit.action
            }
        }
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab) =
            getActionText(item)?.toLabel()
        override fun getComparator() =
            compareBy<MapUnit, String>(collator) { getActionText(it).orEmpty() }
    },

    Strength {
        override fun getHeaderIcon(iconSize: Float) = Fonts.strength.toString().toLabel()
        override fun getEntryValue(item: MapUnit) = item.baseUnit.strength
    },

    RangedStrength {
        override val headerTip = "Ranged strength"
        override fun getHeaderIcon(iconSize: Float) = Fonts.rangedStrength.toString().toLabel()
        override fun getEntryValue(item: MapUnit) = item.baseUnit.rangedStrength
    },

    Movement {
        override fun getHeaderIcon(iconSize: Float) = Fonts.movement.toString().toLabel()
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab) =
            item.getMovementString().toLabel()
        override fun getComparator() = compareBy<MapUnit> { it.getMaxMovement() }
            .thenBy { it.currentMovement }
    },

    ClosestCity {
        override val headerTip = "Closest city"
        private fun getClosestCity(unit: MapUnit) =
            unit.getTile().getTilesInDistance(3).firstOrNull { it.isCityCenter() }
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor? {
            val cityTile = getClosestCity(item) ?: return null
            val cityColor = if (item.getTile() == cityTile) Color.FOREST.brighten(0.5f) else Color.WHITE
            return cityTile.getCity()!!.name
                .toLabel(cityColor, hideIcons = true)
                .onClick { showWorldScreenAt(cityTile) }
        }
        override fun getComparator() = compareBy<MapUnit, String>(collator) {
            getClosestCity(it)?.getCity()?.name?.tr(hideIcons = true).orEmpty()
        }
    },

    Promotions {
        override val defaultDescending = true
        override fun getEntryValue(item: MapUnit) = item.promotions.totalXpProduced()
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab) =
            UnitOverviewPromotionTable(item, actionContext)
    },

    Upgrade {
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor? {
            val unitAction = UnitActionsUpgrade.getUpgradeActionAnywhere(item)
                ?: return null
            val enable = unitAction.action != null && actionContext.viewingPlayer.isCurrentPlayer() &&
                GUI.isAllowedChangeState()
            val unitToUpgradeTo = (unitAction as UpgradeUnitAction).unitToUpgradeTo
            val selectKey = UnitOverviewTab.getUnitIdentifier(item, unitToUpgradeTo)
            val upgradeIcon = ImageGetter.getUnitIcon(unitToUpgradeTo.name,
                if (enable) Color.GREEN else Color.GREEN.darken(0.5f))
            if (enable) upgradeIcon.onClick {
                UnitUpgradeMenu(actionContext.stage, upgradeIcon, item, unitAction) {
                    actionContext.updateAndSelect(selectKey)
                }
            }
            return upgradeIcon
        }
    },

    Health {
        //override fun isVisible(gameInfo: GameInfo): Boolean = false
        override fun getEntryValue(item: MapUnit) = item.health
        override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor? =
            item.health.takeIf { it < 100 }?.toLabel()
    }
    ;
    //endregion

    //region Overridable fields
    override val headerTip get() = name
    override val align = Align.center
    override val fillX = false
    override val expandX = false
    override val equalizeHeight = false
    override val defaultDescending = false
    //endregion

    //region Overridable methods
    override fun getHeaderIcon(iconSize: Float) = headerTip.toLabel()
    override fun getEntryValue(item: MapUnit) = 0
    override fun getEntryActor(item: MapUnit, iconSize: Float, actionContext: UnitOverviewTab): Actor? =
        getEntryValue(item).takeIf { it > 0 }?.toLabel()
    override fun getTotalsActor(items: Iterable<MapUnit>): Actor? = null
    //endregion

    companion object {
        private val collator = UncivGame.Current.settings.getCollatorFromLocale()

        private fun showWorldScreenAt(position: Vector2, unit: MapUnit?) {
            GUI.resetToWorldScreen()
            GUI.getMap().setCenterPosition(position, forceSelectUnit = unit)
        }
        private fun showWorldScreenAt(unit: MapUnit) = showWorldScreenAt(unit.currentTile.position, unit)
        private fun showWorldScreenAt(tile: Tile) = showWorldScreenAt(tile.position, null)
    }
}

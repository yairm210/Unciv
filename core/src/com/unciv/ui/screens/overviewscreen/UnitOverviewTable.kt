package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.UnitGroup
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toPrettyString
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.UnitRenamePopup
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Supplies the Unit sub-table for the Empire Overview
 */
class UnitOverviewTab(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class UnitTabPersistableData(
        var scrollY: Float? = null
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = scrollY == null
    }
    override val persistableData = (persistedData as? UnitTabPersistableData) ?: UnitTabPersistableData()

    override fun activated(index: Int, caption: String, pager: TabbedPager) {
        if (persistableData.scrollY != null)
            pager.setPageScrollY(index, persistableData.scrollY!!)
        super.activated(index, caption, pager)
    }
    override fun deactivated(index: Int, caption: String, pager: TabbedPager) {
        persistableData.scrollY = pager.getPageScrollY(index)
        removeBlinkAction()
    }

    private val supplyTableWidth = (overviewScreen.stage.width * 0.25f).coerceAtLeast(240f)
    private val unitListTable = Table() // could be `this` instead, extra nesting helps readability a little
    private val unitHeaderTable = Table()
    private val fixedContent = Table()

    // used for select()
    private var blinkAction: Action? = null
    private var blinkActor: Actor? = null
    private fun removeBlinkAction() {
        if (blinkAction == null || blinkActor == null) return
        blinkActor!!.removeAction(blinkAction)
        blinkAction = null
        blinkActor = null
    }

    override fun getFixedContent() = fixedContent

    init {
        fixedContent.add(getUnitSupplyTable()).align(Align.top).padBottom(10f).row()
        fixedContent.add(unitHeaderTable.updateUnitHeaderTable())
        top()
        add(unitListTable.updateUnitListTable())
        equalizeColumns(unitListTable, unitHeaderTable)
    }

    // Here overloads are simpler than a generic:
    private fun Table.addLabeledValue (label: String, value: Int) {
        add(label.toLabel()).left()
        add(value.toLabel()).right().row()
    }
    private fun Table.addLabeledValue (label: String, value: String) {
        add(label.toLabel()).left()
        add(value.toLabel()).right().row()
    }

    private fun showWorldScreenAt(position: Vector2, unit: MapUnit?) {
        GUI.resetToWorldScreen()
        GUI.getMap().setCenterPosition(position, forceSelectUnit = unit)
    }
    private fun showWorldScreenAt(unit: MapUnit) = showWorldScreenAt(unit.currentTile.position, unit)
    private fun showWorldScreenAt(tile: Tile) = showWorldScreenAt(tile.position, null)

    private fun getUnitSupplyTable(): ExpanderTab {
        val stats = viewingPlayer.stats
        val deficit = stats.getUnitSupplyDeficit()
        val icon = if (deficit <= 0) null else Group().apply {
            isTransform = false
            setSize(36f, 36f)
            val image = ImageGetter.getImage("OtherIcons/ExclamationMark")
            image.color = Color.FIREBRICK
            image.setSize(36f, 36f)
            image.center(this)
            image.setOrigin(Align.center)
            addActor(image)
        }
        return ExpanderTab(
            title = "Unit Supply",
            fontSize = Constants.defaultFontSize,
            icon = icon,
            startsOutOpened = deficit > 0,
            defaultPad = 0f,
            expanderWidth = supplyTableWidth,
            onChange = {
                overviewScreen.resizePage(this)
            }
        ) {
            it.defaults().pad(5f).fill(false)
            it.background = BaseScreen.skinStrings.getUiBackground(
                "OverviewScreen/UnitOverviewTab/UnitSupplyTable",
                tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.6f)
            )
            it.addLabeledValue("Base Supply", stats.getBaseUnitSupply())
            it.addLabeledValue("Cities", stats.getUnitSupplyFromCities())
            it.addLabeledValue("Population", stats.getUnitSupplyFromPop())
            it.addSeparator()
            it.addLabeledValue("Total Supply", stats.getUnitSupply())
            it.addLabeledValue("In Use", viewingPlayer.units.getCivUnitsSize())
            it.addSeparator()
            it.addLabeledValue("Supply Deficit", deficit)
            it.addLabeledValue("Production Penalty", "${stats.getUnitSupplyProductionPenalty().toInt()}%")
            if (deficit > 0) {
                val penaltyLabel = "Increase your supply or reduce the amount of units to remove the production penalty"
                    .toLabel(Color.FIREBRICK)
                penaltyLabel.wrap = true
                it.add(penaltyLabel).colspan(2).left()
                    .width(supplyTableWidth).row()
            }
        }
    }

    private fun Table.updateUnitHeaderTable(): Table {
        defaults().pad(5f)
        add("Name".toLabel())
        add()
        add("Action".toLabel())
        add(Fonts.strength.toString().toLabel())
        add(Fonts.rangedStrength.toString().toLabel())
        add(Fonts.movement.toString().toLabel())
        add("Closest city".toLabel())
        add("Promotions".toLabel())
        add("Upgrade".toLabel())
        add("Health".toLabel())
        addSeparator().padBottom(0f)
        return this
    }

    private fun Table.updateUnitListTable(): Table {
        clear()
        val game = overviewScreen.game
        defaults().pad(5f)

        for (unit in viewingPlayer.units.getCivUnits().sortedWith(
            compareBy({ it.displayName() },
                { !it.due },
                { it.currentMovement <= Constants.minimumMovementEpsilon },
                { abs(it.currentTile.position.x) + abs(it.currentTile.position.y) })
        )) {
            val baseUnit = unit.baseUnit()

            // Unit button column - name, health, fortified, sleeping, embarked are visible here
            val button = IconTextButton(
                    unit.displayName(),
                    UnitGroup(unit, 20f),
                    fontColor = if (unit.due && unit.isIdle()) Color.WHITE else Color.LIGHT_GRAY
                )
            button.name = getUnitIdentifier(unit)  // Marker to find a unit in select()
            button.onClick {
                showWorldScreenAt(unit)
            }
            add(button).fillX()

            // Column: edit-name
            val editIcon = ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = Color.WHITE }.surroundWithCircle(30f, true, Color.valueOf("000c31"))
            editIcon.onClick {
                UnitRenamePopup(
                    screen = overviewScreen,
                    unit = unit,
                    actionOnClose = {
                        overviewScreen.game.replaceCurrentScreen(
                            EmpireOverviewScreen(viewingPlayer, selection = getUnitIdentifier(unit))
                        )
                    })
            }
            add(editIcon)

            // Column: action
            fun getActionLabel(unit: MapUnit) = when {
                unit.action == null -> ""
                unit.isFortified() -> UnitActionType.Fortify.value
                unit.isMoving() -> "Moving"
                else -> unit.action!!
            }
            if (unit.action == null) add() else add(getActionLabel(unit).toLabel())

            // Columns: strength, ranged
            if (baseUnit.strength > 0) add(baseUnit.strength.toLabel()) else add()
            if (baseUnit.rangedStrength > 0) add(baseUnit.rangedStrength.toLabel()) else add()
            add(unit.getMovementString().toLabel())

            // Closest city column
            val closestCity =
                unit.getTile().getTilesInDistance(3).firstOrNull { it.isCityCenter() }
            val cityColor = if (unit.getTile() == closestCity) Color.FOREST.brighten(0.5f) else Color.WHITE
            if (closestCity != null)
                add(closestCity.getCity()!!.name.toLabel(cityColor).apply {
                    onClick { showWorldScreenAt(closestCity) }
                })
            else add()

            // Promotions column
            val promotionsTable = Table()
            // getPromotions goes by json order on demand, so this is same sorting as on picker
            val promotions = unit.promotions.getPromotions(true)
            if (promotions.any()) {
                val numberOfLines = ceil(promotions.count() / 8f).toInt()
                val promotionsPerLine = promotions.count() / numberOfLines
                var promotionsThisLine = 0
                for (promotion in promotions) {
                    promotionsTable.add(ImageGetter.getPromotionPortrait(promotion.name))
                    promotionsThisLine++
                    if (promotionsThisLine == promotionsPerLine && numberOfLines>1) promotionsTable.row()
                }
            }

            if (unit.promotions.canBePromoted())
                promotionsTable.add(
                    ImageGetter.getImage("OtherIcons/Star").apply {
                        color = if (GUI.isAllowedChangeState() && unit.currentMovement > 0f && unit.attacksThisTurn == 0)
                                Color.GOLDENROD
                            else Color.GOLDENROD.darken(0.25f)
                    }
                ).size(24f).padLeft(8f)
            promotionsTable.onClick {
                if (unit.promotions.canBePromoted() || unit.promotions.promotions.isNotEmpty()) {
                    game.pushScreen(PromotionPickerScreen(unit))
                }
            }
            add(promotionsTable)

            // Upgrade column
            if (unit.upgrade.canUpgrade()) {
                val unitAction = UnitActionsUpgrade.getUpgradeAction(unit)
                val enable = unitAction?.action != null && viewingPlayer.isCurrentPlayer()
                val upgradeIcon = ImageGetter.getUnitIcon(unit.upgrade.getUnitToUpgradeTo().name,
                    if (enable) Color.GREEN else Color.GREEN.darken(0.5f))
                if (enable) upgradeIcon.onClick {
                    SoundPlayer.play(unitAction!!.uncivSound)
                    unitAction.action!!()
                    unitListTable.updateUnitListTable()
                }
                add(upgradeIcon).size(28f)
            } else add()

            // Numeric health column - there's already a health bar on the button, but...?
            if (unit.health < 100) add(unit.health.toLabel()) else add()
            row()
        }
        return this
    }

    companion object {
        fun getUnitIdentifier(unit: MapUnit) = unit.run { "$name@${getTile().position.toPrettyString()}" }
    }

    override fun select(selection: String): Float? {
        val cell = unitListTable.cells.asSequence()
                .filter { it.actor is IconTextButton && it.actor.name == selection }
                .firstOrNull() ?: return null
        val button = cell.actor as IconTextButton
        val scrollY = (0 until cell.row)
            .map { unitListTable.getRowHeight(it) }.sum() -
                (parent.height - unitListTable.getRowHeight(cell.row)) / 2

        removeBlinkAction()
        blinkAction = Actions.repeat(3, Actions.sequence(
            Actions.fadeOut(0.17f),
            Actions.fadeIn(0.17f)
        ))
        blinkActor = button
        button.addAction(blinkAction)
        return scrollY
    }
}

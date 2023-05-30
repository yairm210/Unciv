package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.ExpanderTab
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.KeyboardBinding
import com.unciv.ui.components.TabbedPager
import com.unciv.ui.components.UnitGroup
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toPrettyString
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.pickerscreens.PromotionPickerScreen
import com.unciv.ui.screens.pickerscreens.UnitRenamePopup
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade
import kotlin.math.abs

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
                    UnitGroup(unit, 20f).apply { if (!unit.isIdle()) color.a = 0.5f },
                    fontColor = if (unit.isIdle()) Color.WHITE else Color.LIGHT_GRAY
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
                val iconCount = promotions.count() + (if (unit.promotions.canBePromoted()) 1 else 0)
                val numberOfLines = (iconCount - 1) / 8 + 1
                val promotionsPerLine = (iconCount - 1) / numberOfLines + 1
                for (linePromotions in promotions.chunked(promotionsPerLine)) {
                    for (promotion in linePromotions) {
                        promotionsTable.add(ImageGetter.getPromotionPortrait(promotion.name))
                    }
                    if (linePromotions.size == promotionsPerLine) promotionsTable.row()
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
            val unitAction = UnitActionsUpgrade.getUpgradeActionAnywhere(unit)
            if (unitAction != null) {
                val enable = unitAction.action != null && viewingPlayer.isCurrentPlayer() &&
                    GUI.isAllowedChangeState()
                val unitToUpgradeTo = (unitAction as UpgradeUnitAction).unitToUpgradeTo
                val selectKey = getUnitIdentifier(unit, unitToUpgradeTo)
                val upgradeIcon = ImageGetter.getUnitIcon(unitToUpgradeTo.name,
                    if (enable) Color.GREEN else Color.GREEN.darken(0.5f))
                if (enable) upgradeIcon.onClick {
                    val pos = upgradeIcon.localToStageCoordinates(Vector2(upgradeIcon.width/2, upgradeIcon.height/2))
                    UnitUpgradeMenu(overviewScreen.stage, pos, unit, unitAction) {
                        unitListTable.updateUnitListTable()
                        select(selectKey)
                    }
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
        fun getUnitIdentifier(unit: MapUnit, unitToUpgradeTo: BaseUnit? = null): String {
            val name = unitToUpgradeTo?.name ?: unit.name
            return "$name@${unit.getTile().position.toPrettyString()}"
        }
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

    private class UnitUpgradeMenu(
        stage: Stage,
        position: Vector2,
        unit: MapUnit,
        private val unitAction: UpgradeUnitAction,
        val onButtonClicked: () -> Unit
    ) : Popup(stage, scrollable = false) {
        private val container: Container<Table>
        private val allUpgradableUnits: Sequence<MapUnit>
        private val animationDuration = 0.33f
        private val backgroundColor = (background as NinePatchDrawable).patch.color

        init {
            innerTable.remove()
            val newInnerTable = BaseUnitDescriptions.getUpgradeInfoTable(
                unitAction.title, unit.baseUnit, unitAction.unitToUpgradeTo
            )

            newInnerTable.row()
            val smallButtonStyle = SmallButtonStyle()
            val upgradeButton = "Upgrade".toTextButton(smallButtonStyle)
            upgradeButton.onActivation(::doUpgrade)
            upgradeButton.keyShortcuts.add(KeyboardBinding.Confirm)
            newInnerTable.add(upgradeButton).pad(2f, 15f).growX().row()

            allUpgradableUnits = unit.civ.units.getCivUnits()
                .filter {
                    it.baseUnit.name == unit.baseUnit.name
                        && it.currentMovement > 0f
                        && it.currentTile.getOwner() == unit.civ
                        && !it.isEmbarked()
                        && it.upgrade.canUpgrade(unitAction.unitToUpgradeTo, ignoreResources = true)
                }
            val allCount = allUpgradableUnits.count()

            if (allCount > 1) {
                // Note - all same-baseunit units cost the same to upgrade? What if a mod says e.g. 50% discount on Oasis?
                // - As far as I can see the rest of the upgrading code doesn't support such conditions at the moment.
                val allCost = unitAction.goldCostOfUpgrade * allCount
                val allResources = unitAction.newResourceRequirements * allCount
                val upgradeAllButton =
                    "Upgrade all [$allCount] [${unit.name}] ([$allCost] gold)".toTextButton(
                        smallButtonStyle
                    )
                upgradeAllButton.isDisabled = unit.civ.gold < allCost ||
                    allResources.isNotEmpty() &&
                    unit.civ.getCivResourcesByName().run {
                        allResources.any {
                            it.value > (this[it.key] ?: 0)
                        }
                    }
                upgradeAllButton.onActivation(::doAllUpgrade)
                newInnerTable.add(upgradeAllButton).pad(2f, 15f).growX().row()
            }

            clickBehindToClose = true
            keyShortcuts.add(KeyCharAndCode.BACK) { close() }

            newInnerTable.pack()
            container = Container(newInnerTable)
            container.touchable = Touchable.childrenOnly
            container.isTransform = true
            container.setScale(0.05f)
            container.color.a = 0f

            open(true)  // this only does the screen-covering "click-behind" portion

            container.setPosition(
                position.x.coerceAtMost(stage.width - newInnerTable.width / 2),
                position.y.coerceAtLeast(newInnerTable.height / 2)
            )
            addActor(container)

            container.addAction(Actions.parallel(
                Actions.scaleTo(1f, 1f, animationDuration, Interpolation.fade),
                Actions.fadeIn(animationDuration, Interpolation.fade)
            ))

            backgroundColor.set(0)
            addAction(Actions.alpha(0.35f, animationDuration, Interpolation.fade).apply {
                color = backgroundColor
            })
        }

        private fun doUpgrade() {
            SoundPlayer.play(unitAction.uncivSound)
            unitAction.action!!()
            onButtonClicked()
            close()
        }

        private fun doAllUpgrade() {
            stage.addAction(Actions.sequence(
                Actions.run { SoundPlayer.play(unitAction.uncivSound) },
                Actions.delay(0.2f),
                Actions.run { SoundPlayer.play(unitAction.uncivSound) }
            ))
            for (unit in allUpgradableUnits) {
                val otherAction = UnitActionsUpgrade.getUpgradeAction(unit)
                otherAction?.action?.invoke()
            }
            onButtonClicked()
            close()
        }

        override fun close() {
            addAction(Actions.alpha(0f, animationDuration, Interpolation.fade).apply {
                color = backgroundColor
            })
            container.addAction(Actions.sequence(
                Actions.parallel(
                    Actions.scaleTo(0.05f, 0.05f, animationDuration, Interpolation.fade),
                    Actions.fadeOut(animationDuration, Interpolation.fade)
                ),
                Actions.run {
                    container.remove()
                    super.close()
                }
            ))
        }

        class SmallButtonStyle : TextButton.TextButtonStyle(BaseScreen.skin[TextButton.TextButtonStyle::class.java]) {
            init {
                val upColor = BaseScreen.skin.getColor("color")
                val downColor = BaseScreen.skin.getColor("pressed")
                val overColor = BaseScreen.skin.getColor("highlight")
                val disabledColor = BaseScreen.skin.getColor("disabled")
                val shapeName = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape
                up = BaseScreen.skinStrings.getUiBackground("", shapeName, upColor)
                down = BaseScreen.skinStrings.getUiBackground("", shapeName, downColor)
                over = BaseScreen.skinStrings.getUiBackground("", shapeName, overColor)
                disabled = BaseScreen.skinStrings.getUiBackground("", shapeName, disabledColor)
                disabledFontColor = Color.GRAY
            }
        }
    }
}

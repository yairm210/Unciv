package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UpgradeUnitAction
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade

/**
 *  A popup menu showing info about an Unit upgrade, with buttons to upgrade "this" unit or _all_
 *  similar units.
 *
 *  @param stage The stage this will be shown on, passed to Popup and used for clamping **`position`**
 *  @param position stage coordinates to show this centered over - clamped so that nothing is clipped outside the [stage]
 *  @param unit Who is ready to upgrade?
 *  @param unitAction Holds pre-calculated info like unitToUpgradeTo, cost or resource requirements. Its action is mapped to the Upgrade button.
 *  @param callbackAfterAnimation If true the following will be delayed until the Popup is actually closed (Stage.hasOpenPopups returns false).
 *  @param onButtonClicked A callback after one or several upgrades have been performed (and the menu is about to close)
 */
/*
    Note - callbackAfterAnimation has marginal value: When this is called from UnitOverview, where the
    callback updates the upgrade symbol column, that can happen before/while the animation plays.
    Called from the WorldScreen, to set shouldUpdate, that **needs** to fire late, or else the update is wasted.
    Therefore, simplifying to always use afterCloseCallback would only be visible to the quick keen eye.
 */
class UnitUpgradeMenu(
    stage: Stage,
    positionNextTo: Actor,
    private val unit: MapUnit,
    private val unitAction: UpgradeUnitAction,
    private val callbackAfterAnimation: Boolean = false,
    private val onButtonClicked: () -> Unit
) : AnimatedMenuPopup(stage, getActorTopRight(positionNextTo)) {

    private val unitToUpgradeTo by lazy { unitAction.unitToUpgradeTo }

    private val allUpgradableUnits: Sequence<MapUnit> by lazy {
        unit.civ.units.getCivUnits()
            .filter {
                it.baseUnit.name == unit.baseUnit.name
                    && it.currentMovement > 0f
                    && it.currentTile.getOwner() == unit.civ
                    && !it.isEmbarked()
                    && it.upgrade.canUpgrade(unitToUpgradeTo, ignoreResources = true)
            }
    }

    init {
        val action = {
            if (anyButtonWasClicked) onButtonClicked()
        }
        if (callbackAfterAnimation) afterCloseCallback = action
        else closeListeners.add(action)
    }

    override fun createContentTable(): Table {
        val newInnerTable = BaseUnitDescriptions.getUpgradeInfoTable(
            unitAction.title, unit.baseUnit, unitToUpgradeTo
        )
        newInnerTable.row()
        newInnerTable.add(getButton("Upgrade", KeyboardBinding.Upgrade, ::doUpgrade))
            .pad(15f, 15f, 5f, 15f).growX().row()

        val allCount = allUpgradableUnits.count()
        if (allCount <= 1) return newInnerTable

        // Note - all same-baseunit units cost the same to upgrade? What if a mod says e.g. 50% discount on Oasis?
        // - As far as I can see the rest of the upgrading code doesn't support such conditions at the moment.
        val allCost = unitAction.goldCostOfUpgrade * allCount
        val allResources = unitAction.newResourceRequirements * allCount
        val upgradeAllText = "Upgrade all [$allCount] [${unit.name}] ([$allCost] gold)"
        val upgradeAllButton = getButton(upgradeAllText, KeyboardBinding.UpgradeAll, ::doAllUpgrade)
        upgradeAllButton.isDisabled = unit.civ.gold < allCost ||
            allResources.isNotEmpty() &&
            unit.civ.getCivResourcesByName().run {
                allResources.any {
                    it.value > (this[it.key] ?: 0)
                }
            }
        newInnerTable.add(upgradeAllButton).pad(2f, 15f).growX().row()
        return newInnerTable
    }

    private fun doUpgrade() {
        SoundPlayer.play(unitAction.uncivSound)
        unitAction.action!!()
    }

    private fun doAllUpgrade() {
        SoundPlayer.playRepeated(unitAction.uncivSound)
        for (unit in allUpgradableUnits) {
            val otherAction = UnitActionsUpgrade.getUpgradeActions(unit)
                .firstOrNull{ (it as UpgradeUnitAction).unitToUpgradeTo == unitToUpgradeTo && 
                    it.action != null }
            otherAction?.action?.invoke()
        }
    }
}

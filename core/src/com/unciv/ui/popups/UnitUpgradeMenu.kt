package com.unciv.ui.popups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.Counter
import com.unciv.models.UpgradeUnitAction
import com.unciv.models.translations.tr
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade

/**
 *  A popup menu showing info about an Unit upgrade, with buttons to upgrade "this" unit or _all_
 *  similar units.
 *
 *  @param stage The stage this will be shown on, passed to Popup and used for clamping **`position`**
 *  @param positionNextTo stage coordinates to show this centered over - clamped so that nothing is clipped outside the [stage]
 *  @param unit Who is ready to upgrade?
 *  @param unitAction Holds pre-calculated info like unitToUpgradeTo, cost or resource requirements. Its action is mapped to the Upgrade button.
 *  @param enable Whether the buttons should be enabled - allows use to display benefits when you can't actually afford them.
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
    private val enable: Boolean,
    private val callbackAfterAnimation: Boolean = false,
    private val onButtonClicked: () -> Unit
) : ScrollableAnimatedMenuPopup(stage, getActorTopRight(positionNextTo)) {

    private val unitToUpgradeTo by lazy { unitAction.unitToUpgradeTo }

    private val allUpgradableUnits: Sequence<MapUnit> by lazy {
        unit.civ.units.getCivUnits()
            .filter {
                it.baseUnit.name == unit.baseUnit.name
                    && it.hasMovement()
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

    override fun createScrollableContent() =
        BaseUnitDescriptions.getUpgradeInfoTable(unitAction.title, unit.baseUnit, unitToUpgradeTo)

    override fun createFixedContent() = Table().apply {
        val singleButton = getButton("Upgrade", KeyboardBinding.Upgrade, ::doUpgrade)
        // Using Gdx standard here, not our extension `isEnabled`: These have full styling
        singleButton.isDisabled = !enable
        add(singleButton).growX().row()

        val allCount = allUpgradableUnits.count()
        if (allCount <= 1) return@apply

        // Note - all same-baseunit units cost the same to upgrade? What if a mod says e.g. 50% discount on Oasis?
        // - As far as I can see the rest of the upgrading code doesn't support such conditions at the moment.
        val allCost = unitAction.goldCostOfUpgrade * allCount
        val allResources = unitAction.newResourceRequirements * allCount
        val upgradeAllText = "Upgrade all [$allCount] [${unit.name}] ([$allCost] gold)"
        val upgradeAllButton = getButton(upgradeAllText, KeyboardBinding.UpgradeAll, ::doAllUpgrade)
        val insufficientGold = unit.civ.gold < allCost
        val insufficientResources = getInsufficientResourcesMessage(allResources, unit.civ)
        upgradeAllButton.isDisabled = insufficientGold || insufficientResources.isNotEmpty()
        add(upgradeAllButton).padTop(7f).growX().row()
        if (insufficientResources.isEmpty()) return@apply
        val label = ColorMarkupLabel(insufficientResources, Color.SCARLET)
        add(label).center()
    }

    private fun getInsufficientResourcesMessage(requiredResources: Counter<String>, civ: Civilization): String {
        if (requiredResources.isEmpty()) return ""
        val available = civ.getCivResourcesByName()
        val sb = StringBuilder()
        for ((name, amount) in requiredResources) {
            val difference = amount - (available[name] ?: 0)
            if (difference <= 0) continue
            if (sb.isEmpty()) sb.append('\n')
            sb.append("Need [$difference] more [$name]".tr())
        }
        return sb.toString()
    }

    private fun doUpgrade() {
        SoundPlayer.play(unitAction.uncivSound)
        unitAction.action!!()
    }

    private fun doAllUpgrade() {
        SoundPlayer.playRepeated(unitAction.uncivSound, allUpgradableUnits.count())
        for (unit in allUpgradableUnits) {
            val otherAction = UnitActionsUpgrade.getUpgradeActions(unit)
                .firstOrNull{ (it as UpgradeUnitAction).unitToUpgradeTo == unitToUpgradeTo &&
                    it.action != null }
            otherAction?.action?.invoke()
        }
    }
}

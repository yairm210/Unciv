package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UpgradeUnitAction
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade

//TODO When this gets reused. e.g. from UnitActionsTable, move to another package.

/**
 *  A popup menu showing info about an Unit upgrade, with buttons to upgrade "this" unit or _all_
 *  similar units.
 *
 *  Meant to animate "in" at a given position - unlike other [Popup]s which are always stage-centered.
 *  No close button - use "click-behind".
 *  The "click-behind" semi-transparent covering of the rest of the stage is much darker than a normal
 *  Popup (geve the impression to take away illumination and spotlight the menu) and fades in together
 *  with the UnitUpgradeMenu itself. Closing the menu in any of the four ways will fade out everything
 *  inverting the fade-and-scale-in.
 *
 *  @param stage The stage this will be shown on, passed to Popup and used for clamping **`position`**
 *  @param position stage coortinates to show this centered over - clamped so that nothing is clipped outside the [stage]
 *  @param unit Who is ready to upgrade?
 *  @param unitAction Holds pre-calculated info like unitToUpgradeTo, cost or resource requirements. Its action is mapped to the Upgrade button.
 *  @param onButtonClicked A callback after one or several upgrades have been performed (and the menu is about to close)
 */
class UnitUpgradeMenu(
    stage: Stage,
    position: Vector2,
    private val unit: MapUnit,
    private val unitAction: UpgradeUnitAction,
    private val onButtonClicked: () -> Unit
) : Popup(stage, Scrollability.None) {
    private val container: Container<Table>
    private val allUpgradableUnits: Sequence<MapUnit>
    private val animationDuration = 0.33f
    private val backgroundColor = (background as NinePatchDrawable).patch.color

    init {
        innerTable.remove()

        // Note: getUpgradeInfoTable skins this as General/Tooltip, roundedEdgeRectangle, DARK_GRAY
        // TODO - own skinnable path, possibly when tooltip use of getUpgradeInfoTable gets replaced
        val newInnerTable = BaseUnitDescriptions.getUpgradeInfoTable(
            unitAction.title, unit.baseUnit, unitAction.unitToUpgradeTo
        )

        newInnerTable.row()
        val smallButtonStyle = SmallButtonStyle()
        val upgradeButton = "Upgrade".toTextButton(smallButtonStyle)
        upgradeButton.onActivation(::doUpgrade)
        upgradeButton.keyShortcuts.add(KeyboardBinding.Confirm)
        newInnerTable.add(upgradeButton).pad(15f, 15f, 5f, 15f).growX().row()

        allUpgradableUnits = unit.civ.units.getCivUnits()
            .filter {
                it.baseUnit.name == unit.baseUnit.name
                    && it.currentMovement > 0f
                    && it.currentTile.getOwner() == unit.civ
                    && !it.isEmbarked()
                    && it.upgrade.canUpgrade(unitAction.unitToUpgradeTo, ignoreResources = true)
            }
        newInnerTable.tryAddUpgradeAllUnitsButton(smallButtonStyle)

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

        container.addAction(
            Actions.parallel(
            Actions.scaleTo(1f, 1f, animationDuration, Interpolation.fade),
            Actions.fadeIn(animationDuration, Interpolation.fade)
        ))

        backgroundColor.set(0)
        addAction(Actions.alpha(0.35f, animationDuration, Interpolation.fade).apply {
            color = backgroundColor
        })
    }

    private fun Table.tryAddUpgradeAllUnitsButton(buttonStyle: TextButton.TextButtonStyle) {
        val allCount = allUpgradableUnits.count()
        if (allCount <= 1) return
        // Note - all same-baseunit units cost the same to upgrade? What if a mod says e.g. 50% discount on Oasis?
        // - As far as I can see the rest of the upgrading code doesn't support such conditions at the moment.
        val allCost = unitAction.goldCostOfUpgrade * allCount
        val allResources = unitAction.newResourceRequirements * allCount
        val upgradeAllButton = "Upgrade all [$allCount] [${unit.name}] ([$allCost] gold)"
            .toTextButton(buttonStyle)
        upgradeAllButton.isDisabled = unit.civ.gold < allCost ||
            allResources.isNotEmpty() &&
            unit.civ.getCivResourcesByName().run {
                allResources.any {
                    it.value > (this[it.key] ?: 0)
                }
            }
        upgradeAllButton.onActivation(::doAllUpgrade)
        add(upgradeAllButton).pad(2f, 15f).growX().row()
    }

    private fun doUpgrade() {
        SoundPlayer.play(unitAction.uncivSound)
        unitAction.action!!()
        onButtonClicked()
        close()
    }

    private fun doAllUpgrade() {
        stage.addAction(
            Actions.sequence(
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
        container.addAction(
            Actions.sequence(
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
        /** Modify NinePatch geometry so the roundedEdgeRectangleMidShape button is 38f high instead of 48f,
         *  Otherwise this excercise would be futile - normal roundedEdgeRectangleShape based buttons are 50f high.
         */
        private fun NinePatchDrawable.reduce(): NinePatchDrawable {
            val patch = NinePatch(this.patch)
            patch.padTop = 10f
            patch.padBottom = 10f
            patch.topHeight = 10f
            patch.bottomHeight = 10f
            return NinePatchDrawable(this).also { it.patch = patch }
        }

        init {
            val upColor = BaseScreen.skin.getColor("color")
            val downColor = BaseScreen.skin.getColor("pressed")
            val overColor = BaseScreen.skin.getColor("highlight")
            val disabledColor = BaseScreen.skin.getColor("disabled")
            // UiElementDocsWriter inspects source, which is why this isn't prettified better
            val shape = BaseScreen.run {
                // Let's use _one_ skinnable background lookup but with different tints
                val skinned = skinStrings.getUiBackground("UnitUpgradeMenu/Button", skinStrings.roundedEdgeRectangleMidShape)
                // Reduce height only if not skinned
                val default = ImageGetter.getNinePatch(skinStrings.roundedEdgeRectangleMidShape)
                if (skinned === default) default.reduce() else skinned
            }
            // Now get the tinted variants
            up = shape.tint(upColor)
            down = shape.tint(downColor)
            over = shape.tint(overColor)
            disabled = shape.tint(disabledColor)
            disabledFontColor = Color.GRAY
        }
    }
}

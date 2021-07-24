package com.unciv.ui.worldscreen.unit

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.map.MapUnit
import com.unciv.models.UnitAction
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.utils.*
import com.unciv.ui.utils.KeyPressDispatcher.Companion.keyboardAvailable
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.worldscreen.WorldScreen
import kotlin.concurrent.thread

private data class UnitIconAndKey(val icon: Actor, var key: KeyCharAndCode = KeyCharAndCode.UNKNOWN) {
    constructor(icon: Actor, key: Char) : this(icon, KeyCharAndCode(key))
}

class UnitActionsTable(val worldScreen: WorldScreen) : Table() {


    private fun getIconAndKeyForUnitAction(unitAction: String): UnitIconAndKey {
        when {
            unitAction.equalsPlaceholderText("Upgrade to [] ([] gold)") -> {
                // Regexplaination: start with a [, take as many non-] chars as you can, until you reach a ].
                // What you find between the first [ and the first ] that comes after it, will be group no. 0
                val unitToUpgradeTo = unitAction.getPlaceholderParameters()[0]
                return UnitIconAndKey(ImageGetter.getUnitIcon(unitToUpgradeTo), 'u')
            }
            unitAction.equalsPlaceholderText("Create []") -> {
                // Regexplaination: start with a [, take as many non-] chars as you can, until you reach a ].
                // What you find between the first [ and the first ] that comes after it, will be group no. 0
                val improvementName = unitAction.getPlaceholderParameters()[0]
                return UnitIconAndKey(ImageGetter.getImprovementIcon(improvementName), 'i')
            }
            unitAction.equalsPlaceholderText("Spread []") -> {
                // This should later include icons for the different religions. For now, just use the great prophet icon
                return UnitIconAndKey(ImageGetter.getUnitIcon("Great Prophet"), 'g')
            }
            else -> when (unitAction) {
                "Sleep" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Sleep"), 'f')
                "Sleep until healed" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Sleep"), 'h')
                "Fortify" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Shield").apply { color = Color.BLACK }, 'f')
                "Fortify until healed" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Shield").apply { color = Color.BLACK }, 'h')
                // Move unit is not actually used anywhere
                "Move unit" -> return UnitIconAndKey(ImageGetter.getStatIcon("Movement"))
                "Stop movement" -> return UnitIconAndKey(ImageGetter.getStatIcon("Movement").apply { color = Color.RED }, KeyCharAndCode(Input.Keys.END))
                "Swap units" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Swap"), 'y')
                "Promote" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Star").apply { color = Color.GOLD }, 'o')
                "Construct improvement" -> return UnitIconAndKey(ImageGetter.getUnitIcon(Constants.worker), 'i')
                "Automate" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Great Engineer"), 'm')
                "Stop automation" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Stop"), KeyCharAndCode(Input.Keys.END))
                "Found city" -> return UnitIconAndKey(ImageGetter.getUnitIcon(Constants.settler), 'c')
                "Hurry Research" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Great Scientist"), 'g')
                "Start Golden Age" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Great Artist"), 'g')
                "Hurry Wonder" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Great Engineer"), 'g')
                "Conduct Trade Mission" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Great Merchant"), 'g')
                "Construct road" -> return UnitIconAndKey(ImageGetter.getImprovementIcon("Road"), 'r')
                "Paradrop" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Paratrooper"), 'p')
                "Set up" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Catapult"), 't')
                "Explore" -> return UnitIconAndKey(ImageGetter.getUnitIcon("Scout"), 'x')
                "Stop exploration" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Stop"), 'x')
                "Pillage" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Pillage"), 'p')
                "Disband unit" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/DisbandUnit"), KeyCharAndCode.DEL)
                "Gift unit" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Present"))
                "Show more" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/ArrowRight"), KeyCharAndCode(Input.Keys.PAGE_DOWN))
                "Back" -> return UnitIconAndKey(ImageGetter.getImage("OtherIcons/ArrowLeft"), KeyCharAndCode(Input.Keys.PAGE_UP))
                else -> {
                    // If the unit has been fortifying for some turns
                    if (unitAction.startsWith("Fortification")) return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Shield"))
                    return UnitIconAndKey(ImageGetter.getImage("OtherIcons/Star"))
                }
            }
        }
    }

    fun update(unit: MapUnit?) {
        clear()
        if (unit == null) return
        if (!worldScreen.canChangeState) return // No actions when it's not your turn or spectator!
        for (button in UnitActions.getUnitActions(unit, worldScreen).map { getUnitActionButton(it) })
            add(button).left().padBottom(2f).row()
        pack()
    }


    private fun getUnitActionButton(unitAction: UnitAction): Button {
        val iconAndKey = getIconAndKeyForUnitAction(unitAction.title)

        // If peripheral keyboard not detected, hotkeys will not be displayed
        if (!keyboardAvailable) { iconAndKey.key = KeyCharAndCode.UNKNOWN }

        val actionButton = Button(CameraStageBaseScreen.skin)
        actionButton.add(iconAndKey.icon).size(20f).pad(5f)
        val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        actionButton.add(unitAction.title.toLabel(fontColor)).pad(5f)
        actionButton.addTooltip(iconAndKey.key)
        actionButton.pack()
        val action = {
            unitAction.action?.invoke()
            UncivGame.Current.worldScreen.shouldUpdate = true
        }
        if (unitAction.action == null) actionButton.disable()
        else {
            actionButton.onClick(unitAction.uncivSound, action)
            if (iconAndKey.key != KeyCharAndCode.UNKNOWN)
                worldScreen.keyPressDispatcher[iconAndKey.key] = {
                    thread(name = "Sound") { Sounds.play(unitAction.uncivSound) }
                    action()
                }
        }

        return actionButton
    }
}

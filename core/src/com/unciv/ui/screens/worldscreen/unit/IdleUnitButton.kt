package com.unciv.ui.screens.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation

class IdleUnitButton (
    internal val unitTable: UnitTable,
    private val tileMapHolder: WorldMapHolder,
    val previous: Boolean,
    private val keyShortcutBind: KeyboardBinding
) : Table() {

    val image = ImageGetter.getImage("OtherIcons/BackArrow")

    init {
        val imageSize = 25f
        if(!previous) {
            image.setSize(imageSize, imageSize)
            image.setOrigin(Align.center)
            image.rotateBy(180f)
        }
        add(image).size(imageSize).pad(10f,20f)
        enable()
        keyShortcuts.add(keyShortcutBind)
        addTooltip(keyShortcutBind)
        onActivation {

            val idleUnits = unitTable.worldScreen.viewingCiv.units.getIdleUnits()
            if (idleUnits.none()) return@onActivation

            val unitToSelect: MapUnit
            if (unitTable.selectedUnit == null || !idleUnits.contains(unitTable.selectedUnit!!))
                unitToSelect = idleUnits.first()
            else {
                var index = idleUnits.indexOf(unitTable.selectedUnit!!)
                if (previous) index-- else index++
                index += idleUnits.count()
                index %= idleUnits.count() // for looping
                unitToSelect = idleUnits.elementAt(index)
            }

            tileMapHolder.setCenterPosition(unitToSelect.currentTile.position)
            unitTable.selectUnit(unitToSelect)
            unitTable.worldScreen.shouldUpdate = true
        }
    }

    fun enable() {
        image.color= Color.WHITE
        touchable=Touchable.enabled
    }

    fun disable() {
        image.color= Color.GRAY
        touchable=Touchable.disabled
    }
}

package com.unciv.ui.screens.worldscreen.unit

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder
import com.unciv.view.MapUnitView

class IdleUnitButton (
    private val unitTable: UnitTable,
    private val tileMapHolder: WorldMapHolder,
    val previous: Boolean,
    keyShortcutBind: KeyboardBinding
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
        onActivation (binding = keyShortcutBind) {

            val idleUnits = unitTable.worldScreen.selectedGameView.civView.getUnits().filter { it.isIdle() }
            if (idleUnits.isEmpty()) return@onActivation

            val selectedUnit = unitTable.selectedUnit
            val unitToSelect: MapUnitView
            if (selectedUnit == null || !idleUnits.contains(selectedUnit))
                unitToSelect = idleUnits.first()
            else {
                var index = idleUnits.indexOf(selectedUnit)
                if (previous) index-- else index++
                index += idleUnits.size
                index %= idleUnits.size // for looping
                unitToSelect = idleUnits[index]
            }

            tileMapHolder.setCenterPosition(unitToSelect.getTile().position())
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

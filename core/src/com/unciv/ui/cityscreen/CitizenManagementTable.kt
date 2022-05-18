package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.city.CityFocus
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.*

class CitizenManagementTable(val cityScreen: CityScreen) : Table() {
    private val innerTable = Table()
    val city = cityScreen.city

    init {
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().darken(0.5f))
        add(innerTable).pad(2f).fill()
        background = ImageGetter.getBackground(Color.WHITE)
    }

    fun update(visible: Boolean = false) {
        innerTable.clear()

        if (!visible) {
            isVisible = false
            return
        }
        isVisible = true

        val colorSelected = BaseScreen.skin.get("selection", Color::class.java)
        val colorButton = BaseScreen.skin.get("color", Color::class.java)
        // effectively a button, but didn't want to rewrite TextButton style
        // and much more compact and can control backgrounds easily based on settings
        val avoidLabel = "Avoid Growth".toLabel()
        val avoidCell = Table()
        avoidCell.touchable = Touchable.enabled
        avoidCell.add(avoidLabel).pad(5f)
        avoidCell.onClick { city.avoidGrowth = !city.avoidGrowth; city.reassignPopulation(); cityScreen.update() }

        avoidCell.background = ImageGetter.getBackground(if (city.avoidGrowth) colorSelected else colorButton)
        innerTable.add(avoidCell).colspan(2).growX().pad(3f)
        innerTable.row()

        for (focus in CityFocus.values()) {
            if (!focus.tableEnabled) continue
            if (focus == CityFocus.FaithFocus && !city.civInfo.gameInfo.isReligionEnabled()) continue
            val label = focus.label.toLabel()
            val cell = Table()
            cell.touchable = Touchable.enabled
            cell.add(label).pad(5f)
            cell.onClick { city.cityAIFocus = focus; city.reassignPopulation(); cityScreen.update() }

            cell.background = ImageGetter.getBackground(if (city.cityAIFocus == focus) colorSelected else colorButton)
            innerTable.add(cell).growX().pad(3f)
            if (focus.stat != null)
                innerTable.add(ImageGetter.getStatIcon(focus.stat.name)).size(20f).padRight(5f)
            innerTable.row()
        }

        pack()
    }

}

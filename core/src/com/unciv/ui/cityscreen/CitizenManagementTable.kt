package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.city.CityFocus
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.*

class CitizenManagementTable(val cityScreen: CityScreen): Table() {
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

        val focusLabel = "Default Focus".toLabel().addBorder(5f, Color.CLEAR)
        val defaultCell = Table()
        defaultCell.add(focusLabel)
        defaultCell.touchable = Touchable.enabled
        defaultCell.onClick { city.cityAIFocus = CityFocus.NoFocus; city.reassignPopulation(); cityScreen.update() }
        if (city.cityAIFocus == CityFocus.NoFocus)
            defaultCell.background = ImageGetter.getBackground(BaseScreen.skin.get("selection", Color::class.java))
        else
            defaultCell.background = ImageGetter.getBackground(BaseScreen.skin.get("color", Color::class.java))
        innerTable.add(defaultCell).growX().pad(3f).row()
        val colorSelected = BaseScreen.skin.get("selection", Color::class.java)
        val colorButton = BaseScreen.skin.get("color", Color::class.java)
        for (stat in CityFocus.values()) {
            if (stat.stat == null) continue
            if (stat == CityFocus.FaithFocus && !city.civInfo.gameInfo.isReligionEnabled()) continue
            val label = "${stat.stat.name} Focus".toLabel().addBorder(5f, Color.CLEAR)
            val cell = Table()
            cell.touchable = Touchable.enabled
            cell.add(label)
            cell.onClick { city.cityAIFocus = stat; city.reassignPopulation(); cityScreen.update() }

            if (city.cityAIFocus == stat)
                cell.background = ImageGetter.getBackground(colorSelected)
            else
                cell.background = ImageGetter.getBackground(colorButton)
            innerTable.add(cell).growX().pad(3f)
            innerTable.add(ImageGetter.getStatIcon(stat.stat.name)).size(20f).padRight(5f)
            innerTable.row()
        }

        pack()
    }

}

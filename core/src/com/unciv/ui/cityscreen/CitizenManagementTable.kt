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

        val focusLabel = "Default Focus".tr().toLabel().addBorder(5f, Color.CLEAR)
        val defaultCell = Table()
        defaultCell.add(focusLabel)
        defaultCell.touchable = Touchable.enabled
        defaultCell.onClick { city.cityAIFocus = CityFocus.NoFocus; city.reassignPopulation(); cityScreen.update() }
        if (city.cityAIFocus == CityFocus.NoFocus)
            defaultCell.background = ImageGetter.getBackground(Color.FIREBRICK)
        else
            defaultCell.background = ImageGetter.getBackground(BaseScreen.skin.get("color", Color::class.java))
        innerTable.add(defaultCell).growX().pad(3f).row()
        for ((stat, _) in city.cityStats.currentCityStats) {
            if (stat == Stat.Happiness) continue
            if (stat == Stat.Faith && !city.civInfo.gameInfo.isReligionEnabled()) continue
            val label = "${stat.name} Focus".tr().toLabel().addBorder(5f, Color.CLEAR)
            val cell = Table()
            cell.touchable = Touchable.enabled
            cell.add(label)
            if (stat == Stat.Production)
                cell.onClick { city.cityAIFocus = CityFocus.ProductionFocus; city.reassignPopulation(); cityScreen.update() }
            if (stat == Stat.Food)
                cell.onClick { city.cityAIFocus = CityFocus.FoodFocus; city.reassignPopulation(); cityScreen.update() }
            if (stat == Stat.Gold)
                cell.onClick { city.cityAIFocus = CityFocus.GoldFocus; city.reassignPopulation(); cityScreen.update()}
            if (stat == Stat.Science)
                cell.onClick { city.cityAIFocus = CityFocus.ScienceFocus; city.reassignPopulation(); cityScreen.update() }
            if (stat == Stat.Culture)
                cell.onClick { city.cityAIFocus = CityFocus.CultureFocus; city.reassignPopulation(); cityScreen.update() }
            if (stat == Stat.Faith)
                cell.onClick { city.cityAIFocus = CityFocus.FaithFocus; city.reassignPopulation(); cityScreen.update() }
            
            if (city.isFocus(stat))
                cell.background = ImageGetter.getBackground(BaseScreen.skin.get("selection", Color::class.java))
            else
                cell.background = ImageGetter.getBackground(BaseScreen.skin.get("color", Color::class.java))
            innerTable.add(cell).growX().pad(3f)
            innerTable.add(ImageGetter.getStatIcon(stat.name)).size(20f).padRight(5f)
            innerTable.row()
        }

        pack()
    }

}

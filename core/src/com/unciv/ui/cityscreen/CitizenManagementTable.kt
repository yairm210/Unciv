package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.city.CityFocus
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

        val focusLabel = "Default Focus".tr().toLabel()
        if (city.cityAIFocus == CityFocus.NoFocus)
            focusLabel.addBorder(5f, Color.FIREBRICK)
        else
            focusLabel.addBorder(5f, BaseScreen.skin.get("color", Color::class.java) )
        innerTable.add(focusLabel).row()
        for ((stat, amount) in city.cityStats.currentCityStats) {
            if (stat.name == "Happiness") continue
            val focusLabel = "${stat.name} Focus".tr().toLabel()
            if (stat.name == "Production" && city.cityAIFocus == CityFocus.ProductionFocus ||
                    stat.name == "Food" && city.cityAIFocus == CityFocus.ProductionFocus ||
                    stat.name == "Gold" && city.cityAIFocus == CityFocus.GoldFocus ||
                    stat.name == "Science" && city.cityAIFocus == CityFocus.ScienceFocus ||
                    stat.name == "Culture" && city.cityAIFocus == CityFocus.CultureFocus)
                focusLabel.addBorder(5f, Color.FIREBRICK)
            else
                focusLabel.addBorder(5f, BaseScreen.skin.get("color", Color::class.java))
            innerTable.add(focusLabel)
            innerTable.add(ImageGetter.getStatIcon(stat.name)).size(20f).padRight(5f)
            innerTable.row()
        }

        pack()
    }

}

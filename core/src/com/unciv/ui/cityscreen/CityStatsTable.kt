package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.logic.city.CityInfo
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.colorFromRGB
import com.unciv.ui.utils.toLabel
import kotlin.math.ceil
import kotlin.math.round

class CityStatsTable(val cityInfo: CityInfo): Table() {
    private val innerTable = Table()

    init {
        pad(2f)
        background = ImageGetter.getBackground(colorFromRGB(194,180,131))

        innerTable.pad(5f)
        innerTable.defaults().pad(5f)
        innerTable.background = ImageGetter.getBackground(Color.BLACK.cpy().apply { a=0.8f })

        add(innerTable).fill()
    }

    fun update() {
        innerTable.clear()

        val unassignedPopString = "{Unassigned population}:".tr()+
                " "+cityInfo.population.getFreePopulation().toString() + "/" + cityInfo.population.population

        var turnsToExpansionString =
                if (cityInfo.cityStats.currentCityStats.culture > 0) {
                    val remainingCulture = cityInfo.expansion.getCultureToNextTile() - cityInfo.expansion.cultureStored
                    var turnsToExpansion = ceil(remainingCulture / cityInfo.cityStats.currentCityStats.culture).toInt()
                    if (turnsToExpansion < 1) turnsToExpansion = 1
                    "[$turnsToExpansion] turns to expansion".tr()
                } else {
                    "Stopped expansion".tr()
                }
        turnsToExpansionString += " (" + cityInfo.expansion.cultureStored + "/" +
                cityInfo.expansion.getCultureToNextTile() + ")"

        var turnsToPopString =
                when {
                    cityInfo.isGrowing() -> "[${cityInfo.getNumTurnsToNewPopulation()}] turns to new population".tr()
                    cityInfo.isStarving() -> "[${cityInfo.getNumTurnsToStarvation()}] turns to lose population".tr()
                    cityInfo.cityConstructions.currentConstruction == Constants.settler -> "Food converts to production".tr()
                    else -> "Stopped population growth".tr()
                }
        turnsToPopString +=  " (" + cityInfo.population.foodStored + "/" + cityInfo.population.getFoodToNextPopulation() + ")"

        val ministatsTable = Table().pad(5f)
        ministatsTable.defaults()

        for(stat in cityInfo.cityStats.currentCityStats.toHashMap()) {
            if(stat.key == Stat.Happiness) continue
            ministatsTable.add(ImageGetter.getStatIcon(stat.key.name)).size(20f).padRight(3f)
            ministatsTable.add(round(stat.value).toInt().toString().toLabel()).padRight(13f)
        }

        innerTable.add(unassignedPopString.toLabel()).row()
        innerTable.add(turnsToExpansionString.toLabel()).row()
        innerTable.add(turnsToPopString.toLabel()).row()
        if (cityInfo.isInResistance())
            innerTable.add("In resistance for another [${cityInfo.resistanceCounter}] turns".toLabel()).row()
        innerTable.addSeparator()
        innerTable.add(ministatsTable)

        pack()
    }
}
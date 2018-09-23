package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.gamebasics.Building
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter.getImage
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFont


class BuildingsTable(private val cityScreen: CityScreen) : Table() {

    internal fun update() {
        clear()
        val skin = CameraStageBaseScreen.skin
        val cityInfo = cityScreen.city
        val wonders = mutableListOf<Building>()
        val specialistBuildings = mutableListOf<Building>()
        val others = mutableListOf<Building>()

        for (building in cityInfo.cityConstructions.getBuiltBuildings()) {
            when {
                building.isWonder -> wonders.add(building)
                building.specialistSlots != null -> specialistBuildings.add(building)
                else -> others.add(building)
            }
        }

        fun createLabel(text:String): Label {
            val label = Label(text, skin)
            label.setFont(20)
            label.color = Color.GREEN
            return label
        }

        if (!wonders.isEmpty()) {

            add(createLabel("Wonders")).pad(5f).row()
            for (building in wonders)
                add(Label(building.name, skin)).pad(5f).row()
        }

        if (!specialistBuildings.isEmpty()) {
            add(createLabel("Specialist Buildings")).pad(5f).row()
            for (building in specialistBuildings) {
                add(Label(building.name, skin)).pad(5f)
                val specialists = Table()
                specialists.row().size(20f).pad(5f)
                if (!cityInfo.population.buildingsSpecialists.containsKey(building.name))
                    cityInfo.population.buildingsSpecialists[building.name] = Stats()
                val currentBuildingSpecialists = cityInfo.population.buildingsSpecialists[building.name]!!.toHashMap()
                for(stat in Stat.values()){
                    for (i in 1..(currentBuildingSpecialists[stat]!!).toInt()) {
                        specialists.add(getSpecialistIcon(
                                "StatIcons/${stat}Specialist.png", building.name,
                                currentBuildingSpecialists[stat]!! > i, stat))
                    }
                }

                add(specialists).row()
            }
        }

        if (!others.isEmpty()) {
            add(createLabel("Buildings")).pad(5f).row()
            for (building in others)
                add(Label(building.name, skin)).pad(5f).row()
        }
        pack()
    }


    private fun getSpecialistIcon(imageName: String, building: String, isFilled: Boolean, stat: Stat): Image {
        val specialist = getImage(imageName)
        specialist.setSize(50f, 50f)
        if (!isFilled) specialist.color = Color.GRAY
        specialist.onClick( {
            val cityInfo = cityScreen.city
            when {
                isFilled -> cityInfo.population.buildingsSpecialists[building]!!.add(stat,-1f) //unassign
                cityInfo.population.getFreePopulation() == 0 -> return@onClick
                else -> {
                    if (!cityInfo.population.buildingsSpecialists.containsKey(building))
                        cityInfo.population.buildingsSpecialists[building] = Stats()
                    cityInfo.population.buildingsSpecialists[building]!!.add(stat,1f) //assign!}
                }
            }

            cityInfo.cityStats.update()
            cityScreen.update()
        })

        return specialist
    }


}
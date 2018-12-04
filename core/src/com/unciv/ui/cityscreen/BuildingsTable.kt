package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.models.gamebasics.Building
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import java.util.*
import kotlin.collections.HashMap


class BuildingsTable(private val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin) {
    init {
        defaults().pad(10f)
    }

    internal fun update() {
        clear()
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

        if (!wonders.isEmpty()) {
            val wondersExpander = ExpanderTab("Wonders",skin)
            for (building in wonders) {
                wondersExpander.innerTable.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))
                wondersExpander.innerTable.add(Label(building.name.tr(), skin)).pad(5f).align(Align.left).row()
            }
            add(wondersExpander).row()
        }

        if (!specialistBuildings.isEmpty()) {
            val specialistBuildingsExpander = ExpanderTab("Specialist Buildings",skin)
            for (building in specialistBuildings) {
                specialistBuildingsExpander.innerTable.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))
                specialistBuildingsExpander.innerTable.add(Label(building.name.tr(), skin)).pad(5f)
                val specialistIcons = Table()
                specialistIcons.row().size(20f).pad(5f)
                for(stat in building.specialistSlots!!.toHashMap())
                    for(i in 0 until stat.value.toInt())
                        specialistIcons.add(getSpecialistIcon(stat.key)).size(20f)

                specialistBuildingsExpander.innerTable.add(specialistIcons).row()
            }
            add(specialistBuildingsExpander).row()

            // specialist allocation
            addSpecialistAllocation(skin, cityInfo)
        }

        if (!others.isEmpty()) {
            val buildingsExpanderTab = ExpanderTab("Buildings",skin)
            for (building in others) {
                buildingsExpanderTab.innerTable.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))
                buildingsExpanderTab.innerTable.add(Label(building.name.tr(), skin)).pad(5f).row()
            }
            add(buildingsExpanderTab).row()
        }

        addStatInfo()

        pack()
    }

    private fun addStatInfo() {
        val cityStats = cityScreen.city.cityStats
        val unifiedStatList = LinkedHashMap<String, Stats>(cityStats.baseStatList)
        for(entry in cityStats.happinessList.filter { it.value!=0f }){
            if(!unifiedStatList.containsKey(entry.key)) unifiedStatList[entry.key]= Stats()
            unifiedStatList[entry.key]!!.happiness=entry.value
        }

        val statToCauses = HashMap<Stat,HashMap<String,Float>>()
        for(stat in Stat.values()) statToCauses[stat] = hashMapOf()

        for(cause in unifiedStatList) {
            val statHashmap = cause.value.toHashMap()
            for (statEntry in statHashmap.filter { it.value != 0f })
                statToCauses[statEntry.key]!![cause.key] = statEntry.value
        }

        for(stat in statToCauses){
            val expander = ExpanderTab(stat.key.name.tr(),skin)
            expander.innerTable.defaults().pad(2f)
            for(entry in stat.value) {
                expander.innerTable.add(Label(entry.key.tr(), skin))
                expander.innerTable.add(Label(DecimalFormat("0.#").format(entry.value), skin)).row()
            }
            if(stat.value.isNotEmpty()){
                expander.innerTable.add(Label("Total".tr(),skin))
                expander.innerTable.add(Label(DecimalFormat("0.#").format(stat.value.values.sum()),skin))
            }
            add(expander).row()
        }

    }

    private fun addSpecialistAllocation(skin: Skin, cityInfo: CityInfo) {
        val specialistAllocationExpander = ExpanderTab("Specialist allocation", skin)
        specialistAllocationExpander.innerTable.defaults().pad(5f)


        val currentSpecialists = cityInfo.population.specialists.toHashMap()
        val maximumSpecialists = cityInfo.population.getMaxSpecialists()

        for (statToMaximumSpecialist in maximumSpecialists.toHashMap()) {
            if (statToMaximumSpecialist.value == 0f) continue
            val stat = statToMaximumSpecialist.key
            // these two are conflictingly named compared to above...
            val assignedSpecialists = currentSpecialists[statToMaximumSpecialist.key]!!.toInt()
            val maxSpecialists = statToMaximumSpecialist.value.toInt()
            if (assignedSpecialists > 0) {
                val unassignButton = TextButton("-", skin)
                unassignButton.label.setFontSize(24)
                unassignButton.onClick {
                    cityInfo.population.specialists.add(statToMaximumSpecialist.key, -1f)
                    cityInfo.cityStats.update()
                    cityScreen.update()
                }
                specialistAllocationExpander.innerTable.add(unassignButton)
            } else specialistAllocationExpander.innerTable.add()

            val specialistTable = Table()
            for (i in 1..maxSpecialists) {
                val icon = getSpecialistIcon(stat, i <= assignedSpecialists)
                specialistTable.add(icon).size(30f)
            }
            specialistAllocationExpander.innerTable.add(specialistTable)
            if (assignedSpecialists < maxSpecialists) {
                val assignButton = TextButton("+", skin)
                assignButton.label.setFontSize(24)
                assignButton.onClick {
                    cityInfo.population.specialists.add(statToMaximumSpecialist.key, +1f)
                    cityInfo.cityStats.update()
                    cityScreen.update()
                }
                if (cityInfo.population.getFreePopulation() == 0) assignButton.disable()
                specialistAllocationExpander.innerTable.add(assignButton)
            } else specialistAllocationExpander.innerTable.add()

            specialistAllocationExpander.innerTable.row()

            val specialistStatTable = Table().apply { defaults().pad(5f) }
            val specialistStats = cityInfo.cityStats.getStatsOfSpecialist(stat, cityInfo.civInfo.policies.adoptedPolicies).toHashMap()
            for (entry in specialistStats) {
                if (entry.value == 0f) continue
                specialistStatTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
                specialistStatTable.add(Label(entry.value.toInt().toString(), skin)).padRight(10f)
            }
            specialistAllocationExpander.innerTable.add()
            specialistAllocationExpander.innerTable.add(specialistStatTable).row()
        }
        add(specialistAllocationExpander).row()
    }

    private fun getSpecialistIcon(stat: Stat, isFilled: Boolean =true): Image {
        val specialist = ImageGetter.getImage("StatIcons/Specialist")
        if (!isFilled) specialist.color = Color.GRAY
        else specialist.color=when(stat){
            Stat.Production -> Color.BROWN
            Stat.Gold -> Color.GOLD
            Stat.Science -> Color.BLUE
            Stat.Culture -> Color.PURPLE
            else -> Color.WHITE
        }

        return specialist
    }

}
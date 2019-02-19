package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.tr
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import java.util.*


class CityInfoTable(private val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin) {
    init {
        defaults().pad(10f)
    }

    internal fun update() {
        clear()
        val cityInfo = cityScreen.city

        addBuildingInfo(cityInfo)

        addStatInfo()

        addGreatPersonPointInfo(cityInfo)

        pack()
    }


    private fun addBuildingInfo(cityInfo: CityInfo) {
        val wonders = mutableListOf<Building>()
        val specialistBuildings = mutableListOf<Building>()
        val otherBuildings = mutableListOf<Building>()

        for (building in cityInfo.cityConstructions.getBuiltBuildings()) {
            when {
                building.isWonder -> wonders.add(building)
                building.specialistSlots != null -> specialistBuildings.add(building)
                else -> otherBuildings.add(building)
            }
        }

        if (!wonders.isEmpty()) {
            val wondersExpander = ExpanderTab("Wonders".tr(), skin)
            for (building in wonders) {
                wondersExpander.innerTable.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))
                wondersExpander.innerTable.add(building.name.toLabel()).pad(5f).align(Align.left).row()
            }
            add(wondersExpander).row()
        }

        if (!specialistBuildings.isEmpty()) {
            val specialistBuildingsExpander = ExpanderTab("Specialist Buildings".tr(), skin)
            for (building in specialistBuildings) {
                specialistBuildingsExpander.innerTable.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))
                specialistBuildingsExpander.innerTable.add(building.name.toLabel()).pad(5f)
                val specialistIcons = Table()
                specialistIcons.row().size(20f).pad(5f)
                for (stat in building.specialistSlots!!.toHashMap())
                    for (i in 0 until stat.value.toInt())
                        specialistIcons.add(getSpecialistIcon(stat.key)).size(20f)

                specialistBuildingsExpander.innerTable.add(specialistIcons).row()
            }
            add(specialistBuildingsExpander).row()

            // specialist allocation
            addSpecialistAllocation(skin, cityInfo)
        }

        if (!otherBuildings.isEmpty()) {
            val buildingsExpanderTab = ExpanderTab("Buildings".tr(), skin)
            for (building in otherBuildings) {
                buildingsExpanderTab.innerTable.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))
                buildingsExpanderTab.innerTable.add(building.name.toLabel()).pad(5f).row()
            }
            add(buildingsExpanderTab).row()
        }
    }

    private fun addStatInfo() {
        val cityStats = cityScreen.city.cityStats
        val unifiedStatList = LinkedHashMap<String, Stats>(cityStats.baseStatList)

        for(stats in unifiedStatList.values) stats.happiness=0f


        // add happiness to stat list
        for(entry in cityStats.getCityHappiness().filter { it.value!=0f }){
            if(!unifiedStatList.containsKey(entry.key))
                unifiedStatList[entry.key]= Stats()
            unifiedStatList[entry.key]!!.happiness=entry.value
        }

        for(stat in Stat.values()){
            val expander = ExpanderTab(stat.name.tr(),skin)
            expander.innerTable.defaults().pad(2f)

            for(entry in unifiedStatList) {
                val specificStatValue = entry.value.toHashMap()[stat]!!
                if(specificStatValue==0f) continue
                expander.innerTable.add(entry.key.toLabel())
                expander.innerTable.add(DecimalFormat("0.#").format(specificStatValue).toLabel()).row()
            }
            for(entry in cityStats.statPercentBonusList){
                val specificStatValue = entry.value.toHashMap()[stat]!!
                if(specificStatValue==0f) continue
                expander.innerTable.add(entry.key.toLabel())
                val decimal = DecimalFormat("0.#").format(specificStatValue)
                expander.innerTable.add("+$decimal%".toLabel()).row()
            }

            if(expander.innerTable.hasChildren())
                add(expander).row()
        }
    }

    private fun addGreatPersonPointInfo(cityInfo: CityInfo) {
        val greatPersonPoints = cityInfo.getGreatPersonMap()
        val statToGreatPerson = GreatPersonManager().statToGreatPersonMapping
        for (stat in Stat.values()) {
            if (!statToGreatPerson.containsKey(stat)) continue
            val expanderName = "[" + statToGreatPerson[stat]!! + "] points"
            val expanderTab = ExpanderTab(expanderName.tr(), skin)
            expanderTab.innerTable.defaults().pad(3f)
            for (entry in greatPersonPoints) {
                val value = entry.value.toHashMap()[stat]!!
                if (value == 0f) continue
                expanderTab.innerTable.add(entry.key.toLabel())
                expanderTab.innerTable.add(DecimalFormat("0.#").format(value).toLabel()).row()
            }
            if (expanderTab.innerTable.hasChildren())
                add(expanderTab).row()
        }
    }

    private fun addSpecialistAllocation(skin: Skin, cityInfo: CityInfo) {
        val specialistAllocationExpander = ExpanderTab("Specialist Allocation".tr(), skin)
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
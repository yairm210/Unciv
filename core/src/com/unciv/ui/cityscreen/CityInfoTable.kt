package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.models.ruleset.Building
import com.unciv.models.translations.tr
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable
import java.text.DecimalFormat
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

class CityInfoTable(private val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin) {
    val pad = 5f
    init {
        defaults().pad(pad)
        width = cityScreen.stage.width/4
    }

    internal fun update() {
        clear()
        val cityInfo = cityScreen.city

        addCityStats(cityInfo)
        addBuildingsInfo(cityInfo)
        addStatInfo()
        addGreatPersonPointInfo(cityInfo)

        pack()
    }

    private fun addCategory(str: String, showHideTable: Table) {
        val titleTable = Table().background(ImageGetter.getBackground(ImageGetter.getBlue()))
        val width = cityScreen.stage.width/4 - 2*pad
        val showHideTableWrapper = Table()
        showHideTableWrapper.add(showHideTable).width(width)
        titleTable.add(str.toLabel(fontSize = 24))
        titleTable.onClick {
            if(showHideTableWrapper.hasChildren()) showHideTableWrapper.clear()
            else showHideTableWrapper.add(showHideTable).width(width)
        }
        add(titleTable).width(width).row()
        add(showHideTableWrapper).row()
    }

    fun addBuildingInfo(building: Building, wondersTable: Table){
        val wonderNameAndIconTable = Table()
        wonderNameAndIconTable.touchable = Touchable.enabled
        wonderNameAndIconTable.add(ImageGetter.getConstructionImage(building.name).surroundWithCircle(30f))
        wonderNameAndIconTable.add(building.name.toLabel()).pad(5f)
        wondersTable.add(wonderNameAndIconTable).pad(5f).fillX().row()

        val wonderDetailsTable = Table()
        wondersTable.add(wonderDetailsTable).pad(5f).align(Align.left).row()

        wonderNameAndIconTable.onClick {
            if(wonderDetailsTable.hasChildren())
                wonderDetailsTable.clear()
            else{
                val detailsString = building.getDescription(true,
                        cityScreen.city.civInfo, cityScreen.city.civInfo.gameInfo.ruleSet)
                wonderDetailsTable.add(detailsString.toLabel().apply { setWrap(true)})
                        .width(cityScreen.stage.width/4 - 2*pad ).row() // when you set wrap, then you need to manually set the size of the label
                if(!building.isWonder && !building.isNationalWonder) {
                    val sellAmount = cityScreen.city.getGoldForSellingBuilding(building.name)
                    val sellBuildingButton = TextButton("Sell for [$sellAmount] gold".tr(),skin)
                    wonderDetailsTable.add(sellBuildingButton).pad(5f).row()

                    sellBuildingButton.onClick {
                        YesNoPopupTable("Are you sure you want to sell this [${building.name}]?".tr(),
                            {
                                cityScreen.city.sellBuilding(building.name)
                                cityScreen.city.cityStats.update()
                                cityScreen.update()
                            }, cityScreen)
                    }
                    if (cityScreen.city.hasSoldBuildingThisTurn || cityScreen.city.isPuppet
                            || !UncivGame.Current.worldScreen.isPlayersTurn)
                        sellBuildingButton.disable()
                }
                wonderDetailsTable.addSeparator()
            }
        }
    }

    private fun addCityStats(cityInfo: CityInfo) {
        val statsTable = Table().align(Align.center)
        addCategory("Stats", statsTable)


        val columns = Stats().toHashMap().size
        statsTable.add(Label("{Unassigned population}:".tr()
                +" "+cityInfo.population.getFreePopulation().toString() + "/" + cityInfo.population.population, skin))
                .pad(5f).row()

        val turnsToExpansionString : String
        if (cityInfo.cityStats.currentCityStats.culture > 0) {
            var turnsToExpansion = ceil((cityInfo.expansion.getCultureToNextTile() - cityInfo.expansion.cultureStored)
                    / cityInfo.cityStats.currentCityStats.culture).toInt()
            if (turnsToExpansion < 1) turnsToExpansion = 1
            turnsToExpansionString = "[$turnsToExpansion] turns to expansion".tr()
        } else {
            turnsToExpansionString = "Stopped expansion".tr()
        }

        statsTable.add(Label(turnsToExpansionString + " (" + cityInfo.expansion.cultureStored + "/" + cityInfo.expansion.getCultureToNextTile() + ")",
                skin)).pad(5f).row()

        val turnsToPopString : String
        if (cityInfo.cityStats.currentCityStats.food > 0) {
            if (cityInfo.cityConstructions.currentConstruction == Constants.settler) {
                turnsToPopString = "Food converts to production".tr()
            } else {
                var turnsToPopulation = ceil((cityInfo.population.getFoodToNextPopulation()-cityInfo.population.foodStored)
                        / cityInfo.cityStats.currentCityStats.food).toInt()
                if (turnsToPopulation < 1) turnsToPopulation = 1
                turnsToPopString = "[$turnsToPopulation] turns to new population".tr()
            }
        } else if (cityInfo.cityStats.currentCityStats.food < 0) {
            val turnsToStarvation = floor(cityInfo.population.foodStored / -cityInfo.cityStats.currentCityStats.food).toInt() + 1
            turnsToPopString = "[$turnsToStarvation] turns to lose population".tr()
        } else {
            turnsToPopString = "Stopped population growth".tr()
        }
        statsTable.add(Label(turnsToPopString + " (" + cityInfo.population.foodStored + "/" + cityInfo.population.getFoodToNextPopulation() + ")"
                ,skin)).pad(5f).row()

        if (cityInfo.resistanceCounter > 0) {
            statsTable.add(Label("In resistance for another [${cityInfo.resistanceCounter}] turns".tr(),skin)).pad(5f).row()
        }

        statsTable.addSeparator()

        val ministatsTable = Table().pad(5f)
        ministatsTable.defaults()
        statsTable.add(ministatsTable)

        for(stat in cityInfo.cityStats.currentCityStats.toHashMap()) {
            if(stat.key == Stat.Happiness) continue
            ministatsTable.add(ImageGetter.getStatIcon(stat.key.name)).size(20f).padRight(3f)
            ministatsTable.add(round(stat.value).toInt().toString().toLabel()).padRight(13f)
        }
    }

    private fun addBuildingsInfo(cityInfo: CityInfo) {
        val wonders = mutableListOf<Building>()
        val specialistBuildings = mutableListOf<Building>()
        val otherBuildings = mutableListOf<Building>()

        for (building in cityInfo.cityConstructions.getBuiltBuildings()) {
            when {
                building.isWonder || building.isNationalWonder -> wonders.add(building)
                building.specialistSlots != null -> specialistBuildings.add(building)
                else -> otherBuildings.add(building)
            }
        }

        if (wonders.isNotEmpty()) {
            val wondersTable = Table()
            addCategory("Wonders",wondersTable)
            for (building in wonders) addBuildingInfo(building,wondersTable)
        }

        if (specialistBuildings.isNotEmpty()) {
            val specialistBuildingsTable = Table()
            addCategory("Specialist Buildings", specialistBuildingsTable)

            for (building in specialistBuildings) {
                addBuildingInfo(building, specialistBuildingsTable)
                val specialistIcons = Table()
                specialistIcons.row().size(20f).pad(5f)
                for (stat in building.specialistSlots!!.toHashMap())
                    for (i in 0 until stat.value.toInt())
                        specialistIcons.add(getSpecialistIcon(stat.key)).size(20f)

                specialistBuildingsTable.add(specialistIcons).pad(0f).row()
            }

            // specialist allocation
            addSpecialistAllocation(skin, cityInfo)
        }

        if (!otherBuildings.isEmpty()) {
            val regularBuildingsTable = Table()
            addCategory("Buildings", regularBuildingsTable)
            for (building in otherBuildings) addBuildingInfo(building, regularBuildingsTable)
        }
    }

    private fun addStatInfo() {
        val cityStats = cityScreen.city.cityStats

        for(stat in Stat.values().filter { it!=Stat.Happiness }){
            val relevantBaseStats = cityStats.baseStatList.filter { it.value.get(stat)!=0f }
            if(relevantBaseStats.isEmpty()) continue

            val statValuesTable = Table().apply { defaults().pad(2f) }
            addCategory(stat.name, statValuesTable)

            statValuesTable.add("Base values".toLabel(fontSize= 24)).colspan(2).row()
            var sumOfAllBaseValues = 0f
            for(entry in relevantBaseStats) {
                val specificStatValue = entry.value.get(stat)
                sumOfAllBaseValues += specificStatValue
                statValuesTable.add(entry.key.toLabel())
                statValuesTable.add(DecimalFormat("0.#").format(specificStatValue).toLabel()).row()
            }
            statValuesTable.addSeparator()
            statValuesTable.add("Total".toLabel())
            statValuesTable.add(DecimalFormat("0.#").format(sumOfAllBaseValues).toLabel()).row()

            val relevantBonuses = cityStats.statPercentBonusList.filter { it.value.get(stat)!=0f }
            if(relevantBonuses.isNotEmpty()) {
                statValuesTable.add("Bonuses".toLabel(fontSize = 24)).colspan(2).padTop(20f).row()
                var sumOfBonuses = 0f
                for (entry in relevantBonuses) {
                    val specificStatValue = entry.value.get(stat)
                    sumOfBonuses += specificStatValue
                    statValuesTable.add(entry.key.toLabel())
                    val decimal = DecimalFormat("0.#").format(specificStatValue)
                    if (specificStatValue > 0) statValuesTable.add("+$decimal%".toLabel()).row()
                    else statValuesTable.add("$decimal%".toLabel()).row() // negative bonus
                }
                statValuesTable.addSeparator()
                statValuesTable.add("Total".toLabel())
                val decimal = DecimalFormat("0.#").format(sumOfBonuses)
                if (sumOfBonuses > 0) statValuesTable.add("+$decimal%".toLabel()).row()
                else statValuesTable.add("$decimal%".toLabel()).row() // negative bonus
            }


            statValuesTable.add("Final".toLabel(fontSize = 24)).colspan(2).padTop(20f).row()
            var finalTotal = 0f
            for (entry in cityStats.finalStatList) {
                val specificStatValue = entry.value.get(stat)
                finalTotal += specificStatValue
                if (specificStatValue == 0f) continue
                statValuesTable.add(entry.key.toLabel())
                statValuesTable.add(DecimalFormat("0.#").format(specificStatValue).toLabel()).row()
            }
            statValuesTable.addSeparator()
            statValuesTable.add("Total".toLabel())
            statValuesTable.add(DecimalFormat("0.#").format(finalTotal).toLabel()).row()
        }
    }

    private fun addGreatPersonPointInfo(cityInfo: CityInfo) {
        val greatPersonPoints = cityInfo.getGreatPersonMap()
        val statToGreatPerson = GreatPersonManager().statToGreatPersonMapping
        for (stat in Stat.values()) {
            if (!statToGreatPerson.containsKey(stat)) continue
            if(greatPersonPoints.all { it.value.get(stat)==0f }) continue

            val expanderName = "[" + statToGreatPerson[stat]!! + "] points"
            val greatPersonTable = Table()
            addCategory(expanderName, greatPersonTable)
            for (entry in greatPersonPoints) {
                val value = entry.value.toHashMap()[stat]!!
                if (value == 0f) continue
                greatPersonTable.add(entry.key.toLabel()).padRight(10f)
                greatPersonTable.add(DecimalFormat("0.#").format(value).toLabel()).row()
            }
        }
    }

    private fun addSpecialistAllocation(skin: Skin, cityInfo: CityInfo) {
        val specialistAllocationTable = Table()
        addCategory("Specialist Allocation", specialistAllocationTable) // todo WRONG, BAD - table should contain all the below specialist stuff

        val currentSpecialists = cityInfo.population.specialists.toHashMap()
        val maximumSpecialists = cityInfo.population.getMaxSpecialists()

        for (statToMaximumSpecialist in maximumSpecialists.toHashMap()) {
            val specialistPickerTable = Table()
            if (statToMaximumSpecialist.value == 0f) continue
            val stat = statToMaximumSpecialist.key
            // these two are conflictingly named compared to above...
            val assignedSpecialists = currentSpecialists[stat]!!.toInt()
            val maxSpecialists = statToMaximumSpecialist.value.toInt()
            if (assignedSpecialists > 0 && !cityInfo.isPuppet) {
                val unassignButton = TextButton("-", skin)
                unassignButton.label.setFontSize(24)
                unassignButton.onClick {
                    cityInfo.population.specialists.add(stat, -1f)
                    cityInfo.cityStats.update()
                    cityScreen.update()
                }
                if(!UncivGame.Current.worldScreen.isPlayersTurn) unassignButton.disable()
                specialistPickerTable.add(unassignButton)
            } else specialistPickerTable.add()

            val specialistIconTable = Table()
            for (i in 1..maxSpecialists) {
                val icon = getSpecialistIcon(stat, i <= assignedSpecialists)
                specialistIconTable.add(icon).size(30f)
            }
            specialistPickerTable.add(specialistIconTable)
            if (assignedSpecialists < maxSpecialists && !cityInfo.isPuppet) {
                val assignButton = TextButton("+", skin)
                assignButton.label.setFontSize(24)
                assignButton.onClick {
                    cityInfo.population.specialists.add(statToMaximumSpecialist.key, 1f)
                    cityInfo.cityStats.update()
                    cityScreen.update()
                }
                if (cityInfo.population.getFreePopulation() == 0 || !UncivGame.Current.worldScreen.isPlayersTurn)
                    assignButton.disable()
                specialistPickerTable.add(assignButton)
            } else specialistPickerTable.add()
            specialistAllocationTable.add(specialistPickerTable).row()

            val specialistStatTable = Table().apply { defaults().pad(5f) }
            val specialistStats = cityInfo.cityStats.getStatsOfSpecialist(stat, cityInfo.civInfo.policies.adoptedPolicies).toHashMap()
            for (entry in specialistStats) {
                if (entry.value == 0f) continue
                specialistStatTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
                specialistStatTable.add(entry.value.toInt().toString().toLabel()).padRight(10f)
            }
            specialistAllocationTable.add(specialistStatTable).row()
        }
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
package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.GreatPersonManager
import com.unciv.models.ruleset.Building
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class CityInfoTable(private val cityScreen: CityScreen) : Table(CameraStageBaseScreen.skin) {
    private val pad = 10f

    private val showConstructionsTableButton = "Show construction queue".toTextButton()
    private val scrollPane: ScrollPane
    private val innerTable = Table(skin)

    init {
        align(Align.topLeft)

        showConstructionsTableButton.onClick {
            cityScreen.showConstructionsTable = true
            cityScreen.update()
        }

        innerTable.width = cityScreen.stage.width/4
        innerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK,0.5f))
        scrollPane = ScrollPane(innerTable.addBorder(2f, Color.WHITE))
        scrollPane.setOverscroll(false, false)

        add(showConstructionsTableButton).left().padLeft(pad).padBottom(pad).row()
        add(scrollPane).left().row()
    }

    internal fun update() {
        val cityInfo = cityScreen.city

        innerTable.clear()

        innerTable.apply {
            addBuildingsInfo(cityInfo)
            addStatInfo()
            addGreatPersonPointInfo(cityInfo)
        }

        getCell(scrollPane).maxHeight(stage.height - showConstructionsTableButton.height - pad - 10f)
        pack()
    }

    private fun Table.addCategory(str: String, showHideTable: Table) {
        val width = cityScreen.stage.width / 4 - 2 * pad
        val showHideTableWrapper = Table()
                .add(showHideTable)
                .minWidth(width)
                .table

        val titleTable = Table()
                .background(ImageGetter.getBackground(ImageGetter.getBlue()))
                .pad(4f)
                .addCell(str.toLabel(fontSize = FONT_SIZE_CATEGORY_HEADER))
                .onClick {
                    if (showHideTableWrapper.hasChildren()) {
                        showHideTableWrapper.clear()
                    } else {
                        showHideTableWrapper.add(showHideTable).minWidth(width)
                    }
                }
        addSeparator()

        add(titleTable).minWidth(width).row()
        add(showHideTableWrapper).row()
    }

    private fun Table.addBuildingInfo(building: Building, wondersTable: Table){
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
                    val sellBuildingButton = "Sell for [$sellAmount] gold".toTextButton()
                    wonderDetailsTable.add(sellBuildingButton).pad(5f).row()

                    sellBuildingButton.onClick {
                        sellBuildingButton.disable()
                        cityScreen.closeAllPopups()

                        YesNoPopup("Are you sure you want to sell this [${building.name}]?".tr(),
                                {
                                    cityScreen.city.sellBuilding(building.name)
                                    cityScreen.city.cityStats.update()
                                    cityScreen.update()
                                }, cityScreen,
                                {
                                    cityScreen.update()
                                }).open()
                    }
                    if ((cityScreen.city.hasSoldBuildingThisTurn && !cityScreen.city.civInfo.gameInfo.gameParameters.godMode) || cityScreen.city.isPuppet
                            || !UncivGame.Current.worldScreen.isPlayersTurn)
                        sellBuildingButton.disable()
                }
                wonderDetailsTable.addSeparator()
            }
        }
    }

    private fun Table.addBuildingsInfo(cityInfo: CityInfo) {
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
                        specialistIcons.add(ImageGetter.getSpecialistIcon(stat.key)).size(20f)

                specialistBuildingsTable.add(specialistIcons).pad(0f).row()
            }

            // specialist allocation
//            addCategory("Specialist Allocation", SpecialistAllocationTable(cityScreen)) todo
        }

        if (!otherBuildings.isEmpty()) {
            val regularBuildingsTable = Table()
            addCategory("Buildings", regularBuildingsTable)
            for (building in otherBuildings) addBuildingInfo(building, regularBuildingsTable)
        }
    }

    private fun Table.addStatInfo() {
        val cityStats = cityScreen.city.cityStats

        for(stat in Stat.values().filter { it!=Stat.Happiness }){
            val relevantBaseStats = cityStats.baseStatList.filter { it.value.get(stat)!=0f }
            if(relevantBaseStats.isEmpty()) continue

            val statValuesTable = Table().apply { defaults().pad(2f) }
            addCategory(stat.name, statValuesTable)

            statValuesTable.add("Base values".toLabel(fontSize= FONT_SIZE_STAT_INFO_HEADER)).pad(4f).colspan(2).row()
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
                statValuesTable.add("Bonuses".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).colspan(2).padTop(20f).row()
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


            statValuesTable.add("Final".toLabel(fontSize = FONT_SIZE_STAT_INFO_HEADER)).colspan(2).padTop(20f).row()
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

            statValuesTable.padBottom(4f)
        }
    }

    private fun Table.addGreatPersonPointInfo(cityInfo: CityInfo) {
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

    companion object {
        private const val FONT_SIZE_CATEGORY_HEADER: Int = 24
        private const val FONT_SIZE_STAT_INFO_HEADER = 22
    }

}


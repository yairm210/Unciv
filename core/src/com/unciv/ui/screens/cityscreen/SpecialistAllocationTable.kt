package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.components.extensions.addBorder
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class SpecialistAllocationTable(private val cityScreen: CityScreen) : Table(BaseScreen.skin) {
    val city = cityScreen.city

    fun update() {
        clear()
        // Auto/Manual Specialists Toggle
        // Color of "color" coming from Skin.json that's loaded into BaseScreen
        // 5 columns: unassignButton, AllocationTable, assignButton, SeparatorVertical, SpecialistsStatsTabe
        if (cityScreen.canCityBeChanged()) {
            if (city.manualSpecialists) {
                val manualSpecialists = "Manual Specialists".toLabel()
                    .addBorder(5f, BaseScreen.skin.getColor("color"))
                manualSpecialists.onClick {
                    city.manualSpecialists = false
                    city.reassignPopulation(); cityScreen.update()
                }
                add(manualSpecialists).colspan(5).row()
            } else {
                val autoSpecialists = "Auto Specialists".toLabel()
                    .addBorder(5f, BaseScreen.skin.getColor("color"))
                autoSpecialists.onClick { city.manualSpecialists = true; update() }
                add(autoSpecialists).colspan(5).row()
            }
        }
        for ((specialistName, maxSpecialists) in city.population.getMaxSpecialists()) {
            if (!city.getRuleset().specialists.containsKey(specialistName)) // specialist doesn't exist in this ruleset, probably a mod
                continue
            val newSpecialists = city.population.getNewSpecialists()
            val assignedSpecialists = newSpecialists[specialistName]

            if (cityScreen.canChangeState) add(getUnassignButton(assignedSpecialists, specialistName))
            add(getAllocationTable(assignedSpecialists, maxSpecialists, specialistName)).pad(10f)
            if (cityScreen.canChangeState) add(getAssignButton(assignedSpecialists, maxSpecialists, specialistName))
            addSeparatorVertical().pad(10f)
            add(getSpecialistStatsTable(specialistName)).row()
        }
        pack()
    }


    private fun getAllocationTable(assignedSpecialists: Int, maxSpecialists: Int, specialistName: String): Table {

        val specialistIconTable = Table()
        val specialistObject = city.getRuleset().specialists[specialistName]!!
        for (i in 1..maxSpecialists) {
            val color = if (i <= assignedSpecialists) specialistObject.colorObject
            else Color.GRAY // unassigned
            val icon = ImageGetter.getSpecialistIcon(color)
            specialistIconTable.add(icon).size(30f)
            if (i % 5 == 0) specialistIconTable.row()
        }
        return specialistIconTable
    }

    private fun getAssignButton(assignedSpecialists: Int, maxSpecialists: Int, specialistName: String): Actor {

        if (assignedSpecialists >= maxSpecialists || city.isPuppet) return Table()
        val assignButton = "+".toLabel(Color.BLACK, Constants.headingFontSize)
            .apply { this.setAlignment(Align.center) }
            .surroundWithCircle(30f).apply { circle.color = Color.GREEN.darken(0.2f) }
        assignButton.onClick {
            city.population.specialistAllocations.add(specialistName, 1)
            city.manualSpecialists = true
            city.cityStats.update()
            cityScreen.update()
        }
        if (city.population.getFreePopulation() == 0 || !cityScreen.canChangeState)
            assignButton.clear()
        return assignButton
    }

    private fun getUnassignButton(assignedSpecialists: Int, specialistName: String): Actor {
        val unassignButton = "-".toLabel(Color.BLACK, Constants.headingFontSize)
            .apply { this.setAlignment(Align.center) }
            .surroundWithCircle(30f).apply { circle.color = Color.RED.darken(0.1f) }
        unassignButton.onClick {
            city.population.specialistAllocations.add(specialistName, -1)
            city.manualSpecialists = true
            city.cityStats.update()
            cityScreen.update()
        }

        if (assignedSpecialists <= 0 || city.isPuppet) unassignButton.isVisible = false
        if (!cityScreen.canChangeState) unassignButton.clear()
        return unassignButton
    }


    private fun getSpecialistStatsTable(specialistName: String): Table {
        val specialistStatTable = Table().apply { defaults().padBottom(5f).padTop(5f) }
        val specialistStats = city.cityStats.getStatsOfSpecialist(specialistName)
        var itemsInRow = 0
        fun incrementItemsInRow(){
            itemsInRow++
            if (itemsInRow % 3 == 0){
                itemsInRow = 0
                specialistStatTable.row()
            }
        }

        for ((key, value) in specialistStats) {
            if (value == 0f) continue
            specialistStatTable.add(value.toInt().toLabel())
            specialistStatTable.add(ImageGetter.getStatIcon(key.name)).size(20f).padRight(10f)
            incrementItemsInRow()
        }

        val specialist = city.getRuleset().specialists[specialistName]!!

        for (s in specialist.greatPersonPoints) {
            specialistStatTable.add(s.value.toLabel())
            specialistStatTable.add(ImageGetter.getUnitIcon(s.key, Color.GOLD).toGroup(20f)).padRight(10f)
            incrementItemsInRow()
        }

        return specialistStatTable
    }


    fun asExpander(onChange: (() -> Unit)?): ExpanderTab {
        return ExpanderTab(
            title = "{Specialists}:",
            fontSize = Constants.defaultFontSize,
            persistenceID = "CityStatsTable.Specialists",
            startsOutOpened = true,
            toggleKey = KeyboardBinding.SpecialistDetail,
            onChange = onChange
        ) {
            it.add(this)
            update()
        }
    }

}

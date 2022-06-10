package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ExpanderTab
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.addSeparatorVertical
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel

class SpecialistAllocationTable(val cityScreen: CityScreen) : Table(BaseScreen.skin) {
    val cityInfo = cityScreen.city

    fun update() {
        clear()
        // Auto/Manual Specialists Toggle
        // Color of "color" coming from Skin.json that's loaded into BaseScreen
        // 5 columns: unassignButton, AllocationTable, assignButton, SeparatorVertical, SpecialistsStatsTabe
        if (cityScreen.canCityBeChanged()) {
            if (cityInfo.manualSpecialists) {
                val manualSpecialists = "Manual Specialists".toLabel()
                    .addBorder(5f, BaseScreen.skin.get("color", Color::class.java))
                manualSpecialists.onClick {
                    cityInfo.manualSpecialists = false
                    cityInfo.reassignPopulation(); cityScreen.update()
                }
                add(manualSpecialists).colspan(5).row()
            } else {
                val autoSpecialists = "Auto Specialists".toLabel()
                    .addBorder(5f, BaseScreen.skin.get("color", Color::class.java))
                autoSpecialists.onClick { cityInfo.manualSpecialists = true; update() }
                add(autoSpecialists).colspan(5).row()
            }
        }
        for ((specialistName, maxSpecialists) in cityInfo.population.getMaxSpecialists()) {
            if (!cityInfo.getRuleset().specialists.containsKey(specialistName)) // specialist doesn't exist in this ruleset, probably a mod
                continue
            val newSpecialists = cityInfo.population.getNewSpecialists()
            val assignedSpecialists = newSpecialists[specialistName]!!

            if (cityScreen.canChangeState) add(getUnassignButton(assignedSpecialists, specialistName))
            add(getAllocationTable(assignedSpecialists, maxSpecialists, specialistName)).pad(10f)
            if (cityScreen.canChangeState) add(getAssignButton(assignedSpecialists, maxSpecialists, specialistName))
            addSeparatorVertical().pad(10f)
            add(getSpecialistStatsTable(specialistName)).row()
        }
        pack()
    }


    fun getAllocationTable(assignedSpecialists: Int, maxSpecialists: Int, specialistName: String): Table {

        val specialistIconTable = Table()
        val specialistObject = cityInfo.getRuleset().specialists[specialistName]!!
        for (i in 1..maxSpecialists) {
            val color = if (i <= assignedSpecialists) specialistObject.colorObject
            else Color.GRAY // unassigned
            val icon = ImageGetter.getSpecialistIcon(color)
            specialistIconTable.add(icon).size(30f)
        }
        return specialistIconTable
    }

    private fun getAssignButton(assignedSpecialists: Int, maxSpecialists: Int, specialistName: String): Actor {

        if (assignedSpecialists >= maxSpecialists || cityInfo.isPuppet) return Table()
        val assignButton = "+".toLabel(Color.BLACK, Constants.headingFontSize)
            .apply { this.setAlignment(Align.center) }
            .surroundWithCircle(30f).apply { circle.color = Color.GREEN.darken(0.2f) }
        assignButton.onClick {
            cityInfo.population.specialistAllocations.add(specialistName, 1)
            cityInfo.manualSpecialists = true
            cityInfo.cityStats.update()
            cityScreen.update()
        }
        if (cityInfo.population.getFreePopulation() == 0 || !UncivGame.Current.worldScreen!!.isPlayersTurn)
            assignButton.clear()
        return assignButton
    }

    private fun getUnassignButton(assignedSpecialists: Int, specialistName: String): Actor {
        val unassignButton = "-".toLabel(Color.BLACK, Constants.headingFontSize)
            .apply { this.setAlignment(Align.center) }
            .surroundWithCircle(30f).apply { circle.color = Color.RED.darken(0.1f) }
        unassignButton.onClick {
            cityInfo.population.specialistAllocations.add(specialistName, -1)
            cityInfo.manualSpecialists = true
            cityInfo.cityStats.update()
            cityScreen.update()
        }

        if (assignedSpecialists <= 0 || cityInfo.isPuppet) unassignButton.isVisible = false
        if (!UncivGame.Current.worldScreen!!.isPlayersTurn) unassignButton.clear()
        return unassignButton
    }


    private fun getSpecialistStatsTable(specialistName: String): Table {
        val specialistStatTable = Table().apply { defaults().pad(5f) }
        val specialistStats = cityInfo.cityStats.getStatsOfSpecialist(specialistName)
        for ((key, value) in specialistStats) {
            if (value == 0f) continue
            specialistStatTable.add(ImageGetter.getStatIcon(key.name)).size(20f)
            specialistStatTable.add(value.toInt().toLabel()).padRight(10f)
        }
        return specialistStatTable
    }


    fun asExpander(onChange: (() -> Unit)?): ExpanderTab {
        return ExpanderTab(
            title = "{Specialists}:",
            fontSize = Constants.defaultFontSize,
            persistenceID = "CityStatsTable.Specialists",
            startsOutOpened = true,
            onChange = onChange
        ) {
            it.add(this)
            update()
        }
    }

}

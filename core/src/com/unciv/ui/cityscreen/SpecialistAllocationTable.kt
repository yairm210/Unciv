package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.ui.utils.*

class SpecialistAllocationTable(val cityScreen: CityScreen): Table(CameraStageBaseScreen.skin){
    val cityInfo = cityScreen.city

    fun update() {
        clear()

        for ((specialistName, amount) in cityInfo.population.getMaxSpecialists()) {
            if (!cityInfo.getRuleset().specialists.containsKey(specialistName)) // specialist doesn't exist in this ruleset, probably a mod
                continue
            val newSpecialists = cityInfo.population.getNewSpecialists()
            val assignedSpecialists = newSpecialists[specialistName]!!
            val maxSpecialists = cityInfo.population.getMaxSpecialists()[specialistName]!!

            if (cityScreen.canChangeState) add(getUnassignButton(assignedSpecialists, specialistName))
            add(getAllocationTable(assignedSpecialists, maxSpecialists, specialistName)).pad(10f)
            if (cityScreen.canChangeState) add(getAssignButton(assignedSpecialists, maxSpecialists, specialistName))
            addSeparatorVertical().pad(10f)
            add(getSpecialistStatsTable(specialistName)).row()
        }
        pack()
    }


    fun getAllocationTable(assignedSpecialists: Int, maxSpecialists: Int, specialistName: String):Table{

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

    private fun getAssignButton(assignedSpecialists: Int, maxSpecialists: Int, specialistName: String):Actor {

        if (assignedSpecialists >= maxSpecialists || cityInfo.isPuppet) return Table()
        val assignButton = "+".toLabel(Color.BLACK,24)
                .apply { this.setAlignment(Align.center) }
                .surroundWithCircle(30f).apply { circle.color= Color.GREEN.cpy().lerp(Color.BLACK,0.2f) }
        assignButton.onClick {
            cityInfo.population.specialistAllocations.add(specialistName, 1)
            cityInfo.cityStats.update()
            cityScreen.update()
        }
        if (cityInfo.population.getFreePopulation() == 0 || !UncivGame.Current.worldScreen.isPlayersTurn)
            assignButton.clear()
        return assignButton
    }

    private fun getUnassignButton(assignedSpecialists: Int, specialistName: String):Actor {
        val unassignButton = "-".toLabel(Color.BLACK,24)
                .apply { this.setAlignment(Align.center) }
                .surroundWithCircle(30f).apply { circle.color= Color.RED.cpy().lerp(Color.BLACK,0.1f) }
        unassignButton.onClick {
            cityInfo.population.specialistAllocations.add(specialistName, -1)
            cityInfo.cityStats.update()
            cityScreen.update()
        }

        if (assignedSpecialists <= 0 || cityInfo.isPuppet) unassignButton.isVisible=false
        if (!UncivGame.Current.worldScreen.isPlayersTurn) unassignButton.clear()
        return unassignButton
    }


    private fun getSpecialistStatsTable(specialistName: String): Table {
        val specialistStatTable = Table().apply { defaults().pad(5f) }
        val specialistStats = cityInfo.cityStats.getStatsOfSpecialist(specialistName).toHashMap()
        for (entry in specialistStats) {
            if (entry.value == 0f) continue
            specialistStatTable.add(ImageGetter.getStatIcon(entry.key.name)).size(20f)
            specialistStatTable.add(entry.value.toInt().toLabel()).padRight(10f)
        }
        return specialistStatTable
    }
}
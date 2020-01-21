package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.*

class SpecialistAllocationTable(val cityScreen: CityScreen): Table(CameraStageBaseScreen.skin){
    val cityInfo = cityScreen.city

    fun update() {
        background = ImageGetter.getBackground(Color.BLACK.cpy().apply { a=0.7f })
        clear()

        for (statToMaximumSpecialist in cityInfo.population.getMaxSpecialists().toHashMap()) {
            if (statToMaximumSpecialist.value == 0f) continue

            val stat = statToMaximumSpecialist.key
            add(getAllocationTable(stat)).pad(10f)
            addSeparatorVertical().pad(5f)
            add(getSpecialistStatsTable(stat)).row()
        }
        pack()
    }


    fun getAllocationTable(stat: Stat):Table{
        val specialistPickerTable = Table()

        val assignedSpecialists = cityInfo.population.specialists.get(stat).toInt()
        val maxSpecialists = cityInfo.population.getMaxSpecialists().get(stat).toInt()

        specialistPickerTable.add(getUnassignButton(assignedSpecialists, stat))

        val specialistIconTable = Table()
        for (i in 1..maxSpecialists) {
            val icon = ImageGetter.getSpecialistIcon(stat, i <= assignedSpecialists)
            specialistIconTable.add(icon).size(30f)
        }
        specialistPickerTable.add(specialistIconTable)

        specialistPickerTable.add(getAssignButton(assignedSpecialists, maxSpecialists, stat))

        return specialistPickerTable
    }

    private fun getAssignButton(assignedSpecialists: Int, maxSpecialists: Int, stat: Stat):Actor {
        if (assignedSpecialists >= maxSpecialists || cityInfo.isPuppet) return Table()
        val assignButton = TextButton("+", skin)
        assignButton.label.setFontSize(24)
        assignButton.onClick {
            cityInfo.population.specialists.add(stat, 1f)
            cityInfo.cityStats.update()
            cityScreen.update()
        }
        if (cityInfo.population.getFreePopulation() == 0 || !UncivGame.Current.worldScreen.isPlayersTurn)
            assignButton.disable()
        return assignButton
    }

    private fun getUnassignButton(assignedSpecialists: Int, stat: Stat):Actor {
        if (assignedSpecialists <= 0 || cityInfo.isPuppet) return Table()

        val unassignButton = TextButton("-", skin)
        unassignButton.label.setFontSize(24)
        unassignButton.onClick {
            cityInfo.population.specialists.add(stat, -1f)
            cityInfo.cityStats.update()
            cityScreen.update()
        }
        if (!UncivGame.Current.worldScreen.isPlayersTurn) unassignButton.disable()
        return unassignButton
    }


    private fun getSpecialistStatsTable(stat: Stat): Table {
        val specialistStatTable = Table().apply { defaults().pad(5f) }
        val specialistStats = cityInfo.cityStats.getStatsOfSpecialist(stat, cityInfo.civInfo.policies.adoptedPolicies).toHashMap()
        for (entry in specialistStats) {
            if (entry.value == 0f) continue
            specialistStatTable.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f)
            specialistStatTable.add(entry.value.toInt().toString().toLabel()).padRight(10f)
        }
        return specialistStatTable
    }
}
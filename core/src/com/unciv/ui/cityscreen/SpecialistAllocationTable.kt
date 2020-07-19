package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.models.stats.Stat
import com.unciv.ui.utils.*

class SpecialistAllocationTable(val cityScreen: CityScreen): Table(CameraStageBaseScreen.skin){
    val cityInfo = cityScreen.city

    fun update() {
        clear()

        for (statToMaximumSpecialist in cityInfo.population.getMaxSpecialists().toHashMap()) {
            if (statToMaximumSpecialist.value == 0f) continue

            val stat = statToMaximumSpecialist.key

            val assignedSpecialists = cityInfo.population.specialists.get(stat).toInt()
            val maxSpecialists = cityInfo.population.getMaxSpecialists().get(stat).toInt()

            if (cityScreen.canChangeState) add(getUnassignButton(assignedSpecialists,stat))
            add(getAllocationTable(assignedSpecialists, maxSpecialists, stat)).pad(10f)
            if (cityScreen.canChangeState) add(getAssignButton(assignedSpecialists,maxSpecialists,stat))
            addSeparatorVertical().pad(10f)
            add(getSpecialistStatsTable(stat)).row()
        }
        pack()
    }


    fun getAllocationTable(assignedSpecialists: Int, maxSpecialists: Int, stat: Stat):Table{

        val specialistIconTable = Table()
        for (i in 1..maxSpecialists) {
            val icon = ImageGetter.getSpecialistIcon(stat, i <= assignedSpecialists)
            specialistIconTable.add(icon).size(30f)
        }
        return specialistIconTable
    }

    private fun getAssignButton(assignedSpecialists: Int, maxSpecialists: Int, stat: Stat):Actor {

        if (assignedSpecialists >= maxSpecialists || cityInfo.isPuppet) return Table()
        val assignButton = "+".toLabel(Color.BLACK,24)
                .apply { this.setAlignment(Align.center) }
                .surroundWithCircle(30f).apply { circle.color= Color.GREEN.cpy().lerp(Color.BLACK,0.2f) }
        assignButton.onClick {
            cityInfo.population.specialists.add(stat, 1f)
            cityInfo.cityStats.update()
            cityScreen.update()
        }
        if (cityInfo.population.getFreePopulation() == 0 || !UncivGame.Current.worldScreen.isPlayersTurn)
            assignButton.clear()
        return assignButton
    }

    private fun getUnassignButton(assignedSpecialists: Int, stat: Stat):Actor {
        val unassignButton = "-".toLabel(Color.BLACK,24)
                .apply { this.setAlignment(Align.center) }
                .surroundWithCircle(30f).apply { circle.color= Color.RED.cpy().lerp(Color.BLACK,0.1f) }
        unassignButton.onClick {
            cityInfo.population.specialists.add(stat, -1f)
            cityInfo.cityStats.update()
            cityScreen.update()
        }

        if (assignedSpecialists <= 0 || cityInfo.isPuppet) unassignButton.isVisible=false
        if (!UncivGame.Current.worldScreen.isPlayersTurn) unassignButton.clear()
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
package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.translations.tr
import com.unciv.ui.components.SmallButtonStyle
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class SpecialistAllocationTable(private val cityScreen: CityScreen) : Table(BaseScreen.skin) {
    val city = cityScreen.city
    private val smallButtonStyle = SmallButtonStyle()

    fun update() {
        // 5 columns: "-" unassignButton, AllocationTable, "+" assignButton, SeparatorVertical, SpecialistsStatsTable
        clear()

        // Auto/Manual Specialists Toggle
        if (cityScreen.canCityBeChanged()) {
            val toggleButton = if (city.manualSpecialists) {
                "Manual Specialists".toTextButton(smallButtonStyle)
                   .onActivation {
                       city.manualSpecialists = false
                       city.reassignPopulation()
                       cityScreen.update()
                   }
            } else {
                "Auto Specialists".toTextButton(smallButtonStyle)
                    .onActivation {
                        city.manualSpecialists = true
                        update()
                    }
            }
            add(toggleButton).colspan(5).row()
        }

        // Since getMaxSpecialists is a Counter, iteration is potentially in random order. Ensure consistency by simplified sorting (no collator)
        for ((specialistName, maxSpecialists) in city.population.getMaxSpecialists().asSequence().sortedBy { it.key }) {
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
        specialistIconTable.addTooltip(specialistName, 24f)
        return specialistIconTable
    }

    private fun getAssignButton(assignedSpecialists: Int, maxSpecialists: Int, specialistName: String): Actor {

        if (assignedSpecialists >= maxSpecialists || city.isPuppet) return Table()
        val assignButton = "+".toLabel(ImageGetter.CHARCOAL, Constants.headingFontSize)
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
        val unassignButton = "-".toLabel(ImageGetter.CHARCOAL, Constants.headingFontSize)
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
        val specialist = city.getRuleset().specialists[specialistName]!!
        val maxStat = specialistStats.maxOf { it.value }
        val lightGreen = Color(0x7fff7fff)
        var itemsInRow = 0

        fun addWrapping(value: Int, labelColor: Color, icon: Actor) {
            specialistStatTable.add(value.tr().toLabel(labelColor))
            specialistStatTable.add(icon).size(20f).padRight(10f)

            itemsInRow++
            if (itemsInRow % 3 == 0) {
                itemsInRow = 0
                specialistStatTable.row()
            }
        }

        // Show gpp points first, makes recognizing what the specialist is good for easier
        // greatPersonPoints is a Counter so iteration order is potentially random:
        // Sort by unit name without collator to ensure consistency in those rare mods where one Specialist gives points to several GP counters
        for ((gpName, gpPoints) in specialist.greatPersonPoints.asSequence().sortedBy { it.key }) {
            val greatPerson = city.getRuleset().units[gpName] ?: continue
            addWrapping(gpPoints, Color.GOLD, ImageGetter.getUnitIcon(greatPerson, Color.GOLD))
        }

        // This uses Stats.iterator() which ensures consistent Stat order and returns no zero value
        // To make recognizing the Specialist still easier, the max values of the Stats are done in green
        for ((stat, value) in specialistStats) {
            addWrapping(value.toInt(), if (value == maxStat) lightGreen else Color.WHITE, ImageGetter.getStatIcon(stat.name))
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

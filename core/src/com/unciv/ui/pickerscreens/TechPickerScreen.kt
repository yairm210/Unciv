package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.TechManager
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.Technology
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.tr
import java.util.*

class TechPickerScreen(internal val civInfo: CivilizationInfo) : PickerScreen() {

    private var techNameToButton = HashMap<String, TextButton>()
    private var isFreeTechPick: Boolean = false
    private var selectedTech: Technology? = null
    private var civTech: TechManager = civInfo.tech
    private var techsToResearch: ArrayList<String>

    // All these are to counter performance problems when updating buttons for all techs.
    private var researchableTechs = GameBasics.Technologies.keys
            .filter { civTech.canBeResearched(it) }.toHashSet()
    private val LightBlue = Color.BLUE.cpy().lerp(Color.WHITE, 0.3f)
    private val turnsToTech = GameBasics.Technologies.values.associateBy ({ it.name },{civTech.turnsToTech(it.name)})

    constructor(freeTechPick: Boolean, civInfo: CivilizationInfo) : this(civInfo) {
        isFreeTechPick = freeTechPick
    }

    init {
        techsToResearch = ArrayList(civTech.techsToResearch)

        val techMatrix = Array<Array<Technology?>>(17) { arrayOfNulls(10) } // Divided into columns, then rows

        for (technology in GameBasics.Technologies.values) {
            techMatrix[technology.column!!.columnNumber - 1][technology.row - 1] = technology
        }

        for (i in 0..9) {
            topTable.row().pad(5f)

            for (j in techMatrix.indices) {
                val tech = techMatrix[j][i]
                if (tech == null)
                    topTable.add() // empty cell
                else {
                    val TB = TextButton("", CameraStageBaseScreen.skin)
                    techNameToButton[tech.name] = TB
                    TB.addClickListener {
                        selectTechnology(tech)
                    }
                    topTable.add<TextButton>(TB)
                }
            }
        }

        setButtonsInfo()

        rightSideButton.setText("Pick a tech")
        rightSideButton.addClickListener {
            if (isFreeTechPick) {
                civTech.techsResearched.add(selectedTech!!.name)
                civTech.freeTechs -= 1
                civInfo.addNotification("We have stumbled upon the discovery of " + selectedTech!!.name + "!", null, Color.BLUE)
                if (selectedTech!!.name == civTech.currentTechnology())
                    civTech.techsToResearch.remove(selectedTech!!.name)
            } else
                civTech.techsToResearch = techsToResearch
            game.setWorldScreen()
            game.worldScreen.update()
            dispose()
        }

        displayTutorials("TechPickerScreen")
    }

    private fun setButtonsInfo() {
        for (techName in techNameToButton.keys) {
            val TB = techNameToButton[techName]!!
            when {
                civTech.isResearched(techName) -> TB.color = Color.GREEN
                techsToResearch.contains(techName) -> TB.color = LightBlue
                researchableTechs.contains(techName) -> TB.color = Color.WHITE
                else -> TB.color = Color.BLACK
            }

            TB.isChecked = false
            var text = techName.tr()

            if (techName == selectedTech?.name) {
                TB.isChecked = true
                TB.color = TB.color.cpy().lerp(Color.LIGHT_GRAY, 0.5f)
            }

            if (techsToResearch.contains(techName) && techsToResearch.size > 1) {
                text += " (" + techsToResearch.indexOf(techName) + ")"
            }

            if (!civTech.isResearched(techName)) text += "\r\n" + turnsToTech[techName] + " "+"turns".tr()
            TB.setText(text)
        }
    }

    private fun selectTechnology(tech: Technology?) {
        selectedTech = tech
        descriptionLabel.setText(tech!!.description)
        if (isFreeTechPick) {
            selectTechnologyForFreeTech(tech)
            return
        }

        if (civTech.isResearched(tech.name)) {
            rightSideButton.setText("Research".tr())
            rightSideButton.disable()
            setButtonsInfo()
            return
        }

        if (researchableTechs.contains(tech.name)) {
            techsToResearch.clear()
            techsToResearch.add(tech.name)
        } else {
            val prerequisites = Stack<String>()
            val checkPrerequisites = ArrayDeque<String>()
            checkPrerequisites.add(tech.name)
            while (!checkPrerequisites.isEmpty()) {
                val techNameToCheck = checkPrerequisites.pop()
                if (civTech.isResearched(techNameToCheck) || prerequisites.contains(techNameToCheck))
                    continue //no need to add or check prerequisites
                val techToCheck = GameBasics.Technologies[techNameToCheck]
                for (str in techToCheck!!.prerequisites)
                    if (!checkPrerequisites.contains(str)) checkPrerequisites.add(str)
                prerequisites.add(techNameToCheck)
            }
            techsToResearch.clear()
            while (!prerequisites.isEmpty()) techsToResearch.add(prerequisites.pop())
        }

        pick("{Research} \r\n".tr() + techsToResearch[0])
        setButtonsInfo()
    }

    private fun selectTechnologyForFreeTech(tech: Technology) {
        if (researchableTechs.contains(tech.name)) {
            pick("Pick " + selectedTech!!.name + "\r\n as free tech!")
        } else {
            rightSideButton.setText("Pick a free tech")
            rightSideButton.disable()
        }
    }

}
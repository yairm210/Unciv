package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.TechManager
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import java.util.*


class TechPickerScreen(internal val civInfo: CivilizationInfo) : PickerScreen() {

    private var techNameToButton = HashMap<String, TechButton>()
    private var isFreeTechPick: Boolean = false
    private var selectedTech: Technology? = null
    private var civTech: TechManager = civInfo.tech
    private var tempTechsToResearch: ArrayList<String>

    // All these are to counter performance problems when updating buttons for all techs.
    private var researchableTechs = GameBasics.Technologies.keys
            .filter { civTech.canBeResearched(it) }.toHashSet()

    private val currentTechColor = colorFromRGB(7,46,43)
    private val researchedTechColor = colorFromRGB(133,112,39)
    private val researchableTechColor = colorFromRGB(28,170,0)
    private val queuedTechColor = colorFromRGB(39,114,154)


    private val turnsToTech = GameBasics.Technologies.values.associateBy ({ it.name },{civTech.turnsToTech(it.name)})

    constructor(freeTechPick: Boolean, civInfo: CivilizationInfo) : this(civInfo) {
        isFreeTechPick = freeTechPick
    }

    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen(); dispose() }

        tempTechsToResearch = ArrayList(civTech.techsToResearch)

        val columns = 17
        val techMatrix = Array<Array<Technology?>>(columns) { arrayOfNulls(10) } // Divided into columns, then rows

        for (technology in GameBasics.Technologies.values) {
            techMatrix[technology.column!!.columnNumber][technology.row - 1] = technology
        }

        val eras = ArrayList<Label>()
        for(i in techMatrix.indices) eras.add(Label("",CameraStageBaseScreen.skin).apply { setFontColor(Color.WHITE) })
        eras.forEach { topTable.add(it) }

        for (i in 0..9) {
            topTable.row().pad(5f)

            for (j in techMatrix.indices) {
                val tech = techMatrix[j][i]
                if (tech == null)
                    topTable.add() // empty cell

                else {
                    val techButton = TechButton(tech.name,civTech)

                    techNameToButton[tech.name] = techButton
                    techButton.onClick {
                        selectTechnology(tech)
                    }
                    topTable.add(techButton)
                    if(eras[j].text.toString()=="") // name of era was not yet set
                        eras[j].setText((tech.era().toString()+" era").tr())
                }
            }
        }

        setButtonsInfo()

        rightSideButton.setText("Pick a tech".tr())
        rightSideButton.onClick("paper") {
            if (isFreeTechPick)     civTech.getFreeTechnology(selectedTech!!.name)
            else    civTech.techsToResearch = tempTechsToResearch
            
            game.setWorldScreen()
            game.worldScreen.shouldUpdate=true
            dispose()
        }

        displayTutorials("TechPickerScreen")
    }

    private fun setButtonsInfo() {
        for (techName in techNameToButton.keys) {
            val techButton = techNameToButton[techName]!!
            when {
                civTech.isResearched(techName) && techName!="Future Tech" -> techButton.color = researchedTechColor
                tempTechsToResearch.isNotEmpty() && tempTechsToResearch.first() == techName -> techButton.color = currentTechColor
                tempTechsToResearch.contains(techName) -> techButton.color = queuedTechColor
                researchableTechs.contains(techName) -> techButton.color = researchableTechColor
                else -> techButton.color = Color.BLACK
            }

            var text = techName.tr()

            if (techName == selectedTech?.name) {
                techButton.color = techButton.color.cpy().lerp(Color.LIGHT_GRAY, 0.5f)
            }

            if (tempTechsToResearch.contains(techName) && tempTechsToResearch.size > 1) {
                text += " (" + tempTechsToResearch.indexOf(techName) + ")"
            }

            if (!civTech.isResearched(techName) || techName=="Future Tech")
                text += "\r\n" + turnsToTech[techName] + " {turns}".tr()

            techButton.text.setText(text)
        }
    }

    private fun selectTechnology(tech: Technology?) {
        selectedTech = tech
        descriptionLabel.setText(tech!!.description)
        if (isFreeTechPick) {
            selectTechnologyForFreeTech(tech)
            return
        }

        if (civTech.isResearched(tech.name) && tech.name != "Future Tech") {
            rightSideButton.setText("Pick a tech".tr())
            rightSideButton.disable()
            setButtonsInfo()
            return
        }

        tempTechsToResearch.clear()
        tempTechsToResearch.addAll(civTech.getRequiredTechsToDestination(tech))

        pick("Research [${tempTechsToResearch[0]}]".tr())
        setButtonsInfo()
    }



    private fun selectTechnologyForFreeTech(tech: Technology) {
        if (researchableTechs.contains(tech.name)) {
            pick("Pick [${selectedTech!!.name}] as free tech".tr())
        } else {
            rightSideButton.setText("Pick a free tech".tr())
            rightSideButton.disable()
        }
    }

}
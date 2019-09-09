package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.TechManager
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import java.util.*
import kotlin.collections.HashSet


class TechPickerScreen(internal val civInfo: CivilizationInfo, centerOnTech: Technology? = null) : PickerScreen() {

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
        setDefaultCloseAction()
        onBackButtonClicked { UnCivGame.Current.setWorldScreen() }
        scrollPane.style = skin.get(ScrollPane.ScrollPaneStyle::class.java) // So we can see scrollbars

        tempTechsToResearch = ArrayList(civTech.techsToResearch)

        val columns = GameBasics.Technologies.values.map { it.column!!.columnNumber}.max()!! +1
        val techMatrix = Array<Array<Technology?>>(columns) { arrayOfNulls(10) } // Divided into columns, then rows

        for (technology in GameBasics.Technologies.values) {
            techMatrix[technology.column!!.columnNumber][technology.row - 1] = technology
        }

        val eras = ArrayList<Label>()
        for(i in techMatrix.indices)
            eras.add("".toLabel().setFontColor(Color.WHITE))
        eras.forEach { topTable.add(it) }

        // Create tech table (row by row)
        for (i in 0..9) {
            topTable.row().pad(5f)

            for (j in techMatrix.indices) {
                val tech = techMatrix[j][i]
                if (tech == null)
                    topTable.add() // empty cell

                else {
                    val techButton = TechButton(tech.name,civTech)

                    techNameToButton[tech.name] = techButton
                    techButton.onClick { selectTechnology(tech) }
                    topTable.add(techButton)
                }
            }
        }

        // Set era names (column by column)
        val alreadyDisplayedEras = HashSet<String>()
        for(j in techMatrix.indices)
            for(i in 0..9)
            {
                val tech = techMatrix[j][i]
                if(tech==null) continue
                val eraName = tech.era().name
                if(!alreadyDisplayedEras.contains(eraName)) { // name of era was not yet displayed
                    eras[j].setText("$eraName era".tr())
                    alreadyDisplayedEras.add(eraName)
                }
            }

        setButtonsInfo()

        rightSideButton.setText("Pick a tech".tr())
        rightSideButton.onClick("paper") {
            if (isFreeTechPick) civTech.getFreeTechnology(selectedTech!!.name)
            else civTech.techsToResearch = tempTechsToResearch

            game.setWorldScreen()
            game.worldScreen.shouldUpdate = true
            dispose()
        }

        displayTutorials("TechPickerScreen")

        // per default show current/recent technology,
        // and possibly select it to show description,
        // which is very helpful when just discovered and clicking the notification
        val tech = if (centerOnTech != null) centerOnTech else civInfo.tech.currentTechnology()
        if (tech != null) {
            // select only if there it doesn't mess up tempTechsToResearch
            if (civInfo.tech.isResearched(tech.name) || civInfo.tech.techsToResearch.size <= 1)
                selectTechnology(tech, true)
            else centerOnTechnology(tech)
        }

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

    private fun selectTechnology(tech: Technology?, center: Boolean = false) {

        selectedTech = tech
        descriptionLabel.setText(tech?.description)

        if(tech==null)
            return

        // center on technology
        if (center) {
            centerOnTechnology(tech)
        }

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

    private fun centerOnTechnology(tech: Technology) {
        Gdx.app.postRunnable {
            techNameToButton[tech.name]?.let {
                scrollPane.scrollTo(it.x, it.y, it.width, it.height, true, true)
                scrollPane.updateVisualScroll()
            }
        }
    }


    private fun selectTechnologyForFreeTech(tech: Technology) {
        if (researchableTechs.contains(tech.name)&&!civTech.isResearched(tech.name)) {
            pick("Pick [${selectedTech!!.name}] as free tech".tr())
        } else {
            rightSideButton.setText("Pick a free tech".tr())
            rightSideButton.disable()
        }
    }

}
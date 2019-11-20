package com.unciv.ui.pickerscreens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.TechManager
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import java.util.*


class TechPickerScreen(internal val civInfo: CivilizationInfo, switchfromWorldScreen: Boolean = true, centerOnTech: Technology? = null) : PickerScreen() {

    private var techNameToButton = HashMap<String, TechButton>()
    private var isFreeTechPick: Boolean = false
    private var selectedTech: Technology? = null
    private var civTech: TechManager = civInfo.tech
    private var tempTechsToResearch: ArrayList<String>
    private var lines=ArrayList<Image>()

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
        scrollPane.setOverscroll(false,false)
        tempTechsToResearch = ArrayList(civTech.techsToResearch)

        val columns = GameBasics.Technologies.values.map { it.column!!.columnNumber}.max()!! +1
        val techMatrix = Array<Array<Technology?>>(columns) { arrayOfNulls(10) } // Divided into columns, then rows

        for (technology in GameBasics.Technologies.values) {
            techMatrix[technology.column!!.columnNumber][technology.row - 1] = technology
        }

        val erasName = arrayOf("Ancient","Classical","Medieval","Renaissance","Industrial","Modern","Information","Future")
        for (i in 0..7) {
            val j = if (erasName[i]!="Ancient" && erasName[i]!="Future") 2 else 3
            if (i%2==0) topTable.add((erasName[i]+" era").toLabel().addBorder(2f, Color.BLUE)).fill().colspan(j)
            else topTable.add((erasName[i]+" era").toLabel().addBorder(2f, Color.FIREBRICK)).fill().colspan(j)
        }

        // Create tech table (row by row)
        for (i in 0..9) {
            topTable.row().pad(5f).padRight(40f)

            for (j in techMatrix.indices) {
                val tech = techMatrix[j][i]
                if (tech == null)
                    topTable.add() // empty cell

                else {
                    val techButton = TechButton(tech.name, civTech,false)

                    techNameToButton[tech.name] = techButton
                    techButton.onClick { selectTechnology(tech, false, switchfromWorldScreen) }
                    topTable.add(techButton)
                }
            }
        }

        setButtonsInfo()
        if (!switchfromWorldScreen){
            rightSideButton.apply {
                disable()
                setText("Tech Tree Of [${civInfo.civName}]".tr())
            }
        }
        else rightSideButton.setText("Pick a tech".tr())
        rightSideButton.onClick("paper") {
            if (isFreeTechPick) civTech.getFreeTechnology(selectedTech!!.name)
            else civTech.techsToResearch = tempTechsToResearch

            game.setWorldScreen()
            game.worldScreen.shouldUpdate = true
            dispose()
        }

        displayTutorials("Technology")

        // per default show current/recent technology,
        // and possibly select it to show description,
        // which is very helpful when just discovered and clicking the notification
        val tech = if (centerOnTech != null) centerOnTech else civInfo.tech.currentTechnology()
        if (tech != null) {
            // select only if there it doesn't mess up tempTechsToResearch
            if (civInfo.tech.isResearched(tech.name) || civInfo.tech.techsToResearch.size <= 1)
                selectTechnology(tech, true, switchfromWorldScreen)
            else centerOnTechnology(tech)
        }

    }

    private fun setButtonsInfo() {
        for (techName in techNameToButton.keys) {
            val techButton = techNameToButton[techName]!!
            techButton.color = when {
                civTech.isResearched(techName) && techName != Constants.futureTech -> researchedTechColor
                // if we're here to pick a free tech, show the current tech like the rest of the researchables so it'll be obvious what we can pick
                tempTechsToResearch.firstOrNull() == techName && !isFreeTechPick -> currentTechColor
                researchableTechs.contains(techName) && !civTech.isResearched(techName) -> researchableTechColor
                tempTechsToResearch.contains(techName) -> queuedTechColor
                else -> Color.GRAY
            }

            var text = techName.tr()

            if (techName == selectedTech?.name) {
                techButton.color = techButton.color.cpy().lerp(Color.LIGHT_GRAY, 0.5f)
            }

            if (tempTechsToResearch.contains(techName) && tempTechsToResearch.size > 1) {
                text += " (" + tempTechsToResearch.indexOf(techName) + ")"
            }

            if (!civTech.isResearched(techName) || techName== Constants.futureTech)
                text += "\r\n" + turnsToTech[techName] + " {turns}".tr()

            techButton.text.setText(text)
        }


        addConnectingLines()
    }

    private fun addConnectingLines() {
        topTable.pack() // Needed for the lines to work!
        for (line in lines) line.remove()
        lines.clear()

        for (tech in GameBasics.Technologies.values) {
            val techButton = techNameToButton[tech.name]!!
            for (prerequisite in tech.prerequisites) {
                val prerequisiteButton = techNameToButton[prerequisite]!!
                val techButtonCoords = Vector2(0f, techButton.height / 2)
                techButton.localToStageCoordinates(techButtonCoords)
                topTable.stageToLocalCoordinates(techButtonCoords)

                val prerequisiteCoords = Vector2(prerequisiteButton.width, prerequisiteButton.height / 2)
                prerequisiteButton.localToStageCoordinates(prerequisiteCoords)
                topTable.stageToLocalCoordinates(prerequisiteCoords)

                val line = ImageGetter.getLine(techButtonCoords.x, techButtonCoords.y,
                        prerequisiteCoords.x, prerequisiteCoords.y, 2f)

                val lineColor = when {
                    civTech.isResearched(tech.name) && tech.name != Constants.futureTech -> researchedTechColor
                    civTech.isResearched(prerequisite) -> researchableTechColor
                    else -> Color.GRAY
                }
                line.color = lineColor

                topTable.addActor(line)
                lines.add(line)
            }
        }
    }

    private fun selectTechnology(tech: Technology?, center: Boolean = false, switchfromWorldScreen: Boolean = true) {

        selectedTech = tech
        descriptionLabel.setText(tech?.getDescription())

        if (!switchfromWorldScreen)
            return

        if(tech==null)
            return

        // center on technology
        if (center) {
            centerOnTechnology(tech)
        }

        if (isFreeTechPick) {
            selectTechnologyForFreeTech(tech)
            setButtonsInfo()
            return
        }

        if (civTech.isResearched(tech.name) && tech.name != Constants.futureTech) {
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
        if (researchableTechs.contains(tech.name)&& (!civTech.isResearched(tech.name) || tech.name==Constants.futureTech)) {
            pick("Pick [${selectedTech!!.name}] as free tech".tr())
        } else {
            rightSideButton.setText("Pick a free tech".tr())
            rightSideButton.disable()
        }
    }

}

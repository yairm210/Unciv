package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.TechManager
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaCategories
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.utils.concurrency.Concurrency


class TechPickerScreen(
    internal val civInfo: CivilizationInfo,
    centerOnTech: Technology? = null,
    private val freeTechPick: Boolean = false
) : PickerScreen() {

    private var techNameToButton = HashMap<String, TechButton>()
    private var selectedTech: Technology? = null
    private var civTech: TechManager = civInfo.tech
    private var tempTechsToResearch: ArrayList<String>
    private var lines = ArrayList<Image>()

    /** We need this to be a separate table, and NOT the topTable, because *inhales*
     * When call setConnectingLines we need to pack() the table so that the lines will align correctly, BUT
     *  this causes the table to be SMALLER THAN THE SCREEN for small tech trees from mods,
     *  meaning the tech tree is in a crumpled heap at the lower-left corner of the screen
     * Having this be a separate table allows us to leave the TopTable as is (that is: auto-width to fit the scrollPane)
     *  leaving us the juicy small tech tree right in the center.
     */
    private val techTable = Table()

    // All these are to counter performance problems when updating buttons for all techs.
    private var researchableTechs = civInfo.gameInfo.ruleSet.technologies.keys
            .filter { civTech.canBeResearched(it) }.toHashSet()

    private val currentTechColor = colorFromRGB(7, 46, 43)
    private val researchedTechColor = colorFromRGB(133, 112, 39)
    private val researchableTechColor = colorFromRGB(28, 170, 0)
    private val queuedTechColor = colorFromRGB(39, 114, 154)


    private val turnsToTech = civInfo.gameInfo.ruleSet.technologies.values.associateBy({ it.name }, { civTech.turnsToTech(it.name) })

    init {
        setDefaultCloseAction()
        scrollPane.setOverscroll(false, false)

        descriptionLabel.onClick {
            if (selectedTech != null)
                game.pushScreen(CivilopediaScreen(civInfo.gameInfo.ruleSet, CivilopediaCategories.Technology, selectedTech!!.name))
        }

        tempTechsToResearch = ArrayList(civTech.techsToResearch)

        createTechTable()
        setButtonsInfo()
        topTable.add(techTable)

        rightSideButton.setText("Pick a tech".tr())
        rightSideButton.onClick(UncivSound.Paper) {
            if (freeTechPick) {
                val freeTech = selectedTech!!.name
                // More evil people fast-clicking to cheat - #4977
                if (!researchableTechs.contains(freeTech)) return@onClick
                civTech.getFreeTechnology(selectedTech!!.name)
            }
            else civTech.techsToResearch = tempTechsToResearch

            game.settings.addCompletedTutorialTask("Pick technology")

            game.popScreen()
        }

        // per default show current/recent technology,
        // and possibly select it to show description,
        // which is very helpful when just discovered and clicking the notification
        val tech = centerOnTech ?: civInfo.tech.currentTechnology()
        if (tech != null) {
            // select only if there it doesn't mess up tempTechsToResearch
            if (civInfo.tech.isResearched(tech.name) || civInfo.tech.techsToResearch.size <= 1)
                selectTechnology(tech, true)
            else centerOnTechnology(tech)
        } else {
            // center on any possible technology which is ready for the research right now
            val firstAvailable = researchableTechs.firstOrNull()
            val firstAvailableTech = civInfo.gameInfo.ruleSet.technologies[firstAvailable]
            if (firstAvailableTech != null)
                centerOnTechnology(firstAvailableTech)
        }
    }

    private fun createTechTable() {
        val allTechs = civInfo.gameInfo.ruleSet.technologies.values
        if (allTechs.isEmpty()) return
        val columns = allTechs.maxOf { it.column!!.columnNumber } + 1
        val rows = allTechs.maxOf { it.row } + 1
        val techMatrix = Array<Array<Technology?>>(columns) { arrayOfNulls(rows) } // Divided into columns, then rows

        for (technology in allTechs) {
            techMatrix[technology.column!!.columnNumber][technology.row - 1] = technology
        }

        val erasNamesToColumns = LinkedHashMap<String, ArrayList<Int>>()
        for (tech in allTechs) {
            val era = tech.era()
            if (!erasNamesToColumns.containsKey(era)) erasNamesToColumns[era] = ArrayList()
            val columnNumber = tech.column!!.columnNumber
            if (!erasNamesToColumns[era]!!.contains(columnNumber)) erasNamesToColumns[era]!!.add(columnNumber)
        }
        var i = 0
        for ((era, eraColumns) in erasNamesToColumns) {
            val columnSpan = eraColumns.size
            val color = if (i % 2 == 0) Color.BLUE else Color.FIREBRICK
            i++
            techTable.add(era.toLabel().addBorder(2f, color)).fill().colspan(columnSpan)
        }

        for (rowIndex in 0 until rows) {
            techTable.row().pad(5f).padRight(40f)

            for (columnIndex in techMatrix.indices) {
                val tech = techMatrix[columnIndex][rowIndex]
                if (tech == null)
                    techTable.add() // empty cell

                else {
                    val techButton = TechButton(tech.name, civTech, false)

                    techNameToButton[tech.name] = techButton
                    techButton.onClick { selectTechnology(tech, false) }
                    techTable.add(techButton).fillX()
                }
            }
        }
    }

    private fun setButtonsInfo() {
        for (techName in techNameToButton.keys) {
            val techButton = techNameToButton[techName]!!
            techButton.color = when {
                civTech.isResearched(techName) && techName != Constants.futureTech -> researchedTechColor
                // if we're here to pick a free tech, show the current tech like the rest of the researchables so it'll be obvious what we can pick
                tempTechsToResearch.firstOrNull() == techName && !freeTechPick -> currentTechColor
                researchableTechs.contains(techName) -> researchableTechColor
                tempTechsToResearch.contains(techName) -> queuedTechColor
                else -> Color.GRAY
            }

            var text = techName.tr()

            if (techName == selectedTech?.name && techButton.color != currentTechColor) {
                techButton.color = techButton.color.darken(0.5f)
            }

            techButton.orderIndicator?.remove()
            if (tempTechsToResearch.contains(techName) && tempTechsToResearch.size > 1) {
                techButton.addOrderIndicator(tempTechsToResearch.indexOf(techName) + 1)
            }

            if (!civTech.isResearched(techName) || techName == Constants.futureTech)
                text += "\r\n" + turnsToTech[techName] + "${Fonts.turn}".tr()

            techButton.text.setText(text)
        }

        addConnectingLines()
    }

    private fun addConnectingLines() {
        techTable.pack() // required for the table to have the button positions set, so topTable.stageToLocalCoordinates will be correct
        scrollPane.updateVisualScroll()

        for (line in lines) line.remove()
        lines.clear()

        for (tech in civInfo.gameInfo.ruleSet.technologies.values) {
            if (!techNameToButton.containsKey(tech.name)) {
                ToastPopup("Tech ${tech.name} appears to be missing - perhaps two techs have the same row & column", this)
                continue
            }
            val techButton = techNameToButton[tech.name]!!
            for (prerequisite in tech.prerequisites) {
                if (!techNameToButton.containsKey(prerequisite)) {
                    ToastPopup("Tech $prerequisite. prerequisite of ${tech.name}, appears to be missing - perhaps two techs have the same row & column", this)
                    continue
                }
                val prerequisiteButton = techNameToButton[prerequisite]!!
                val techButtonCoords = Vector2(0f, techButton.height / 2)
                techButton.localToStageCoordinates(techButtonCoords)
                techTable.stageToLocalCoordinates(techButtonCoords)

                val prerequisiteCoords = Vector2(prerequisiteButton.width, prerequisiteButton.height / 2)
                prerequisiteButton.localToStageCoordinates(prerequisiteCoords)
                techTable.stageToLocalCoordinates(prerequisiteCoords)

                val line = ImageGetter.getLine(techButtonCoords.x, techButtonCoords.y,
                        prerequisiteCoords.x, prerequisiteCoords.y, 2f)

                val lineColor = when {
                    civTech.isResearched(tech.name) && !tech.isContinuallyResearchable() -> researchedTechColor
                    civTech.isResearched(prerequisite) -> researchableTechColor
                    tempTechsToResearch.contains(tech.name) -> queuedTechColor
                    else -> Color.GRAY
                }
                line.color = lineColor

                techTable.addActor(line)
                line.toBack()
                lines.add(line)
            }
        }
    }

    private fun selectTechnology(tech: Technology?, center: Boolean = false, switchFromWorldScreen: Boolean = true) {

        val previousSelectedTech = selectedTech
        selectedTech = tech
        descriptionLabel.setText(tech?.getDescription(civInfo.gameInfo.ruleSet))

        if (!switchFromWorldScreen)
            return

        if (tech == null)
            return

        // center on technology
        if (center) centerOnTechnology(tech)

        if (freeTechPick) {
            selectTechnologyForFreeTech(tech)
            setButtonsInfo()
            return
        }

        if (civInfo.gameInfo.gameParameters.godMode && !civInfo.tech.isResearched(tech.name)
                && selectedTech == previousSelectedTech) {
            civInfo.tech.addTechnology(tech.name)
        }

        if (civTech.isResearched(tech.name) && !tech.isContinuallyResearchable()) {
            rightSideButton.setText("Pick a tech".tr())
            rightSideButton.disable()
            setButtonsInfo()
            return
        }

        if (!UncivGame.Current.worldScreen!!.canChangeState) {
            rightSideButton.disable()
            return
        }

        val pathToTech = civTech.getRequiredTechsToDestination(tech)
        for (requiredTech in pathToTech) {
            for (unique in requiredTech.uniqueObjects
                .filter { it.type == UniqueType.OnlyAvailableWhen && !it.conditionalsApply(civInfo) }) {
                rightSideButton.setText(unique.text.tr())
                rightSideButton.disable()
                return
            }
        }

        tempTechsToResearch.clear()
        tempTechsToResearch.addAll(pathToTech.map { it.name })

        val label = "Research [${tempTechsToResearch[0]}]".tr()
        val techProgression = getTechProgressLabel(tempTechsToResearch)

        pick("${label}\n${techProgression}")
        setButtonsInfo()
    }

    private fun getTechProgressLabel(techs: List<String>): String {
        val progress = techs.sumOf { tech -> civTech.researchOfTech(tech) }
        val techCost = techs.sumOf { tech -> civInfo.tech.costOfTech(tech) }
        return "(${progress}/${techCost})"
    }

    private fun centerOnTechnology(tech: Technology) {
        Concurrency.runOnGLThread {
            techNameToButton[tech.name]?.let {
                scrollPane.scrollTo(it.x, it.y, it.width, it.height, true, true)
                scrollPane.updateVisualScroll()
            }
        }
    }

    private fun selectTechnologyForFreeTech(tech: Technology) {
        if (researchableTechs.contains(tech.name)) {
            val label = "Pick [${tech.name}] as free tech".tr()
            val techProgression = getTechProgressLabel(listOf(tech.name))
            pick("${label}\n${techProgression}")
        } else {
            rightSideButton.setText("Pick a free tech".tr())
            rightSideButton.disable()
        }
    }

}

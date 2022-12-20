package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
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
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.utils.concurrency.Concurrency
import kotlin.math.abs


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
    private var eraLabels = ArrayList<Label>()

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

    private val currentTechColor = colorFromRGB(72, 147, 175)
    private val researchedTechColor = colorFromRGB(255, 215, 0)
    private val researchableTechColor = colorFromRGB(28, 170, 0)
    private val queuedTechColor = colorFromRGB(7*2, 46*2, 43*2)


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
        techTable.background = skinStrings.getUiBackground("TechPickerScreen/Background", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.bottomTable.background = skinStrings.getUiBackground("TechPickerScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)

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

        for (label in eraLabels) label.remove()
        eraLabels.clear()

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
        for ((era, eraColumns) in erasNamesToColumns) {
            val columnSpan = eraColumns.size
            val color = when {
                civTech.era.name == era -> queuedTechColor
                civInfo.gameInfo.ruleSet.eras[era]!!.eraNumber < civTech.era.eraNumber -> colorFromRGB(255, 175, 0)
                else -> Color.BLACK
            }

            val table1 = Table().pad(1f)
            val table2 = Table()

            table1.background = skinStrings.getUiBackground("General/Border", tintColor = Color.WHITE)
            table2.background = skinStrings.getUiBackground("General/Border", tintColor = color)

            val label = era.toLabel().apply {
                setAlignment(Align.center)
                if (civInfo.gameInfo.ruleSet.eras[era]!!.eraNumber < civTech.era.eraNumber)
                    this.color = colorFromRGB(120, 46, 16) }

            eraLabels.add(label)

            table2.add(label).growX()
            table1.add(table2).growX()

            techTable.add(table1).fill().colspan(columnSpan)
        }

        for (rowIndex in 0 until rows) {

            techTable.row()

            for (columnIndex in techMatrix.indices) {
                val tech = techMatrix[columnIndex][rowIndex]

                val table = Table().pad(2f).padRight(20f).padLeft(20f)
                if (rowIndex == 0)
                    table.padTop(7f)
                table.toBack()

                if (erasNamesToColumns[civTech.era.name]!!.contains(columnIndex))
                    table.background = skinStrings.getUiBackground("TechPickerScreen/Background", tintColor = queuedTechColor.darken(0.5f))

                if (tech == null) {
                    techTable.add(table).fill()
                } else {
                    val techButton = TechButton(tech.name, civTech, false)
                    table.add(techButton)
                    techNameToButton[tech.name] = techButton
                    techButton.onClick { selectTechnology(tech, false) }
                    techTable.add(table).fillX()
                }
            }
        }
    }

    private fun setButtonsInfo() {
        for (techName in techNameToButton.keys) {
            val techButton = techNameToButton[techName]!!
            techButton.setButtonColor(when {
                civTech.isResearched(techName) && techName != Constants.futureTech -> researchedTechColor
                // if we're here to pick a free tech, show the current tech like the rest of the researchables so it'll be obvious what we can pick
                tempTechsToResearch.firstOrNull() == techName && !freeTechPick -> currentTechColor
                researchableTechs.contains(techName) -> researchableTechColor
                tempTechsToResearch.contains(techName) -> queuedTechColor
                else -> Color.BLACK
            })

            if (civTech.isResearched(techName) && techName != Constants.futureTech) {
                techButton.text.color = colorFromRGB(154, 98, 16)
                techButton.color = researchedTechColor.cpy().darken(0.5f)
            }

            if (techName == selectedTech?.name && civTech.isResearched(techName)) {
                techButton.setButtonColor(colorFromRGB(230, 220, 114))
            }

            techButton.orderIndicator?.remove()
            if (tempTechsToResearch.contains(techName) && tempTechsToResearch.size > 1) {
                techButton.addOrderIndicator(tempTechsToResearch.indexOf(techName) + 1)
            }

            if (!civTech.isResearched(techName) || techName == Constants.futureTech) {
                techButton.turns.setText(turnsToTech[techName] + "${Fonts.turn}".tr())
            }

            techButton.text.setText(techName.tr())
        }

        addConnectingLines()
    }

    private fun addConnectingLines() {
        techTable.pack() // required for the table to have the button positions set, so topTable.stageToLocalCoordinates will be correct
        scrollPane.updateVisualScroll()

        for (line in lines) line.remove()
        lines.clear()

        for (eraLabel in eraLabels) {
            val coords = Vector2(0f, 0f)
            eraLabel.localToStageCoordinates(coords)
            techTable.stageToLocalCoordinates(coords)
            val line = ImageGetter.getLine(coords.x-1f, coords.y, coords.x-1f, coords.y - 1000f, 1f)
            line.color = Color.GRAY.apply { a = 0.7f }
            line.toBack()
            techTable.addActor(line)
            lines.add(line)
        }

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

                val lineColor = when {
                    civTech.isResearched(tech.name) && !tech.isContinuallyResearchable() -> Color.WHITE
                    civTech.isResearched(prerequisite) -> researchableTechColor
                    tempTechsToResearch.contains(tech.name) -> queuedTechColor
                    else -> Color.WHITE
                }

                if (techButtonCoords.y != prerequisiteCoords.y) {

                    val r = 6f

                    val deltaX = techButtonCoords.x - prerequisiteCoords.x
                    val deltaY = techButtonCoords.y - prerequisiteCoords.y
                    val halfLength = deltaX / 2f

                    val line = ImageGetter.getWhiteDot().apply {
                        width = halfLength - r
                        height = 2f
                        x = prerequisiteCoords.x
                        y = prerequisiteCoords.y - height / 2
                    }
                    val line1 = ImageGetter.getWhiteDot().apply {
                        width = halfLength - r
                        height = 2f
                        x = techButtonCoords.x - halfLength + r
                        y = techButtonCoords.y - height / 2
                    }
                    val line2 = ImageGetter.getWhiteDot().apply {
                        width = 2f
                        height = abs(deltaY) - 2*r
                        x = techButtonCoords.x - halfLength - width / 2
                        y = techButtonCoords.y + (if (deltaY > 0f) -height - r else r + 1f)
                    }

                    var line3: Image?
                    var line4: Image?

                    if (deltaY < 0) {
                        line3 = ImageGetter.getLine(line2.x, line2.y + line2.height,
                            line.x + line.width, line.y+1f, 2f)
                        line4 = ImageGetter.getLine(line2.x, line2.y, line1.x, line1.y+1f, 2f)
                    } else {
                        line3 = ImageGetter.getLine(line2.x+1f, line2.y,
                            line.x + line.width, line.y, 2f)
                        line4 = ImageGetter.getLine(line2.x, line2.y + line2.height-1f,
                            line1.x, line1.y+1f, 2f)
                    }

                    line.color = lineColor
                    line1.color = lineColor
                    line2.color = lineColor
                    line3.color = lineColor
                    line4.color = lineColor

                    if (tempTechsToResearch.contains(tech.name)) {
                        line1.toFront()
                        line2.toFront()
                        line3.toFront()
                        line4.toFront()
                        line.toFront()
                    }

                    techTable.addActor(line)
                    techTable.addActor(line1)
                    techTable.addActor(line2)
                    techTable.addActor(line3)
                    techTable.addActor(line4)

                    lines.add(line)
                    lines.add(line1)
                    lines.add(line2)
                    lines.add(line3)
                    lines.add(line4)

                } else {

                    val line = ImageGetter.getWhiteDot().apply {
                        width = techButtonCoords.x - prerequisiteCoords.x
                        height = 2f
                        x = prerequisiteCoords.x
                        y = prerequisiteCoords.y - height / 2
                    }
                    line.color = lineColor

                    if (tempTechsToResearch.contains(tech.name)) line.toFront()

                    techTable.addActor(line)
                    lines.add(line)

                }
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

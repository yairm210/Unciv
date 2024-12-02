package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.TechManager
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Concurrency
import kotlin.math.abs


class TechPickerScreen(
    internal val civInfo: Civilization,
    centerOnTech: Technology? = null,
) : PickerScreen() {

    private val freeTechPick: Boolean = civInfo.tech.freeTechs != 0
    private val ruleset = civInfo.gameInfo.ruleset
    private var techNameToButton = HashMap<String, TechButton>()
    private var selectedTech: Technology? = null
    private var civTech: TechManager = civInfo.tech
    private var tempTechsToResearch: ArrayList<String>
    private var lines = NonTransformGroup()
    private var orderIndicators = NonTransformGroup()
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
    private var researchableTechs = ruleset.technologies.keys
            .filter { civTech.canBeResearched(it) }.toHashSet()

    private val currentTechColor = skinStrings.getUIColor("TechPickerScreen/CurrentTechColor", colorFromRGB(72, 147, 175))
    private val researchedTechColor = skinStrings.getUIColor("TechPickerScreen/ResearchedTechColor", colorFromRGB(255, 215, 0))
    private val researchableTechColor = skinStrings.getUIColor("TechPickerScreen/ResearchableTechColor", colorFromRGB(28, 170, 0))
    private val queuedTechColor = skinStrings.getUIColor("TechPickerScreen/QueuedTechColor", colorFromRGB(7*2, 46*2, 43*2))
    private val researchedFutureTechColor = skinStrings.getUIColor("TechPickerScreen/ResearchedFutureTechColor", colorFromRGB(127, 50, 0))

    private val turnsToTech = ruleset.technologies.values.associateBy({ it.name }, { civTech.turnsToTech(it.name) })

    init {
        setDefaultCloseAction()
        scrollPane.setOverscroll(false, false)

        descriptionLabel.onClick {
            if (selectedTech != null)
                openCivilopedia(selectedTech!!.makeLink())
        }

        tempTechsToResearch = ArrayList(civTech.techsToResearch)

        createTechTable()
        setButtonsInfo()
        techTable.addActor(lines)
        techTable.addActor(orderIndicators)
        topTable.add(techTable)
        techTable.background = skinStrings.getUiBackground("TechPickerScreen/Background", tintColor = skinStrings.skinConfig.clearColor)
        pickerPane.bottomTable.background = skinStrings.getUiBackground("TechPickerScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        
        rightSideButton.setText(if (freeTechPick) "Pick a free tech".tr() else "Pick a tech".tr())
        rightSideButton.onClick(UncivSound.Paper) { tryExit() }

        // per default show current/recent technology,
        // and possibly select it to show description,
        // which is very helpful when just discovered and clicking the notification
        val tech = centerOnTech ?: civInfo.tech.currentTechnology()
        if (tech != null) {
            // select only if there it doesn't mess up tempTechsToResearch
            if (civInfo.tech.isResearched(tech.name) || civInfo.tech.techsToResearch.size <= 1)
                selectTechnology(tech, queue = false, center = true)
            else centerOnTechnology(tech)
        } else {
            // center on any possible technology which is ready for the research right now
            val firstAvailable = researchableTechs.firstOrNull()
            val firstAvailableTech = ruleset.technologies[firstAvailable]
            if (firstAvailableTech != null)
                centerOnTechnology(firstAvailableTech)
        }
    }

    override fun getCivilopediaRuleset() = ruleset


    private fun tryExit() {
        if (freeTechPick) {
            val freeTech = selectedTech!!.name
            // More evil people fast-clicking to cheat - #4977
            if (!researchableTechs.contains(freeTech)) return
            civTech.getFreeTechnology(selectedTech!!.name)
        }
        else civTech.techsToResearch = tempTechsToResearch

        civTech.updateResearchProgress()

        game.settings.addCompletedTutorialTask("Pick technology")

        game.popScreen()
    }

    private fun createTechTable() {

        for (label in eraLabels) label.remove()
        eraLabels.clear()

        val allTechs = ruleset.technologies.values
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
                ruleset.eras[era]!!.eraNumber < civTech.era.eraNumber -> colorFromRGB(255, 175, 0)
                else -> Color.BLACK.cpy()
            }

            val table1 = Table().pad(1f)
            val table2 = Table()

            table1.background = skinStrings.getUiBackground("General/Border", tintColor = Color.WHITE)
            table2.background = skinStrings.getUiBackground("General/Border", tintColor = color)

            val label = era.toLabel().apply {
                setAlignment(Align.center)
                if (ruleset.eras[era]!!.eraNumber < civTech.era.eraNumber)
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

                val table = Table().pad(2f).padRight(60f).padLeft(20f)
                if (rowIndex == 0)
                    table.padTop(7f)

                if (erasNamesToColumns[civTech.era.name]?.contains(columnIndex) == true)
                    table.background = skinStrings.getUiBackground("TechPickerScreen/Background", tintColor = queuedTechColor.darken(0.5f))

                if (tech == null) {
                    techTable.add(table).fill()
                } else {
                    val techButton = TechButton(tech.name, civTech, false)
                    table.add(techButton)
                    techNameToButton[tech.name] = techButton
                    techButton.onClick { selectTechnology(tech, queue = false, center = false) }
                    techButton.onRightClick { selectTechnology(tech, queue = true, center = false) }
                    techButton.onDoubleClick(UncivSound.Paper) { tryExit() }
                    techTable.add(table).fillX()
                }
            }
        }
    }

    private fun setButtonsInfo() {
        for ((techName, techButton) in techNameToButton) {
            val isResearched = civTech.isResearched(techName)
            techButton.setButtonColor(when {
                isResearched && techName != Constants.futureTech -> researchedTechColor
                isResearched -> researchedFutureTechColor
                // if we're here to pick a free tech, show the current tech like the rest of the researchables so it'll be obvious what we can pick
                tempTechsToResearch.firstOrNull() == techName && !freeTechPick -> currentTechColor
                researchableTechs.contains(techName) -> researchableTechColor
                tempTechsToResearch.contains(techName) -> queuedTechColor
                else -> Color.BLACK.cpy()
            })

            if (isResearched && techName != Constants.futureTech) {
                techButton.text.color = colorFromRGB(154, 98, 16)
            }

            if (!isResearched || techName == Constants.futureTech) {
                techButton.turns.setText(turnsToTech[techName] + "${Fonts.turn}".tr())
            }

            techButton.text.setText(techName.tr(true))
        }

        addConnectingLines()

        addOrderIndicators()
    }

    private fun addConnectingLines() {
        techTable.pack() // required for the table to have the button positions set, so topTable.stageToLocalCoordinates will be correct
        scrollPane.updateVisualScroll()

        lines.clear()

        for (eraLabel in eraLabels) {
            val coords = Vector2(0f, 0f)
            eraLabel.localToStageCoordinates(coords)
            techTable.stageToLocalCoordinates(coords)
            val line = ImageGetter.getLine(coords.x-1f, coords.y, coords.x-1f, coords.y - 1000f, 1f)
            line.color = Color.GRAY.cpy().apply { a = 0.6f }
            line.toBack()
            lines.addActor(line)
        }

        for (tech in ruleset.technologies.values) {
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
                    civTech.isResearched(tech.name) && !tech.isContinuallyResearchable() -> Color.WHITE.cpy()
                    civTech.isResearched(prerequisite) -> researchableTechColor
                    tempTechsToResearch.contains(tech.name) -> currentTechColor
                    else -> Color.WHITE.cpy()
                }

                val lineSize = when {
                    tempTechsToResearch.contains(tech.name) && !civTech.isResearched(prerequisite) -> 4f
                    else -> 2f
                }

                if (techButtonCoords.y != prerequisiteCoords.y) {

                    val r = 6f

                    val deltaX = techButtonCoords.x - prerequisiteCoords.x
                    val deltaY = techButtonCoords.y - prerequisiteCoords.y
                    val halfLength = deltaX / 2f

                    val line = ImageGetter.getWhiteDot().apply {
                        width = halfLength - r - lineSize/2
                        height = lineSize
                        x = prerequisiteCoords.x
                        y = prerequisiteCoords.y - lineSize / 2
                    }
                    val line1 = ImageGetter.getWhiteDot().apply {
                        width = halfLength - r - lineSize/2
                        height = lineSize
                        x = techButtonCoords.x - width
                        y = techButtonCoords.y - lineSize / 2
                    }
                    val line2 = ImageGetter.getWhiteDot().apply {
                        width = lineSize
                        height = abs(deltaY) - 2*r - lineSize
                        x = techButtonCoords.x - halfLength - lineSize / 2
                        y = techButtonCoords.y + (if (deltaY > 0f) -height-r-lineSize/2 else r+lineSize/2)
                    }

                    var line3: Image?
                    var line4: Image?

                    if (deltaY < 0) {
                        /* -\ */ line3 = ImageGetter.getLine(line2.x+lineSize/2+0.3f, line2.y + line2.height-lineSize/2,line.x + line.width-lineSize/2, line.y+lineSize/2+0.3f, lineSize)
                        /* \- */ line4 = ImageGetter.getLine(line2.x+lineSize/2-0.3f, line2.y+lineSize/2, line1.x+lineSize/2, line1.y+lineSize/2-0.3f, lineSize)
                    } else {
                        /* -/ */ line3 = ImageGetter.getLine(line2.x+lineSize/2+0.3f, line2.y+lineSize/2, line.x + line.width-lineSize/2, line.y+lineSize/2-0.3f, lineSize)
                        /* /- */ line4 = ImageGetter.getLine(line2.x+lineSize/2-0.3f, line2.y + line2.height-lineSize/2, line1.x+lineSize/2, line1.y+lineSize/2+0.3f, lineSize)
                    }

                    line.color = lineColor
                    line1.color = lineColor
                    line2.color = lineColor
                    line3.color = lineColor
                    line4.color = lineColor

                    lines.addActor(line)
                    lines.addActor(line1)
                    lines.addActor(line2)
                    lines.addActor(line3)
                    lines.addActor(line4)

                } else {

                    val line = ImageGetter.getWhiteDot().apply {
                        width = techButtonCoords.x - prerequisiteCoords.x
                        height = lineSize
                        x = prerequisiteCoords.x
                        y = prerequisiteCoords.y - lineSize / 2
                    }
                    line.color = lineColor

                    lines.addActor(line)
                }
            }
        }

        lines.children.filter { it.color == currentTechColor && it.color != Color.WHITE.cpy() }
            .forEach { it.toFront() }
    }

    private fun addOrderIndicators() {
        orderIndicators.clear()
        for ((techName, techButton) in techNameToButton) {
            val techButtonCoords = Vector2(0f, techButton.height / 2)
            techButton.localToStageCoordinates(techButtonCoords)
            techTable.stageToLocalCoordinates(techButtonCoords)
            if (tempTechsToResearch.contains(techName) && tempTechsToResearch.size > 1) {
                val index = tempTechsToResearch.indexOf(techName) + 1
                val orderIndicator = index.tr().toLabel(fontSize = 18)
                    .apply { setAlignment(Align.center) }
                    .surroundWithCircle(28f, color = skinStrings.skinConfig.baseColor)
                    .surroundWithCircle(30f,false)
                    .apply { setPosition(techButtonCoords.x - width, techButtonCoords.y - height / 2) }
                orderIndicators.addActor(orderIndicator)
            }
        }
        orderIndicators.toFront()
    }

    private fun selectTechnology(tech: Technology?, queue: Boolean = false, center: Boolean = false, switchFromWorldScreen: Boolean = true) {

        val previousSelectedTech = selectedTech
        selectedTech = tech
        descriptionLabel.setText(tech?.getDescription(civInfo))

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

        if (!GUI.isAllowedChangeState()) {
            rightSideButton.disable()
            return
        }

        val pathToTech = civTech.getRequiredTechsToDestination(tech)
        for (requiredTech in pathToTech) {
            for (unique in requiredTech.uniqueObjects
                .filter { it.type == UniqueType.OnlyAvailable && !it.conditionalsApply(civInfo.state) }) {
                rightSideButton.setText(unique.getDisplayText().tr())
                rightSideButton.disable()
                return
            }
        }

        if (queue){
            for (pathTech in pathToTech) {
                if (pathTech.name !in tempTechsToResearch) {
                    tempTechsToResearch.add(pathTech.name)
                }
            }
        }else{
            tempTechsToResearch.clear()
            tempTechsToResearch.addAll(pathToTech.map { it.name })
        }

        if (tempTechsToResearch.any()) {
            val label = "Research [${tempTechsToResearch[0]}]".tr()
            val techProgression = getTechProgressLabel(tempTechsToResearch)
            pick("${label}\n${techProgression}")
        } else {
            rightSideButton.setText("Unavailable".tr())
            rightSideButton.disable()
        }
        setButtonsInfo()
    }

    private fun getTechProgressLabel(techs: List<String>): String {
        val progress = techs.sumOf { tech -> civTech.researchOfTech(tech) } + civTech.getOverflowScience(techs.first())
        val techCost = techs.sumOf { tech -> civInfo.tech.costOfTech(tech) }
        return "(${progress}/${techCost})"
    }

    private fun centerOnTechnology(tech: Technology) {
        Concurrency.runOnGLThread {
            techNameToButton[tech.name]?.parent?.let {
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

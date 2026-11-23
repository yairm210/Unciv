package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.InternationalProject
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.view.CivView
import kotlin.math.min

class InternationalProjectsOverviewTab(
    viewingPlayer: CivView,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    init {
        defaults().pad(20f).align(Align.top)
        top()
        update()
    }

    private fun update() {
        clear()
        val projects = gameInfo.internationalProjects.values.toList()
        if (projects.isEmpty()) {
            add("No international projects in this game.".toLabel()).row()
        } else {
            val mainTable = Table()
            mainTable.defaults().pad(15f).align(Align.center)

            for (project in projects) {
                val building = viewingPlayer.ruleset.buildings[project.name]
                mainTable.add(createProjectTable(project, building)).growX().maxWidth(750f).row()
            }
            add(mainTable).growX().align(Align.top)
        }
    }

    private fun createProjectTable(project: InternationalProject, building: Building?): Table {
        val projectTable = Table()
        //projectTable.background = BaseScreen.skinStrings.getUiBackground("CityScreen/CityStatsTable/InnerTable", tintColor = ImageGetter.CHARCOAL.cpy().apply { a = 0.85f })
        projectTable.defaults().pad(12f)

        // Header row: Icon + Name + Status
        val headerTable = Table()

        val nameLabel = project.name.toLabel(fontSize = Constants.headingFontSize)
        nameLabel.onClick {
            overviewScreen.openCivilopedia(building?.makeLink() ?: "Building/${project.name}")
        }
        headerTable.add(nameLabel).expandX().left()

        val statusText = if (project.isConstructed()) "Completed".tr() else "In Progress".tr()
        val statusLabel = statusText.toLabel(if (project.isConstructed()) Color.LIME else Color.YELLOW)
        headerTable.add(statusLabel).right()

        headerTable.addSeparator(Color.WHITE, height = 2f)

        projectTable.add(headerTable).growX().row()

        // Contributions Breakdown with individual bar charts and notches
        projectTable.add(createContributionsTable(project, building)).growX().padBottom(10f).row()

        projectTable.addSeparator(height = 2f)

        // Rewards / Effects
        if (building != null && building.uniqueObjects.isNotEmpty()) {
            projectTable.add(createRewardsTable(building)).growX().row()
        }

        return projectTable
    }

    private fun extractContributionGoals(building: Building): List<Float> {
        val goals = mutableListOf<Float>()
        for (unique in building.uniqueObjects) {
            if (unique.type == UniqueType.ConditionalInternationalProjectContributionAbove) {
                val amount = unique.params.firstOrNull()?.toFloatOrNull()
                if (amount != null) goals.add(amount)
            }
        }
        return goals
    }

    private fun addContributionRow(
        table: Table,
        labelName: String,
        amount: Int,
        color: Color,
        totalCost: Int,
        goals: List<Float>,
        labelColor: Color = Color.WHITE
    ) {
        val label = labelName.toLabel(labelColor, hideIcons = true)

        val civRow = Table()
        civRow.defaults().padRight(15f).align(Align.left)
        civRow.add(label).width(160f).left()

        val percentage = if (totalCost > 0) min(1f, amount.toFloat() / totalCost) else 0f
        val barWidth = 320f
        val progressBar = ImageGetter.ProgressBar(barWidth, 30f, false)
        progressBar.setBackground(ImageGetter.CHARCOAL.cpy().apply { a = 0.9f })
        progressBar.setProgress(color, percentage)
        progressBar.setLabel(Color.WHITE, "$amount / $totalCost")

        // Add notches for contribution goals
        for (goalPercent in goals) {
            val notch = ImageGetter.getWhiteDot()
            notch.color = Color.CYAN
            notch.setSize(20f, progressBar.height + 4f)
            val notchX = (goalPercent / 100f) * barWidth
            notch.setPosition(notchX, 0f)
            progressBar.addActor(notch)
        }

        civRow.add(progressBar).left().row()
        table.add(civRow).growX().left().row()
    }

    private fun createContributionsTable(project: InternationalProject, building: Building?): Table {
        val contributionsTable = Table()
        contributionsTable.defaults().pad(6f).align(Align.left)

        val totalCost = project.getCost()
        val goals = building?.let { extractContributionGoals(it) } ?: emptyList()

        val sortedContributions = project.contributions.entries.sortedByDescending { it.value }
        for ((civName, amount) in sortedContributions) {
            val civ = gameInfo.civilizations.firstOrNull { it.civName == civName }
            val civColor = civ?.nation?.getOuterColor() ?: Color.WHITE
            addContributionRow(contributionsTable, civName, amount, civColor, totalCost, goals)
        }

        val totalContributions = project.contributions.values.sum()
        addContributionRow(contributionsTable, "Total", totalContributions, Color.GOLD, totalCost, goals, Color.GOLD)

        return contributionsTable
    }

    private fun createRewardsTable(building: Building): Table {
        val uniquesTable = Table()
        uniquesTable.defaults().pad(3f).align(Align.left)
        uniquesTable.add("Rewards / Effects:".toLabel(Color.LIGHT_GRAY)).row()
        for (unique in building.uniqueObjects) {
            uniquesTable.add("• ${unique.text}".toLabel()).left().row()
        }
        return uniquesTable
    }
}

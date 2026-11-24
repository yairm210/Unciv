package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.InternationalProject
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.unique.Countables
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unique.Conditionals
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.*
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ShadowedLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.view.CivView
import kotlin.math.min

internal fun hasAvailableInternationalProjects(viewingPlayer: CivView): Boolean =
    viewingPlayer.getCiv().gameInfo.internationalProjects.values.any {
        it.isInternationalProjectAvailable(viewingPlayer)
    }

class InternationalProjectsOverviewTab(
    viewingPlayer: CivView,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    private val headerTable = Table()
    private val projectsTable = Table()

    override fun getFixedContent() = headerTable

    init {
        headerTable.apply {
            defaults().pad(5f)
            align(Align.top)
            add("International Projects".toLabel(fontSize = Constants.headingFontSize)).row()
            addSeparator()
        }

        projectsTable.defaults().pad(15f).align(Align.center)

        defaults().pad(5f)
        align(Align.top)
        add(projectsTable).growX()
        updateProjectsTable()
    }

    private fun updateProjectsTable() {
        projectsTable.clear()
        val projects = gameInfo.internationalProjects.values
            .filter { it.isInternationalProjectAvailable(viewingPlayer) }
            .toList()
        if (projects.isEmpty()) {
            projectsTable.add("No international projects in this game.".toLabel()).row()
        } else {
            for (project in projects) {
                val building = viewingPlayer.ruleset.buildings[project.name]
                projectsTable.add(createProjectTable(project, building)).growX().maxWidth(750f).row()
            }
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
            for (conditional in unique.modifiers) {
                if (conditional.type == UniqueType.ConditionalInternationalProjectContributionAbove) {
                    val parameter = conditional.params.firstOrNull() ?: continue
                    val amount = Countables.getCountableAmount(
                        parameter,
                        viewingPlayer.getCiv().state
                    ) ?: continue
                    goals.add(amount.toFloat().coerceIn(0f, 100f))
                }
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
        labelColor: Color = Color.WHITE,
        withNotches: Boolean = true
    ) {
        val label = labelName.toLabel(labelColor, hideIcons = true)

        val civRow = Table()
        civRow.defaults().padRight(15f).align(Align.left)
        civRow.add(label).width(160f).left()

        val percentage = if (totalCost > 0) min(1f, amount.toFloat() / totalCost) else 0f
        val barWidth = 320f
        val barHeight = 30f
        val progressBar = ImageGetter.ProgressBar(barWidth, barHeight,  false)
        progressBar.setBackground(ImageGetter.CHARCOAL.cpy().apply { a = 0.9f })
        progressBar.setProgress(color, percentage)
        val shadowedLabel = ShadowedLabel("$amount / $totalCost")
        progressBar.addActor(shadowedLabel)
        shadowedLabel.center(progressBar)
        shadowedLabel.toFront()

        // Add notches for contribution goals
        if (withNotches) {
            for (goalPercent in goals) {
                val notch = ImageGetter.getWhiteDot()
                notch.color = Color.GRAY
                val notchWidth = 4f
                val notchHeight = 8f
                notch.setSize(notchWidth, notchHeight)
                val notchX = (goalPercent / 100f) * barWidth - notchWidth / 2f
                notch.setPosition(notchX, barHeight - notchHeight / 2)
                progressBar.addActor(notch)
            }
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

        val totalContributions = project.getTotalContributions()
        addContributionRow(contributionsTable, "Total", totalContributions, Color.WHITE, totalCost, goals, withNotches = false)

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

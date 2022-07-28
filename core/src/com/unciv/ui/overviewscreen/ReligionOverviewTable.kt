package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.ReligionState
import com.unciv.models.Religion
import com.unciv.models.ruleset.Belief
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.disable
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setFontSize
import com.unciv.ui.utils.extensions.toLabel
import kotlin.math.max

class ReligionOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class ReligionTabPersistableData(
        var selectedReligion: String? = null
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = selectedReligion == null
    }
    override val persistableData = (persistedData as? ReligionTabPersistableData) ?: ReligionTabPersistableData()

    private val civStatsTable = Table()
    private val religionButtons = Table()
    private val religionButtonLabel = "Click an icon to see the stats of this religion".toLabel()
    private val statsTable = Table()
    private val beliefsTable = Table()

    override fun getFixedContent() = Table().apply {
            defaults().pad(5f)
            align(Align.top)

            civStatsTable.defaults().left().pad(5f)
            civStatsTable.addCivSpecificStats()
            add(civStatsTable).row()
            add(religionButtons).row()
            add(religionButtonLabel)
            addSeparator()
        }

    init {
        defaults().pad(5f)
        align(Align.top)
        loadReligionButtons()

        statsTable.defaults().left().pad(5f)
        beliefsTable.defaults().padBottom(20f)
        loadReligion(persistableData.selectedReligion)
        add(statsTable).row()
        add(beliefsTable).pad(20f)
    }

    private fun Table.addCivSpecificStats() {
        if (viewingPlayer.religionManager.canGenerateProphet()) {
            add("Minimal Faith required for\nthe next [great prophet equivalent]:"
                .fillPlaceholders(viewingPlayer.religionManager.getGreatProphetEquivalent()!!)
                .toLabel()
            )
            add(
                (viewingPlayer.religionManager.faithForNextGreatProphet() + 1)
                .toLabel()
            ).right().row()
        }

        add("Religions to be founded:".toLabel())
        add((viewingPlayer.religionManager.remainingFoundableReligions()).toLabel()).right().row()

        add("Religious status:".toLabel()).left()
        add(viewingPlayer.religionManager.religionState.toString().toLabel()).right().row()
    }

    private fun loadReligionButtons() {
        religionButtons.clear()
        val existingReligions: List<Religion> = gameInfo.civilizations.mapNotNull { it.religionManager.religion }
        for (religion in existingReligions) {
            val image = if (religion.isPantheon()) {
                if (viewingPlayer.knows(religion.foundingCivName) || viewingPlayer.civName == religion.foundingCivName)
                    ImageGetter.getNationIndicator(religion.getFounder().nation, 60f)
                else
                    ImageGetter.getRandomNationIndicator(60f)
            } else {
                    ImageGetter.getCircledReligionIcon(religion.getIconName(), 60f)
            }
            val button = Button(image, BaseScreen.skin)

            button.onClick {
                persistableData.selectedReligion = religion.name
                loadReligionButtons()
                loadReligion(religion)
            }

            if (persistableData.selectedReligion == religion.name)
                button.disable()

            religionButtons.add(button).pad(5f)
        }
    }

    override fun select(selection: String) {
        persistableData.selectedReligion = selection
        loadReligionButtons()  // so the icon is "highlighted"
        loadReligion(selection)
    }
    private fun loadReligion(religionName: String?) {
        if (religionName == null) return
        val religion = gameInfo.religions[religionName] ?: return
        loadReligion(religion)
    }
    private fun loadReligion(religion: Religion) {
        statsTable.clear()
        beliefsTable.clear()
        religionButtonLabel.setText(religion.getReligionDisplayName().tr())
        religionButtonLabel.setFontSize(Constants.headingFontSize)
        religionButtonLabel.color = Color.CORAL

        for (belief in religion.getAllBeliefsOrdered()) {
            beliefsTable.add(createBeliefDescription(belief)).row()
        }

        statsTable.add((if (religion.isPantheon()) "Pantheon Name:" else "Religion Name:").toLabel())
        statsTable.add(religion.getReligionDisplayName().toLabel()).right().row()
        statsTable.add("Founding Civ:".toLabel())
        val foundingCivName =
            if (viewingPlayer.knows(religion.foundingCivName) || viewingPlayer.civName == religion.foundingCivName)
                religion.foundingCivName
            else Constants.unknownNationName
        statsTable.add(foundingCivName.toLabel()).right().row()
        if (religion.isMajorReligion()) {
            val holyCity = gameInfo.getCities().firstOrNull { it.isHolyCityOf(religion.name) }
            if (holyCity != null) {
                statsTable.add("Holy City:".toLabel())
                val cityName =
                    if (viewingPlayer.exploredTiles.contains(holyCity.getCenterTile().position))
                        holyCity.name
                    else Constants.unknownNationName
                statsTable.add(cityName.toLabel()).right().row()
            }
        }
        val manager = religion.getFounder().religionManager
        statsTable.add("Cities following this religion:".toLabel())
        statsTable.add(manager.numberOfCitiesFollowingThisReligion().toLabel()).right().row()
        statsTable.add("Followers of this religion:".toLabel())
        statsTable.add(manager.numberOfFollowersFollowingThisReligion("in all cities").toLabel()).right().row()

        val minWidth = max(statsTable.minWidth, beliefsTable.minWidth) + 5

        statsTable.width = minWidth
        for (cell in beliefsTable.cells) {
            cell.minWidth(minWidth)
        }

        // If we're wider than the container set our ScrollPane to center us
        (parent as? ScrollPane)?.apply {
            layout()
            scrollX = maxX / 2
            updateVisualScroll()
        }
    }

    private fun createBeliefDescription(belief: Belief) =
        MarkupRenderer.render(
            belief.getCivilopediaTextLines(withHeader = true)
        ) {
            UncivGame.Current.pushScreen(CivilopediaScreen(gameInfo.ruleSet, link = it))
        }.apply {
            background = ImageGetter.getBackground(ImageGetter.getBlue())
        }
}

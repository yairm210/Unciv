package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import kotlin.math.roundToInt

class DiplomacyOverviewTab (
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    class DiplomacyTabPersistableData(
        var includeCityStates: Boolean = false
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = !includeCityStates
    }
    override val persistableData = (persistedData as? DiplomacyTabPersistableData) ?: DiplomacyTabPersistableData()

    // Widgets that are kept between updates
    private val fixedContent = Table()
    private val civTable = Table().apply {
        defaults().pad(5f)
        background = ImageGetter.getBackground(Color.BLACK)
    }
    val toggleCityStatesButton: TextButton = Constants.cityStates.toTextButton().apply {
        onClick {
            persistableData.includeCityStates = !persistableData.includeCityStates
            update()
        }
    }
    private val civTableScroll = AutoScrollPane(civTable).apply {
        setOverscroll(false, false)
    }
    private val floatingTable = Table().apply {
        add(toggleCityStatesButton).row()
        add(civTableScroll.addBorder(2f, Color.WHITE)).pad(10f)
    }

    // Reusable sequences for the Civilizations to display
    private var undefeatedCivs = sequenceOf<CivilizationInfo>()
    private var defeatedCivs = sequenceOf<CivilizationInfo>()

    private var relevantCivsCount = 0  // includes unknown civs
    private var showDiplomacyGroup = false
    private var portraitMode = false

    init {
        update()
    }

    override fun getFixedContent() = fixedContent

    // Refresh content and determine landscape/portrait layout
    private fun update() {
        relevantCivsCount = gameInfo.civilizations.count {
            !it.isSpectator() && !it.isBarbarian() && (persistableData.includeCityStates || !it.isCityState())
        }
        undefeatedCivs = sequenceOf(viewingPlayer) +
                viewingPlayer.getKnownCivsSorted(persistableData.includeCityStates)
        defeatedCivs = viewingPlayer.getKnownCivsSorted(persistableData.includeCityStates, true)
            .filter { it.isDefeated() }

        clear()
        fixedContent.clear()

        showDiplomacyGroup = undefeatedCivs.any { it != viewingPlayer }
        updateCivTable(2)
        portraitMode = !showDiplomacyGroup ||
                civTable.minWidth > overviewScreen.stage.width / 2 ||
                overviewScreen.isPortrait()
        val table = if (portraitMode) this else fixedContent

        if (showDiplomacyGroup) {
            val diplomacySize = (overviewScreen.stage.width - (if (portraitMode) 0f else civTable.minWidth))
                .coerceAtMost(overviewScreen.centerAreaHeight)
            val diplomacyGroup = DiplomacyGroup(undefeatedCivs, diplomacySize)
            table.add(diplomacyGroup).top()
        }

        if (portraitMode) {
            if (showDiplomacyGroup) table.row()
            val columns = 2 * (overviewScreen.stage.width / civTable.minWidth).roundToInt()
            if (columns > 2) {
                updateCivTable(columns)
                if (civTable.minWidth > overviewScreen.stage.width)
                    updateCivTable(columns - 2)
            }
        }

        table.add(floatingTable)
        toggleCityStatesButton.style = if (persistableData.includeCityStates) {
            BaseScreen.skin.get("negative", TextButtonStyle::class.java)
        } else {
            BaseScreen.skin.get("positive", TextButtonStyle::class.java)
        }
        civTableScroll.setScrollingDisabled(portraitMode, portraitMode)
    }

    private fun updateCivTable(columns: Int) = civTable.apply {
        clear()
        addTitleInfo(columns)
        addCivsCategory(columns, "alive", undefeatedCivs.filter { it != viewingPlayer })
        addCivsCategory(columns, "defeated", defeatedCivs)
        layout()
    }

    private fun getCivMiniTable(civInfo: CivilizationInfo): Table {
        val table = Table()
        table.add(ImageGetter.getNationIndicator(civInfo.nation, 25f)).pad(5f)
        table.add(civInfo.civName.toLabel()).left().padRight(10f)
        table.touchable = Touchable.enabled
        table.onClick {
            if (civInfo.isDefeated() || viewingPlayer.isSpectator() || civInfo == viewingPlayer) return@onClick
            UncivGame.Current.pushScreen(DiplomacyScreen(viewingPlayer, civInfo))
        }
        return table
    }

    private fun Table.addTitleInfo(columns: Int) {
        add("[$relevantCivsCount] Civilizations in the game".toLabel()).colspan(columns).row()
        add("Our Civilization:".toLabel()).colspan(columns).left().padLeft(10f).padTop(10f).row()
        add(getCivMiniTable(viewingPlayer)).left()
        add(viewingPlayer.calculateTotalScore().toInt().toLabel()).left().row()
        val turnsTillNextDiplomaticVote = viewingPlayer.getTurnsTillNextDiplomaticVote() ?: return
        add("Turns until the next\ndiplomacy victory vote: [$turnsTillNextDiplomaticVote]".toLabel()).colspan(columns).row()
    }

    private fun Table.addCivsCategory(columns: Int, aliveOrDefeated: String, civs: Sequence<CivilizationInfo>) {
        addSeparator()
        val count = civs.count()
        add("Known and $aliveOrDefeated ([$count])".toLabel())
            .pad(5f).colspan(columns).row()
        if (count == 0) return
        addSeparator()
        var currentColumn = 0
        var lastCivWasMajor = false
        fun advanceCols(delta: Int) {
            currentColumn += delta
            if (currentColumn >= columns) {
                row()
                currentColumn = 0
            }
            lastCivWasMajor = delta == 2
        }
        for (civ in civs) {
            if (lastCivWasMajor && civ.isCityState())
                advanceCols(columns)
            add(getCivMiniTable(civ)).left()
            if (civ.isCityState()) {
                advanceCols(1)
            } else {
                add(civ.calculateTotalScore().toInt().toLabel()).left()
                advanceCols(2)
            }
        }
    }

    /** This is the 'spider net'-like polygon showing one line per civ-civ relation */
    private class DiplomacyGroup(
        undefeatedCivs: Sequence<CivilizationInfo>,
        freeSize: Float
    ): Group() {
        private fun onCivClicked(civLines: HashMap<String, MutableSet<Actor>>, name: String) {
            // ignore the clicks on "dead" civilizations, and remember the selected one
            val selectedLines = civLines[name] ?: return

            // let's check whether lines of all civs are visible (except selected one)
            var atLeastOneLineVisible = false
            var allAreLinesInvisible = true
            for (lines in civLines.values) {
                // skip the civilization selected by user, and civilizations with no lines
                if (lines == selectedLines || lines.isEmpty()) continue

                val visibility = lines.first().isVisible
                atLeastOneLineVisible = atLeastOneLineVisible || visibility
                allAreLinesInvisible = allAreLinesInvisible && visibility

                // check whether both visible and invisible lines are present
                if (atLeastOneLineVisible && !allAreLinesInvisible) {
                    // invert visibility of the selected civ's lines
                    selectedLines.forEach { it.isVisible = !it.isVisible }
                    return
                }
            }

            if (selectedLines.first().isVisible) {
                // invert visibility of all lines except selected one
                civLines.filter { it.key != name }
                    .forEach { it.value.forEach { line -> line.isVisible = !line.isVisible } }
            } else {
                // it happens only when all are visible except selected one
                // invert visibility of the selected civ's lines
                selectedLines.forEach { it.isVisible = !it.isVisible }
            }
        }

        init {
            setSize(freeSize, freeSize)
            val civGroups = HashMap<String, Actor>()
            val civLines = HashMap<String, MutableSet<Actor>>()
            val civCount = undefeatedCivs.count()

            for ((i, civ) in undefeatedCivs.withIndex()) {
                val civGroup = ImageGetter.getNationIndicator(civ.nation, 30f)

                val vector = HexMath.getVectorForAngle(2 * Math.PI.toFloat() * i / civCount)
                civGroup.center(this)
                civGroup.moveBy(vector.x * freeSize / 2.25f, vector.y * freeSize / 2.25f)
                civGroup.touchable = Touchable.enabled
                civGroup.onClick {
                    onCivClicked(civLines, civ.civName)
                }
                civGroup.addTooltip(civ.civName, tipAlign = Align.bottomLeft)

                civGroups[civ.civName] = civGroup
                addActor(civGroup)
            }

            for (civ in undefeatedCivs)
                for (diplomacy in civ.diplomacy.values) {
                    if (diplomacy.otherCiv() !in undefeatedCivs) continue
                    val civGroup = civGroups[civ.civName]!!
                    val otherCivGroup = civGroups[diplomacy.otherCivName]!!

                    val statusLine = ImageGetter.getLine(
                        startX = civGroup.x + civGroup.width / 2,
                        startY = civGroup.y + civGroup.height / 2,
                        endX = otherCivGroup.x + otherCivGroup.width / 2,
                        endY = otherCivGroup.y + otherCivGroup.height / 2,
                        width = 2f)

                    statusLine.color = if (diplomacy.diplomaticStatus == DiplomaticStatus.War) Color.RED
                        else diplomacy.relationshipLevel().color

                    if (!civLines.containsKey(civ.civName)) civLines[civ.civName] = mutableSetOf()
                    civLines[civ.civName]!!.add(statusLine)

                    addActorAt(0, statusLine)
                }
        }
    }
}

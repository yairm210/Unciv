package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.map.HexMath
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.AutoScrollPane
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import com.unciv.ui.utils.extensions.addBorder
import com.unciv.ui.utils.extensions.addSeparator
import com.unciv.ui.utils.extensions.addSeparatorVertical
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import kotlin.math.roundToInt

class GlobalPoliticsOverviewTable (
    viewingPlayer: Civilization,
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
        background = BaseScreen.skinStrings.getUiBackground(
            "OverviewScreen/DiplomacyOverviewTab/CivTable",
            tintColor = Color.BLACK
        )
    }

    // Reusable sequences for the Civilizations to display
    private var undefeatedCivs = sequenceOf<Civilization>()
    private var defeatedCivs = sequenceOf<Civilization>()

    private var relevantCivsCount = 0  // includes unknown civs
    private var showDiplomacyGroup = false
    private var portraitMode = false


    init {
        updatePoliticsTable()
    }

    private fun updatePoliticsTable() {
        clear()
        getFixedContent().clear()

        val diagramButton = "Show diagram".toTextButton().onClick(::updateDiagram)

        add()
        addSeparatorVertical(Color.GRAY)
        add("Civilization Info".toLabel())
        addSeparatorVertical(Color.GRAY)
        add("Social policies".toLabel())
        addSeparatorVertical(Color.GRAY)
        add("Wonders".toLabel())
        addSeparatorVertical(Color.GRAY)
        add(Table().apply {
            add("Relations".toLabel()).row()
            add(diagramButton).pad(10f)
        })
        row()
        addSeparator(Color.GRAY)

        createGlobalPoliticsTable()
    }

    private fun createGlobalPoliticsTable() {
        val civilizations = mutableListOf<Civilization>()
        civilizations.add(viewingPlayer)
        civilizations.addAll(viewingPlayer.getKnownCivs())
        civilizations.removeAll(civilizations.filter { it.isBarbarian() || it.isCityState() || it.isSpectator() })
        for (civ in civilizations) {
            // civ image
            add(ImageGetter.getNationPortrait(civ.nation, 100f)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            // info about civ
            add(getCivInfoTable(civ)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            // policies
            add(getPoliciesTable(civ)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            // wonders
            add(getWondersOfCivTable(civ)).pad(20f)

            addSeparatorVertical(Color.GRAY)

            //politics
            add(getPoliticsOfCivTable(civ)).pad(20f)

            if (civilizations.indexOf(civ) != civilizations.lastIndex)
                addSeparator(Color.GRAY)
        }
    }

    private fun getCivInfoTable(civ: Civilization): Table {
        val civInfoTable = Table(skin)
        val leaderName = civ.nation.leaderName
        civInfoTable.add(leaderName.toLabel(fontSize = 30)).row()
        civInfoTable.add(civ.civName.toLabel()).row()
        civInfoTable.add(civ.tech.era.name.toLabel()).row()
        return civInfoTable
    }

    private fun getPoliciesTable(civ: Civilization): Table {
        val policiesTable = Table(skin)
        for (branch in civ.policies.branches)
            if (civ.policies.isAdopted(branch.name)) {
                val count = 1 + branch.policies.count {
                    it.policyBranchType != PolicyBranchType.BranchComplete &&
                    civ.policies.isAdopted(it.name)
                }
                policiesTable.add("[${branch.name}]: $count".toLabel()).row()
            }
        return policiesTable
    }

    private fun getWondersOfCivTable(civ: Civilization): Table {
        val wonderTable = Table(skin)
        val wonderInfo = WonderInfo()
        val allWorldWonders = wonderInfo.collectInfo(viewingPlayer)

        for (wonder in allWorldWonders) {
            if (wonder.civ?.civName == civ.civName) {
                val wonderName = wonder.name.toLabel()
                if (wonder.location != null) {
                    wonderName.onClick {
                        val worldScreen = UncivGame.Current.resetToWorldScreen()
                        worldScreen.mapHolder.setCenterPosition(wonder.location.position)
                    }
                }
                wonderTable.add(wonderName).row()
            }
        }

        return wonderTable
    }

    private fun getCivName(otherciv: Civilization): String {
        if (viewingPlayer.knows(otherciv)){
            return otherciv.civName
        }
        return "an unknown civilization"
    }

    private fun getPoliticsOfCivTable(civ: Civilization): Table {
        val politicsTable = Table(skin)

        // wars
        for (otherCiv in civ.getKnownCivs()) {
            if (!viewingPlayer.knows(civ))
                continue

            if(civ.isAtWarWith(otherCiv)) {
                println(getCivName(otherCiv))
                val warText = "At war with [${getCivName(otherCiv)}]".toLabel()
                warText.color = Color.RED
                politicsTable.add(warText).row()
            }
        }
        politicsTable.row()

        // declaration of friendships
        for (otherCiv in civ.getKnownCivs()) {
            if (!viewingPlayer.knows(otherCiv))
                continue

            if(civ.diplomacy[otherCiv.civName]?.hasFlag(DiplomacyFlags.DeclarationOfFriendship) == true) {
                val friendText = "Friends with [${getCivName(otherCiv)}]".toLabel()
                friendText.color = Color.GREEN
                val turnsLeftText = " (${civ.diplomacy[otherCiv.civName]?.getFlag(DiplomacyFlags.DeclarationOfFriendship)} ${Fonts.turn})".toLabel()
                politicsTable.add(friendText)
                politicsTable.add(turnsLeftText).row()
            }
        }
        politicsTable.row()

        // denounced civs
        for (otherCiv in civ.getKnownCivs()) {
            if (!viewingPlayer.knows(otherCiv))
                continue

            if(civ.diplomacy[otherCiv.civName]?.hasFlag(DiplomacyFlags.Denunciation) == true) {
                val denouncedText = "Denounced [${getCivName(otherCiv)}]".toLabel()
                denouncedText.color = Color.RED
                val turnsLeftText = "(${civ.diplomacy[otherCiv.civName]?.getFlag(DiplomacyFlags.Denunciation)} ${Fonts.turn})".toLabel()
                politicsTable.add(denouncedText)
                politicsTable.add(turnsLeftText).row()
            }
        }
        politicsTable.row()

        //allied CS
        for (cityState in gameInfo.getAliveCityStates()) {
            if (!viewingPlayer.knows(cityState))
                continue

            if (cityState.diplomacy[civ.civName]?.relationshipLevel() == RelationshipLevel.Ally) {
                val alliedText = "Allied with [${getCivName(cityState)}]".toLabel()
                alliedText.color = Color.GREEN
                politicsTable.add(alliedText).row()
            }
        }

        return politicsTable
    }

    override fun getFixedContent() = fixedContent

    // Refresh content and determine landscape/portrait layout
    private fun updateDiagram() {
        val politicsButton = "Show global politics".toTextButton().onClick(::updatePoliticsTable)

        val toggleCityStatesButton: TextButton = Constants.cityStates.toTextButton().apply {
            onClick {
                persistableData.includeCityStates = !persistableData.includeCityStates
                updateDiagram()
            }
        }

        val civTableScroll = AutoScrollPane(civTable).apply {
            setOverscroll(false, false)
        }
        val floatingTable = Table().apply {
            add(toggleCityStatesButton).pad(10f).row()
            add(politicsButton).row()
            add(civTableScroll.addBorder(2f, Color.WHITE)).pad(10f)
        }

        relevantCivsCount = gameInfo.civilizations.count {
            !it.isSpectator() && !it.isBarbarian() && (persistableData.includeCityStates || !it.isCityState())
        }
        undefeatedCivs = sequenceOf(viewingPlayer) +
                viewingPlayer.diplomacyFunctions.getKnownCivsSorted(persistableData.includeCityStates)
        defeatedCivs = viewingPlayer.diplomacyFunctions.getKnownCivsSorted(persistableData.includeCityStates, true)
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
            BaseScreen.skin.get("negative", TextButton.TextButtonStyle::class.java)
        } else {
            BaseScreen.skin.get("positive", TextButton.TextButtonStyle::class.java)
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

    private fun getCivMiniTable(civInfo: Civilization): Table {
        val table = Table()
        table.add(ImageGetter.getNationPortrait(civInfo.nation, 25f)).pad(5f)
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

    private fun Table.addCivsCategory(columns: Int, aliveOrDefeated: String, civs: Sequence<Civilization>) {
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
        undefeatedCivs: Sequence<Civilization>,
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
                val civGroup = ImageGetter.getNationPortrait(civ.nation, 30f)

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

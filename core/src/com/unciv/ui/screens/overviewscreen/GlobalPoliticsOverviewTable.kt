package com.unciv.ui.screens.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addBorder
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.equalizeColumns
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt

class GlobalPoliticsOverviewTable(
    viewingPlayer: Civilization,
    overviewScreen: EmpireOverviewScreen,
    persistedData: EmpireOverviewTabPersistableData? = null
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {

    class DiplomacyTabPersistableData(
        var showDiagram: Boolean = false,
        var includeCityStates: Boolean = false
    ) : EmpireOverviewTabPersistableData() {
        override fun isEmpty() = !showDiagram && !includeCityStates
    }
    override val persistableData = (persistedData as? DiplomacyTabPersistableData) ?: DiplomacyTabPersistableData()

    // Widgets that are kept between updates
    private val fixedContent = Table()
    private val civTable = Table().apply {
        defaults().pad(5f)
        background = BaseScreen.skinStrings.getUiBackground(
            "OverviewScreen/DiplomacyOverviewTab/CivTable",
            tintColor = ImageGetter.CHARCOAL
        )
    }

    // Reusable sequences for the Civilizations to display
    private var undefeatedCivs = listOf<Civilization>()
    private var defeatedCivs = listOf<Civilization>()

    private var relevantCivsCount = "?"  // includes unknown civs if player allowed to know
    private var showDiplomacyGroup = false
    private var portraitMode = false


    init {
        top()
        if (persistableData.showDiagram) updateDiagram()
        else updatePoliticsTable()
    }

    override fun getFixedContent() = fixedContent

    //region Politics Table View

    private fun updatePoliticsTable() {
        persistableData.showDiagram = false
        createGlobalPoliticsHeader()
        createGlobalPoliticsTable()
        equalizeColumns(fixedContent, this)
    }

    /** Clears fixedContent and adds the header cells.
     *  Needs to stay matched to [createGlobalPoliticsTable].
     *
     *  9 Columns: 5 info, 4 separators. The first column gets an empty header, the content below is the civ image
     */
    private fun createGlobalPoliticsHeader() = fixedContent.run {
        val diagramButton = "Show diagram".toTextButton().onClick(::updateDiagram)

        clear()
        add()
        addSeparatorVertical(Color.GRAY)
        add("Civilization Info".toLabel())
        addSeparatorVertical(Color.GRAY)
        add("Social policies".toLabel())
        addSeparatorVertical(Color.GRAY)
        add("Wonders".toLabel())
        addSeparatorVertical(Color.GRAY)
        add(Table().apply {
            add("Relations".toLabel()).padTop(10f).row()
            add(diagramButton).pad(10f)
        })
        addSeparator(Color.GRAY)
    }

    /** Clears [EmpireOverviewTab]'s main Table and adds data columns/rows.
     *  Needs to stay matched to [createGlobalPoliticsHeader].
     */
    private fun createGlobalPoliticsTable() {
        clear()

        for (civ in sequenceOf(viewingPlayer) + viewingPlayer.diplomacyFunctions.getKnownCivsSorted(includeCityStates = false)) {
            // We already have a separator under the fixed header, we only need them here between rows.
            // This is also the replacement for calling row() explicitly
            if (cells.size > 0) addSeparator(Color.GRAY)

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
        }
    }

    private fun getCivInfoTable(civ: Civilization): Table {
        val civInfoTable = Table(skin)
        val leaderName = civ.nation.leaderName
        civInfoTable.add(leaderName.toLabel(fontSize = 30)).row()
        civInfoTable.add(civ.civName.toLabel(hideIcons = true)).row()
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
            if (wonder.civ == civ) {
                val wonderName = wonder.name.toLabel()
                if (wonder.location != null) {
                    wonderName.onClick {
                        val worldScreen = UncivGame.Current.resetToWorldScreen()
                        worldScreen.mapHolder.setCenterPosition(wonder.location.position)
                    }
                }
                wonderTable.add(wonderName).left().row()
            }
        }

        return wonderTable
    }

    @Readonly
    private fun getCivName(otherciv: Civilization): String {
        if (viewingPlayer.knows(otherciv) || otherciv == viewingPlayer) {
            return otherciv.civName
        }
        return "an unknown civilization"
    }

    private fun getPoliticsOfCivTable(civ: Civilization): Table {
        val politicsTable = Table(skin)

        if (!viewingPlayer.knows(civ) && civ != viewingPlayer)
            return politicsTable

        if (civ.isDefeated()) {
            politicsTable.add("{Defeated} ${Fonts.death}".toLabel())
            return politicsTable
        }

        // wars
        for (otherCiv in civ.getKnownCivs()) {
            if (civ.isAtWarWith(otherCiv)) {
                val warText = ColorMarkupLabel("At war with [${getCivName(otherCiv)}]", Color.RED)
                politicsTable.add(warText).row()
            }
        }
        politicsTable.row()

        // defensive pacts and declaration of friendships
        for (otherCiv in civ.getKnownCivs()) {
            if (civ.getDiplomacyManager(otherCiv)?.hasFlag(DiplomacyFlags.DefensivePact) == true) {
                val friendText = ColorMarkupLabel("Defensive pact with [${getCivName(otherCiv)}]", Color.CYAN)
                val turnsLeftText = " (${civ.getDiplomacyManager(otherCiv)?.getFlag(DiplomacyFlags.DefensivePact)} ${Fonts.turn})".toLabel()
                politicsTable.add(friendText)
                politicsTable.add(turnsLeftText).row()
            } else if (civ.getDiplomacyManager(otherCiv)?.hasFlag(DiplomacyFlags.DeclarationOfFriendship) == true) {
                val friendText = ColorMarkupLabel("Friends with [${getCivName(otherCiv)}]", Color.GREEN)
                val turnsLeftText = " (${civ.getDiplomacyManager(otherCiv)?.getFlag(DiplomacyFlags.DeclarationOfFriendship)} ${Fonts.turn})".toLabel()
                politicsTable.add(friendText)
                politicsTable.add(turnsLeftText).row()
            }
        }
        politicsTable.row()

        // denounced civs
        for (otherCiv in civ.getKnownCivs()) {
            if (civ.getDiplomacyManager(otherCiv)?.hasFlag(DiplomacyFlags.Denunciation) == true) {
                val denouncedText = ColorMarkupLabel("Denounced [${getCivName(otherCiv)}]", Color.RED)
                val turnsLeftText = "(${civ.getDiplomacyManager(otherCiv)?.getFlag(DiplomacyFlags.Denunciation)} ${Fonts.turn})".toLabel()
                politicsTable.add(denouncedText)
                politicsTable.add(turnsLeftText).row()
            }
        }
        politicsTable.row()

        //allied CS
        for (cityState in gameInfo.getAliveCityStates()) {
            if (cityState.getDiplomacyManager(civ)?.isRelationshipLevelEQ(RelationshipLevel.Ally) == true) {
                val alliedText = ColorMarkupLabel("Allied with [${getCivName(cityState)}]", Color.CYAN)
                politicsTable.add(alliedText).row()
            }
        }

        return politicsTable
    }

    //endregion
    //region Diagram View ("Ball of Yarn")

    // Refresh content and determine landscape/portrait layout
    private fun updateDiagram() {
        persistableData.showDiagram = true
        val politicsButton = "Show global politics".toTextButton().onClick {
            updatePoliticsTable()
            overviewScreen.resizePage(this)  // Or else the header stays curernt size, which with any non-empty diagram is most of the client area
        }

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

        val hideCivsCount = viewingPlayer.shouldHideCivCount() ||
            persistableData.includeCityStates && viewingPlayer.hideCityStateCount()
        relevantCivsCount = if (hideCivsCount) "?"
            else gameInfo.civilizations.count {
                !it.isSpectator() && !it.isBarbarian && (persistableData.includeCityStates || !it.isCityState)
            }.tr()
        undefeatedCivs = listOf(viewingPlayer) +
                viewingPlayer.diplomacyFunctions.getKnownCivsSorted(persistableData.includeCityStates)
        defeatedCivs = viewingPlayer.diplomacyFunctions.getKnownCivsSorted(persistableData.includeCityStates, true)
            .filter { it.isDefeated() }.toList()

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
            val diplomacyGroup = GlobalPoliticsDiagramGroup(undefeatedCivs, diplomacySize)
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

    /** Same as [Civilization.shouldHideCivCount] but for City-States instead of Major Civs */
    @Readonly
    private fun Civilization.hideCityStateCount(): Boolean {
        if (!gameInfo.gameParameters.randomNumberOfCityStates) return false
        val knownCivs = 1 + getKnownCivs().count { it.isCityState }
        if (knownCivs >= gameInfo.gameParameters.maxNumberOfCityStates) return false
        if (hasUnique(UniqueType.OneTimeRevealEntireMap)) return false
        return true
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
        table.add(civInfo.civName.toLabel(hideIcons = true)).left().padRight(10f)
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
        val scoreText = if (viewingPlayer.isDefeated()) Fonts.death.toString()
            else viewingPlayer.calculateTotalScore().toInt().tr()
        add(scoreText.toLabel()).left().row()
        val turnsTillNextDiplomaticVote = viewingPlayer.getTurnsTillNextDiplomaticVote() ?: return
        add("Turns until the next\ndiplomacy victory vote: [$turnsTillNextDiplomaticVote]".toLabel()).colspan(columns).row()
    }

    private fun Table.addCivsCategory(columns: Int, aliveOrDefeated: String, civs: List<Civilization>) {
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
            if (lastCivWasMajor && civ.isCityState)
                advanceCols(columns)
            add(getCivMiniTable(civ)).left()
            if (civ.isCityState) {
                advanceCols(1)
            } else {
                add(civ.calculateTotalScore().toInt().toLabel()).left()
                advanceCols(2)
            }
        }
    }

    //endregion
}

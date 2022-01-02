package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.*
import com.unciv.logic.civilization.diplomacy.*
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers.*
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.tilegroups.CityButton
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import kotlin.collections.ArrayList
import kotlin.math.floor
import kotlin.math.roundToInt
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class DiplomacyScreen(val viewingCiv:CivilizationInfo): BaseScreen() {

    private val leftSideTable = Table().apply { defaults().pad(10f) }
    private val rightSideTable = Table()

    private fun isNotPlayersTurn() = !UncivGame.Current.worldScreen.canChangeState

    init {
        onBackButtonClicked { UncivGame.Current.setWorldScreen() }
        val splitPane = SplitPane(ScrollPane(leftSideTable), rightSideTable, false, skin)
        splitPane.splitAmount = 0.2f

        updateLeftSideTable()

        splitPane.setFillParent(true)
        stage.addActor(splitPane)


        val closeButton = Constants.close.toTextButton()
        closeButton.onClick { UncivGame.Current.setWorldScreen() }
        closeButton.label.setFontSize(24)
        closeButton.labelCell.pad(10f)
        closeButton.pack()
        closeButton.y = stage.height - closeButton.height - 10
        closeButton.x =
            (stage.width * 0.2f - closeButton.width) / 2   // center, leftSideTable.width not known yet
        stage.addActor(closeButton) // This must come after the split pane so it will be above, that the button will be clickable
    }

    private fun updateLeftSideTable() {
        leftSideTable.clear()
        leftSideTable.add().padBottom(60f).row()  // room so the close button does not cover the first

        val civsToDisplay = viewingCiv.gameInfo.civilizations.asSequence()
            .filterNot {
                it.isDefeated() || it == viewingCiv || it.isBarbarian() || it.isSpectator() ||
                    !viewingCiv.knows(it)
            }
            .sortedWith(
                compareByDescending<CivilizationInfo>{ it.isMajorCiv() }
                    .thenBy (UncivGame.Current.settings.getCollatorFromLocale(), { it.civName.tr() })
            )

        for (civ in civsToDisplay) {

            val civIndicator = ImageGetter.getNationIndicator(civ.nation, 100f)

            val relationLevel = civ.getDiplomacyManager(viewingCiv).relationshipLevel()
            val relationshipIcon = if (civ.isCityState() && relationLevel == RelationshipLevel.Ally)
                ImageGetter.getImage("OtherIcons/Star")
                    .surroundWithCircle(size = 30f, color = relationLevel.color).apply { 
                        actor.color = Color.GOLD
                    }
            else
                ImageGetter.getCircle().apply {
                    color = if (viewingCiv.isAtWarWith(civ)) Color.RED else relationLevel.color
                    setSize(30f, 30f)
                }
            civIndicator.addActor(relationshipIcon)

            if (civ.isCityState()) {
                val innerColor = civ.gameInfo.ruleSet.nations[civ.civName]!!.getInnerColor()
                val typeIcon = ImageGetter.getImage(civ.cityStateType.icon)
                    .surroundWithCircle(size = 35f, color = innerColor).apply {
                        actor.color = Color.BLACK
                    }
                civIndicator.addActor(typeIcon)
                typeIcon.y = floor(civIndicator.height - typeIcon.height)
                typeIcon.x = floor(civIndicator.width - typeIcon.width)
            }

            if (civ.isCityState() && civ.questManager.haveQuestsFor(viewingCiv)) {
                val questIcon = ImageGetter.getImage("OtherIcons/Quest")
                    .surroundWithCircle(size = 30f, color = Color.GOLDENROD)
                civIndicator.addActor(questIcon)
                questIcon.x = floor(civIndicator.width - questIcon.width)
            }

            leftSideTable.add(civIndicator).row()

            civIndicator.onClick { updateRightSide(civ) }
        }
    }

    fun updateRightSide(otherCiv: CivilizationInfo) {
        rightSideTable.clear()
        if (otherCiv.isCityState()) rightSideTable.add(
            ScrollPane(getCityStateDiplomacyTable(otherCiv))
        )
        else rightSideTable.add(ScrollPane(getMajorCivDiplomacyTable(otherCiv)))
            .height(stage.height)
    }

    fun setTrade(civ: CivilizationInfo): TradeTable {
        rightSideTable.clear()
        val tradeTable = TradeTable(civ, this)
        rightSideTable.add(tradeTable)
        return tradeTable
    }

    private fun getCityStateDiplomacyTableHeader(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(2.5f)

        diplomacyTable.add(LeaderIntroTable(otherCiv)).padBottom(15f).row()

        diplomacyTable.add("{Type}:  {${otherCiv.cityStateType}}".toLabel()).row()
        diplomacyTable.add("{Personality}:  {${otherCiv.cityStatePersonality}}".toLabel()).row()

        if (otherCiv.detailedCivResources.any { it.resource.resourceType != ResourceType.Bonus }) {
            val resourcesTable = Table()
            resourcesTable.add("{Resources:}  ".toLabel()).padRight(10f)
            for (supplyList in otherCiv.detailedCivResources) {
                if (supplyList.resource.resourceType == ResourceType.Bonus)
                    continue
                val name = supplyList.resource.name
                val wrapper = Table()
                val image = ImageGetter.getResourceImage(name, 30f)
                wrapper.add(image).padRight(5f)
                wrapper.add(supplyList.amount.toLabel())
                resourcesTable.add(wrapper).padRight(20f)
                wrapper.addTooltip(name, 18f)
                wrapper.onClick {
                    val pedia = CivilopediaScreen(
                        UncivGame.Current.gameInfo.ruleSet,
                        this,
                        link = "Resource/$name"
                    )
                    UncivGame.Current.setScreen(pedia)
                }
            }
            diplomacyTable.add(resourcesTable).row()
        }
        diplomacyTable.row().padTop(15f)

        otherCiv.updateAllyCivForCityState()
        val ally = otherCiv.getAllyCiv()
        if (ally != null) {
            val allyString = "{Ally}: {$ally} {Influence}: ".tr() +
                    otherCiv.getDiplomacyManager(ally).influence.toString()
            diplomacyTable.add(allyString.toLabel()).row()
        }

        val protectors = otherCiv.getProtectorCivs()
        if (protectors.isNotEmpty()) {
            val protectorString = "{Protected by}: " + protectors.joinToString(", ") { it.civName }
            diplomacyTable.add(protectorString.toLabel()).row()
        }

        val nextLevelString = when {
            otherCivDiplomacyManager.influence.toInt() < 30 -> "Reach 30 for friendship."
            ally == viewingCiv.civName -> ""
            else -> "Reach highest influence above 60 for alliance."
        }
        diplomacyTable.add(getRelationshipTable(otherCivDiplomacyManager)).row()
        if (nextLevelString.isNotEmpty()) {
            diplomacyTable.add(nextLevelString.toLabel()).row()
        }
        diplomacyTable.row().padTop(15f)

        val eraInfo = viewingCiv.getEra()

        var friendBonusText = "{When Friends:} ".tr()
        val friendBonusObjects = eraInfo.getCityStateBonuses(otherCiv.cityStateType, RelationshipLevel.Friend)
        val friendBonusStrings = getAdjustedBonuses(friendBonusObjects)
        friendBonusText += friendBonusStrings.joinToString(separator = ", ") { it.tr() }

        var allyBonusText = "{When Allies:} ".tr()
        val allyBonusObjects = eraInfo.getCityStateBonuses(otherCiv.cityStateType, RelationshipLevel.Ally)
        val allyBonusStrings = getAdjustedBonuses(allyBonusObjects)
        allyBonusText += allyBonusStrings.joinToString(separator = ", ") { it.tr() }

        val relationLevel = otherCivDiplomacyManager.relationshipLevel()
        if (relationLevel >= RelationshipLevel.Friend) {
            // RelationshipChange = Ally -> Friend or Friend -> Favorable
            val turnsToRelationshipChange = otherCivDiplomacyManager.getTurnsToRelationshipChange()
            diplomacyTable.add("Relationship changes in another [$turnsToRelationshipChange] turns".toLabel())
                .row()
        }

        val friendBonusLabelColor = if (relationLevel >= RelationshipLevel.Friend) Color.GREEN else Color.GRAY
        val friendBonusLabel = friendBonusText.toLabel(friendBonusLabelColor)
            .apply { setAlignment(Align.center) }
        diplomacyTable.add(friendBonusLabel).row()

        val allyBonusLabelColor = if (relationLevel == RelationshipLevel.Ally) Color.GREEN else Color.GRAY
        val allyBonusLabel = allyBonusText.toLabel(allyBonusLabelColor)
            .apply { setAlignment(Align.center) }
        diplomacyTable.add(allyBonusLabel).row()

        if (otherCiv.cityStateUniqueUnit != null) {
            val unitName = otherCiv.cityStateUniqueUnit
            val techName = viewingCiv.gameInfo.ruleSet.units[otherCiv.cityStateUniqueUnit]!!.requiredTech
            diplomacyTable.add("[${otherCiv.civName}] is able to provide [${unitName}] once [${techName}] is researched.".toLabel(fontSize = 18)).row()
        }

        return diplomacyTable
    }

    /** Given a list of [bonuses], returns a list of pretty strings with updated values for Siam-like uniques
     *  Assumes that each bonus contains only one stat type */
    private fun getAdjustedBonuses(bonuses: List<Unique>): List<String> {
        val bonusStrings = ArrayList<String>()
        for (bonus in bonuses) {
            var improved = false
            for (unique in viewingCiv.getMatchingUniques("[]% [] from City-States")) {
                val boostAmount = unique.params[0].toPercent()
                val boostedStat = Stat.valueOf(unique.params[1])
                when (bonus.type) {
                    UniqueType.CityStateStatsPerTurn -> { // "Provides [stats] per turn"
                        if (bonus.stats[boostedStat] > 0) {
                            bonusStrings.add(
                                bonus.text.fillPlaceholders(
                                    (bonus.stats * boostAmount).toStringWithDecimals()))
                            improved = true
                        }
                    }
                    UniqueType.CityStateStatsPerCity -> { // "Provides [stats] [cityFilter]"
                        if (bonus.stats[boostedStat] > 0) {
                            bonusStrings.add(
                                bonus.text.fillPlaceholders(
                                    (bonus.stats * boostAmount).toStringWithDecimals(), bonus.params[1]))
                            improved = true
                        }
                    }
                    UniqueType.CityStateHappiness -> { // "Provides [amount] Happiness"
                        if (boostedStat == Stat.Happiness) {
                            bonusStrings.add(
                                bonus.text.fillPlaceholders(
                                    (bonus.params[0].toFloat() * boostAmount).toString().removeSuffix(".0")))
                            improved = true
                        }
                    }
                    else -> Unit  // To silence "exhaustive when" warning
                }
            }
            // No matching unique, add it unmodified
            if (!improved)
                bonusStrings.add(bonus.text)
        }
        return bonusStrings
    }

    private fun getCityStateDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)

        diplomacyTable.addSeparator()

        val giveGiftButton = "Give a Gift".toTextButton()
        giveGiftButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getGoldGiftTable(otherCiv)))
        }
        diplomacyTable.add(giveGiftButton).row()
        if (isNotPlayersTurn()) giveGiftButton.disable()

        val improveTileButton = getImproveTilesButton(otherCiv, otherCivDiplomacyManager)
        if (improveTileButton != null) diplomacyTable.add(improveTileButton).row()

        if (otherCivDiplomacyManager.diplomaticStatus == DiplomaticStatus.Protector) {
            val revokeProtectionButton = "Revoke Protection".toTextButton()
            revokeProtectionButton.onClick {
                YesNoPopup("Revoke protection for [${otherCiv.civName}]?", {
                    otherCiv.removeProtectorCiv(viewingCiv)
                    updateLeftSideTable()
                    updateRightSide(otherCiv)
                }, this).open()
            }
            diplomacyTable.add(revokeProtectionButton).row()
            if (isNotPlayersTurn() || !otherCiv.otherCivCanWithdrawProtection(viewingCiv)) revokeProtectionButton.disable()
        } else {
            val protectionButton = "Pledge to protect".toTextButton()
            protectionButton.onClick {
                YesNoPopup("Declare Protection of [${otherCiv.civName}]?", {
                    otherCiv.addProtectorCiv(viewingCiv)
                    updateLeftSideTable()
                    updateRightSide(otherCiv)
                }, this).open()
            }
            diplomacyTable.add(protectionButton).row()
            if (isNotPlayersTurn() || !otherCiv.otherCivCanPledgeProtection(viewingCiv)) protectionButton.disable()
        }

        val demandTributeButton = "Demand Tribute".toTextButton()
        demandTributeButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getDemandTributeTable(otherCiv)))
        }
        diplomacyTable.add(demandTributeButton).row()
        if (isNotPlayersTurn()) demandTributeButton.disable()

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)
        if (!viewingCiv.gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.diplomaticRelationshipsCannotChange)) {
            if (viewingCiv.isAtWarWith(otherCiv)) {
                val peaceButton = "Negotiate Peace".toTextButton()
                peaceButton.onClick {
                    YesNoPopup("Peace with [${otherCiv.civName}]?", {
                        val tradeLogic = TradeLogic(viewingCiv, otherCiv)
                        tradeLogic.currentTrade.ourOffers.add(
                            TradeOffer(
                                Constants.peaceTreaty,
                                TradeType.Treaty
                            )
                        )
                        tradeLogic.currentTrade.theirOffers.add(
                            TradeOffer(
                                Constants.peaceTreaty,
                                TradeType.Treaty
                            )
                        )
                        tradeLogic.acceptTrade()
                        updateLeftSideTable()
                        updateRightSide(otherCiv)
                    }, this).open()
                }
                diplomacyTable.add(peaceButton).row()
                val cityStatesAlly = otherCiv.getAllyCiv()
                val atWarWithItsAlly = viewingCiv.getKnownCivs()
                    .any { it.civName == cityStatesAlly && it.isAtWarWith(viewingCiv) }
                if (isNotPlayersTurn() || atWarWithItsAlly) peaceButton.disable()
            } else {
                val declareWarButton = getDeclareWarButton(diplomacyManager, otherCiv)
                if (isNotPlayersTurn()) declareWarButton.disable()
                diplomacyTable.add(declareWarButton).row()
            }
        }

        val diplomaticMarriageButton = getDiplomaticMarriageButton(otherCiv)
        if (diplomaticMarriageButton != null) diplomacyTable.add(diplomaticMarriageButton).row()

        for (assignedQuest in otherCiv.questManager.assignedQuests.filter { it.assignee == viewingCiv.civName }) {
            diplomacyTable.addSeparator()
            diplomacyTable.add(getQuestTable(assignedQuest)).row()
        }
        
        for (target in otherCiv.getKnownCivs().filter { otherCiv.questManager.warWithMajorActive(it) }) {
            diplomacyTable.addSeparator()
            diplomacyTable.add(getWarWithMajorTable(target, otherCiv)).row()
        }

        return diplomacyTable
    }

    private fun getImproveTilesButton(
        otherCiv: CivilizationInfo,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton? {
        if (otherCiv.cities.isEmpty()) return null
        val improvableResourceTiles = getImprovableResourceTiles(otherCiv)
        val improvements =
            otherCiv.gameInfo.ruleSet.tileImprovements.filter { it.value.turnsToBuild != 0 }
        var needsImprovements = false

        for (improvableTile in improvableResourceTiles)
            for (tileImprovement in improvements.values)
                if (improvableTile.canBuildImprovement(
                        tileImprovement,
                        otherCiv
                    ) && improvableTile.tileResource.improvement == tileImprovement.name
                )
                    needsImprovements = true

        if (!needsImprovements) return null


        val improveTileButton = "Gift Improvement".toTextButton()
        improveTileButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getImprovementGiftTable(otherCiv)))
        }


        if (isNotPlayersTurn() || otherCivDiplomacyManager.influence < 60 || !needsImprovements)
            improveTileButton.disable()
        return improveTileButton
    }

    private fun getDiplomaticMarriageButton(otherCiv: CivilizationInfo): TextButton? {
        if (!viewingCiv.hasUnique(UniqueType.CityStateCanBeBoughtForGold))
            return null

        val diplomaticMarriageButton =
            "Diplomatic Marriage ([${otherCiv.cityStateFunctions.getDiplomaticMarriageCost()}] Gold)".toTextButton()
        diplomaticMarriageButton.onClick {
            val newCities = otherCiv.cities
            otherCiv.cityStateFunctions.diplomaticMarriage(viewingCiv)
            UncivGame.Current.setWorldScreen() // The other civ will no longer exist
            for (city in newCities)
                viewingCiv.popupAlerts.add(PopupAlert(AlertType.DiplomaticMarriage, city.id))   // Player gets to choose between annex and puppet
        }
        if (isNotPlayersTurn() || !otherCiv.cityStateFunctions.canBeMarriedBy(viewingCiv)) diplomaticMarriageButton.disable()
        return diplomaticMarriageButton
    }

    private fun getGoldGiftTable(otherCiv: CivilizationInfo): Table {
        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)
        diplomacyTable.addSeparator()

        for (giftAmount in listOf(250, 500, 1000)) {
            val influenceAmount = otherCiv.cityStateFunctions.influenceGainedByGift(viewingCiv, giftAmount)
            val giftButton =
                "Gift [$giftAmount] gold (+[$influenceAmount] influence)".toTextButton()
            giftButton.onClick {
                otherCiv.receiveGoldGift(viewingCiv, giftAmount)
                updateLeftSideTable()
                updateRightSide(otherCiv)
            }
            diplomacyTable.add(giftButton).row()
            if (viewingCiv.gold < giftAmount || isNotPlayersTurn()) giftButton.disable()
        }

        val backButton = "Back".toTextButton()
        backButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(backButton)
        return diplomacyTable
    }

    fun getImprovableResourceTiles(otherCiv:CivilizationInfo) =  otherCiv.getCapital().getTiles()
        .filter { it.hasViewableResource(otherCiv) && it.tileResource.resourceType!=ResourceType.Bonus
                && it.tileResource.improvement != it.improvement }

    private fun getImprovementGiftTable(otherCiv: CivilizationInfo): Table {
        val improvementGiftTable = getCityStateDiplomacyTableHeader(otherCiv)
        improvementGiftTable.addSeparator()

        val improvableResourceTiles = getImprovableResourceTiles(otherCiv)
        val tileImprovements =
            otherCiv.gameInfo.ruleSet.tileImprovements

        for (improvableTile in improvableResourceTiles) {
            for (tileImprovement in tileImprovements.values) {
                if (improvableTile.tileResource.improvement == tileImprovement.name
                    && improvableTile.canBuildImprovement(tileImprovement, otherCiv)
                ) {
                    val improveTileButton =
                        "Build [${tileImprovement}] on [${improvableTile.tileResource}] (200 Gold)".toTextButton()
                    improveTileButton.onClick {
                        viewingCiv.addGold(-200)
                        improvableTile.stopWorkingOnImprovement()
                        improvableTile.improvement = tileImprovement.name
                        otherCiv.updateDetailedCivResources()
                        rightSideTable.clear()
                        rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
                    }
                    if (viewingCiv.gold < 200)
                        improveTileButton.disable()
                    improvementGiftTable.add(improveTileButton).row()
                }
            }
        }

        val backButton = "Back".toTextButton()
        backButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        improvementGiftTable.add(backButton)
        return improvementGiftTable

    }

    private fun getDemandTributeTable(otherCiv: CivilizationInfo): Table {
        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)
        diplomacyTable.addSeparator()
        diplomacyTable.add("Tribute Willingness".toLabel()).row()
        val modifierTable = Table()
        val tributeModifiers = otherCiv.cityStateFunctions.getTributeModifiers(viewingCiv, requireWholeList = true)
        for (item in tributeModifiers) {
            val color = if (item.value >= 0) Color.GREEN else Color.RED
            modifierTable.add(item.key.toLabel(color))
            modifierTable.add(item.value.toString().toLabel(color)).row()
        }
        modifierTable.add("Sum:".toLabel())
        modifierTable.add(tributeModifiers.values.sum().toLabel()).row()
        diplomacyTable.add(modifierTable).row()
        diplomacyTable.add("At least 0 to take gold, at least 30 and size 4 city for worker".toLabel()).row()
        diplomacyTable.addSeparator()

        val demandGoldButton = "Take [${otherCiv.cityStateFunctions.goldGainedByTribute()}] gold (-15 Influence)".toTextButton()
        demandGoldButton.onClick {
            otherCiv.cityStateFunctions.tributeGold(viewingCiv)
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandGoldButton).row()
        if (otherCiv.getTributeWillingness(viewingCiv, demandingWorker = false) < 0)   demandGoldButton.disable()

        val demandWorkerButton = "Take worker (-50 Influence)".toTextButton()
        demandWorkerButton.onClick {
            otherCiv.cityStateFunctions.tributeWorker(viewingCiv)
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandWorkerButton).row()
        if (otherCiv.getTributeWillingness(viewingCiv, demandingWorker = true) < 0)    demandWorkerButton.disable()

        val backButton = "Back".toTextButton()
        backButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(backButton)
        return diplomacyTable
    }

    private fun getQuestTable(assignedQuest: AssignedQuest): Table {
        val questTable = Table()
        questTable.defaults().pad(10f)

        val quest: Quest = viewingCiv.gameInfo.ruleSet.quests[assignedQuest.questName]!!
        val remainingTurns: Int = assignedQuest.getRemainingTurns()
        val title = if (quest.influence > 0)
            "[${quest.name}] (+[${quest.influence.toInt()}] influence)"
        else
            quest.name
        val description = assignedQuest.getDescription()

        questTable.add(title.toLabel(fontSize = 24)).row()
        questTable.add(description.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(stage.width / 2).row()
        if (quest.duration > 0)
            questTable.add("[${remainingTurns}] turns remaining".toLabel()).row()
        if (quest.isGlobal()) {
            val leaderString = viewingCiv.gameInfo.getCivilization(assignedQuest.assigner).questManager.getLeaderStringForQuest(assignedQuest.questName)
            if (leaderString != "")
                questTable.add(leaderString.toLabel()).row()
        }

        questTable.onClick {
            assignedQuest.onClickAction()
        }
        return questTable
    }

    private fun getWarWithMajorTable(target: CivilizationInfo, otherCiv: CivilizationInfo): Table {
        val warTable = Table()
        warTable.defaults().pad(10f)
        
        val title = "War against [${target.civName}]"
        val description = "We need you to help us defend against [${target.civName}]. Killing [${otherCiv.questManager.unitsToKill(target)}] of their military units would slow their offensive."
        val progress = if (viewingCiv.knows(target)) "Currently you have killed [${otherCiv.questManager.unitsKilledSoFar(target, viewingCiv)}] of their military units."
            else "You need to find them first!"

        warTable.add(title.toLabel(fontSize = 24)).row()
        warTable.add(description.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(stage.width / 2).row()
        warTable.add(progress.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(stage.width / 2).row()
  
        return warTable
    }

    private fun getMajorCivDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)

        val helloText = if (otherCivDiplomacyManager.relationshipLevel() <= RelationshipLevel.Enemy)
            otherCiv.nation.hateHello
        else otherCiv.nation.neutralHello
        val leaderIntroTable = LeaderIntroTable(otherCiv, helloText)
        diplomacyTable.add(leaderIntroTable).row()
        diplomacyTable.addSeparator()

        val diplomaticRelationshipsCanChange =
            !viewingCiv.gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.diplomaticRelationshipsCannotChange)

        if (!viewingCiv.isAtWarWith(otherCiv)) {
            val tradeButton = "Trade".toTextButton()
            tradeButton.onClick {
                setTrade(otherCiv).apply {
                    tradeLogic.ourAvailableOffers.apply { remove(firstOrNull { it.type == TradeType.Treaty }) }
                    tradeLogic.theirAvailableOffers.apply { remove(firstOrNull { it.type == TradeType.Treaty }) }
                    offerColumnsTable.update()
                }
            }
            diplomacyTable.add(tradeButton).row()
            if (isNotPlayersTurn()) tradeButton.disable()
        } else if (diplomaticRelationshipsCanChange) {
            val negotiatePeaceButton = "Negotiate Peace".toTextButton()
            negotiatePeaceButton.onClick {
                val tradeTable = setTrade(otherCiv)
                val peaceTreaty = TradeOffer(Constants.peaceTreaty, TradeType.Treaty)
                tradeTable.tradeLogic.currentTrade.theirOffers.add(peaceTreaty)
                tradeTable.tradeLogic.currentTrade.ourOffers.add(peaceTreaty)
                tradeTable.offerColumnsTable.update()
            }
            if (isNotPlayersTurn() || otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar)) {
                negotiatePeaceButton.disable() // Can't trade for 10 turns after war was declared
                if (otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar)) {
                    val turnsLeft = otherCivDiplomacyManager.getFlag(DiplomacyFlags.DeclaredWar)
                    negotiatePeaceButton.setText(negotiatePeaceButton.text.toString() + "\n$turnsLeft" + Fonts.turn)
                }
            }

            diplomacyTable.add(negotiatePeaceButton).row()
        }

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)

        if (!viewingCiv.isAtWarWith(otherCiv)) {
            if (!diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)) {
                val declareFriendshipButton =
                    "Offer Declaration of Friendship ([30] turns)".toTextButton()
                declareFriendshipButton.onClick {
                    otherCiv.popupAlerts.add(
                        PopupAlert(
                            AlertType.DeclarationOfFriendship,
                            viewingCiv.civName
                        )
                    )
                    declareFriendshipButton.disable()
                }
                diplomacyTable.add(declareFriendshipButton).row()
                if (isNotPlayersTurn() || otherCiv.popupAlerts
                        .any { it.type == AlertType.DeclarationOfFriendship && it.value == viewingCiv.civName }
                )
                    declareFriendshipButton.disable()
            }


            if (viewingCiv.canSignResearchAgreementsWith(otherCiv)) {
                val researchAgreementButton = "Research Agreement".toTextButton()

                val requiredGold = viewingCiv.getResearchAgreementCost()
                researchAgreementButton.onClick {
                    val tradeTable = setTrade(otherCiv)
                    val researchAgreement =
                        TradeOffer(Constants.researchAgreement, TradeType.Treaty, requiredGold)
                    val goldCostOfSignResearchAgreement =
                        TradeOffer("Gold".tr(), TradeType.Gold, -requiredGold)
                    tradeTable.tradeLogic.currentTrade.theirOffers.add(researchAgreement)
                    tradeTable.tradeLogic.ourAvailableOffers.add(researchAgreement)
                    tradeTable.tradeLogic.ourAvailableOffers.add(goldCostOfSignResearchAgreement)
                    tradeTable.tradeLogic.currentTrade.ourOffers.add(researchAgreement)
                    tradeTable.tradeLogic.theirAvailableOffers.add(researchAgreement)
                    tradeTable.tradeLogic.theirAvailableOffers.add(goldCostOfSignResearchAgreement)
                    tradeTable.offerColumnsTable.update()
                }
                if (isNotPlayersTurn()) researchAgreementButton.disable()

                diplomacyTable.add(researchAgreementButton).row()
            }

            if (!diplomacyManager.hasFlag(DiplomacyFlags.Denunciation)
                && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            ) {
                val denounceButton = "Denounce ([30] turns)".toTextButton()
                denounceButton.onClick {
                    YesNoPopup("Denounce [${otherCiv.civName}]?", {
                        diplomacyManager.denounce()
                        updateLeftSideTable()
                        setRightSideFlavorText(otherCiv, "We will remember this.", "Very well.")
                    }, this).open()
                }
                diplomacyTable.add(denounceButton).row()
                if (isNotPlayersTurn()) denounceButton.disable()
            }

            if (diplomaticRelationshipsCanChange) {
                val declareWarButton = getDeclareWarButton(diplomacyManager, otherCiv)
                diplomacyTable.add(declareWarButton).row()
                if (isNotPlayersTurn()) declareWarButton.disable()
            }
        }

        val demandsButton = "Demands".toTextButton()
        demandsButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(getDemandsTable(viewingCiv, otherCiv))
        }
        diplomacyTable.add(demandsButton).row()
        if (isNotPlayersTurn()) demandsButton.disable()

        if (!otherCiv.isPlayerCivilization()) { // human players make their own choices
            diplomacyTable.add(getRelationshipTable(otherCivDiplomacyManager)).row()
            diplomacyTable.add(getDiplomacyModifiersTable(otherCivDiplomacyManager)).row()
            val promisesTable = getPromisesTable(diplomacyManager, otherCivDiplomacyManager)
            if (promisesTable != null) diplomacyTable.add(promisesTable).row()
        }

        UncivGame.Current.musicController.chooseTrack(otherCiv.civName,
            MusicMood.peaceOrWar(viewingCiv.isAtWarWith(otherCiv)), MusicTrackChooserFlags.setSelectNation)

        return diplomacyTable
    }

    private fun getPromisesTable(
        diplomacyManager: DiplomacyManager,
        otherCivDiplomacyManager: DiplomacyManager
    ): Table? {
        val promisesTable = Table()

        // Not for (flag in DiplomacyFlags.values()) - all other flags should result in DiplomaticModifiers or stay internal?
        val flag = DiplomacyFlags.AgreedToNotSettleNearUs
        if (otherCivDiplomacyManager.hasFlag(flag)) {
            val text =
                "We promised not to settle near them ([${otherCivDiplomacyManager.getFlag(flag)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }
        if (diplomacyManager.hasFlag(flag)) {
            val text =
                "They promised not to settle near us ([${diplomacyManager.getFlag(flag)}] turns remaining)"
            promisesTable.add(text.toLabel(Color.LIGHT_GRAY)).row()
        }

        return if (promisesTable.cells.isEmpty) null else promisesTable
    }

    private fun getDiplomacyModifiersTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val diplomacyModifiersTable = Table()
        for (modifier in otherCivDiplomacyManager.diplomaticModifiers) {
            // Angry about attacked CS and destroyed CS do not stack
            if (modifier.key == AttackedProtectedMinor.name
                && otherCivDiplomacyManager.hasModifier(DestroyedProtectedMinor))
                continue

            var text = when (valueOf(modifier.key)) {
                DeclaredWarOnUs -> "You declared war on us!"
                WarMongerer -> "Your warmongering ways are unacceptable to us."
                LiberatedCity -> "We applaud your liberation of conquered cities!"
                CapturedOurCities -> "You have captured our cities!"
                YearsOfPeace -> "Years of peace have strengthened our relations."
                SharedEnemy -> "Our mutual military struggle brings us closer together."
                DeclarationOfFriendship -> "We have signed a public declaration of friendship"
                DeclaredFriendshipWithOurEnemies -> "You have declared friendship with our enemies!"
                DeclaredFriendshipWithOurAllies -> "You have declared friendship with our allies"
                OpenBorders -> "Our open borders have brought us closer together."
                BetrayedDeclarationOfFriendship -> "Your so-called 'friendship' is worth nothing."
                Denunciation -> "You have publicly denounced us!"
                DenouncedOurAllies -> "You have denounced our allies"
                DenouncedOurEnemies -> "You have denounced our enemies"
                BetrayedPromiseToNotSettleCitiesNearUs -> "You betrayed your promise to not settle cities near us"
                RefusedToNotSettleCitiesNearUs -> "You refused to stop settling cities near us"
                FulfilledPromiseToNotSettleCitiesNearUs -> "You fulfilled your promise to stop settling cities near us!"
                UnacceptableDemands -> "Your arrogant demands are in bad taste"
                UsedNuclearWeapons -> "Your use of nuclear weapons is disgusting!"
                StealingTerritory -> "You have stolen our lands!"
                GaveUsUnits -> "You gave us units!"
                DestroyedProtectedMinor -> "You destroyed City States that were under our protection!"
                AttackedProtectedMinor -> "You attacked City States that were under our protection!"
                BulliedProtectedMinor -> "You demanded tribute from City States that were under our protection!"
                SidedWithProtectedMinor -> "You sided with a City State over us"
                ReturnedCapturedUnits -> "You returned captured units to us"
            }
            text = text.tr() + " "
            if (modifier.value > 0) text += "+"
            text += modifier.value.roundToInt()
            val color = if (modifier.value < 0) Color.RED else Color.GREEN
            diplomacyModifiersTable.add(text.toLabel(color)).row()
        }
        return diplomacyModifiersTable
    }

    private fun getDemandsTable(viewingCiv: CivilizationInfo, otherCiv: CivilizationInfo): Table {
        val demandsTable = Table()
        demandsTable.defaults().pad(10f)

        val dontSettleCitiesButton = "Please don't settle new cities near us.".toTextButton()
        if (otherCiv.popupAlerts.any { it.type == AlertType.DemandToStopSettlingCitiesNear && it.value == viewingCiv.civName })
            dontSettleCitiesButton.disable()
        dontSettleCitiesButton.onClick {
            otherCiv.popupAlerts.add(
                PopupAlert(
                    AlertType.DemandToStopSettlingCitiesNear,
                    viewingCiv.civName
                )
            )
            dontSettleCitiesButton.disable()
        }
        demandsTable.add(dontSettleCitiesButton).row()

        demandsTable.add(Constants.close.toTextButton().onClick { updateRightSide(otherCiv) })
        return demandsTable
    }

    private fun getRelationshipTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val relationshipTable = Table()

        val opinionOfUs =
            if (otherCivDiplomacyManager.civInfo.isCityState()) otherCivDiplomacyManager.influence.toInt()
            else otherCivDiplomacyManager.opinionOfOtherCiv().toInt()

        relationshipTable.add("{Our relationship}: ".toLabel())
        val relationshipLevel = otherCivDiplomacyManager.relationshipLevel()
        val relationshipText = relationshipLevel.name.tr() + " ($opinionOfUs)"
        val relationshipColor = when (relationshipLevel) {
            RelationshipLevel.Neutral -> Color.WHITE
            RelationshipLevel.Favorable, RelationshipLevel.Friend,
            RelationshipLevel.Ally -> Color.GREEN
            RelationshipLevel.Afraid -> Color.YELLOW
            else -> Color.RED
        }

        relationshipTable.add(relationshipText.toLabel(relationshipColor)).row()
        if (otherCivDiplomacyManager.civInfo.isCityState())
            relationshipTable.add(
                CityButton.getInfluenceBar(
                    otherCivDiplomacyManager.influence,
                    otherCivDiplomacyManager.relationshipLevel(),
                    200f, 10f
                )
            ).colspan(2).pad(5f)
        return relationshipTable
    }

    private fun getDeclareWarButton(
        diplomacyManager: DiplomacyManager,
        otherCiv: CivilizationInfo
    ): TextButton {
        val declareWarButton = "Declare war".toTextButton()
        declareWarButton.color = Color.RED
        val turnsToPeaceTreaty = diplomacyManager.turnsToPeaceTreaty()
        if (turnsToPeaceTreaty > 0) {
            declareWarButton.disable()
            declareWarButton.setText(declareWarButton.text.toString() + " ($turnsToPeaceTreaty${Fonts.turn})")
        }
        declareWarButton.onClick {
            YesNoPopup("Declare war on [${otherCiv.civName}]?", {
                diplomacyManager.declareWar()
                setRightSideFlavorText(otherCiv, otherCiv.nation.attacked, "Very well.")
                updateLeftSideTable()
                UncivGame.Current.musicController.chooseTrack(otherCiv.civName, MusicMood.War, MusicTrackChooserFlags.setSpecific)
            }, this).open()
        }
        return declareWarButton
    }

    // response currently always gets "Very Well.", but that may expand in the future.
    @Suppress("SameParameterValue")
    private fun setRightSideFlavorText(
        otherCiv: CivilizationInfo,
        flavorText: String,
        response: String
    ) {
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        diplomacyTable.add(LeaderIntroTable(otherCiv))
        diplomacyTable.addSeparator()
        diplomacyTable.add(flavorText.toLabel()).row()

        val responseButton = response.toTextButton()
        val action = {
            keyPressDispatcher.remove(KeyCharAndCode.SPACE)
            updateRightSide(otherCiv)
        }
        responseButton.onClick(action)
        keyPressDispatcher[KeyCharAndCode.SPACE] = action
        diplomacyTable.add(responseButton)

        rightSideTable.clear()
        rightSideTable.add(diplomacyTable)
    }

}

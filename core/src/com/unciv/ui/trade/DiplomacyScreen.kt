package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.*
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers.*
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Quest
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.tilegroups.CityButton
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivTooltip.Companion.addTooltip
import kotlin.math.floor
import kotlin.math.roundToInt
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class DiplomacyScreen(val viewingCiv:CivilizationInfo):CameraStageBaseScreen() {

    private val leftSideTable = Table().apply { defaults().pad(10f) }
    private val rightSideTable = Table()

    private fun isNotPlayersTurn() = !UncivGame.Current.worldScreen.isPlayersTurn

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
        for (civ in viewingCiv.gameInfo.civilizations
            .filterNot {
                it.isDefeated() || it == viewingCiv || it.isBarbarian() || it.isSpectator() || !viewingCiv.knows(
                    it
                )
            }) {

            val civIndicator = ImageGetter.getNationIndicator(civ.nation, 100f)

            val relationship = ImageGetter.getCircle()
            if (viewingCiv.isAtWarWith(civ)) relationship.color = Color.RED
            else relationship.color = Color.GREEN
            relationship.setSize(30f, 30f)
            civIndicator.addActor(relationship)

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
        diplomacyTable.defaults().pad(10f)

        diplomacyTable.add(LeaderIntroTable(otherCiv)).row()

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
                        link = "Resource/$name"
                    )
                    UncivGame.Current.setScreen(pedia)
                }
            }
            diplomacyTable.add(resourcesTable).row()
        }

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
        if (nextLevelString != "") {
            diplomacyTable.add(nextLevelString.toLabel()).row()
        }

        var friendBonusText = "When Friends: ".tr()
        val eraInfo = viewingCiv.getEraObject()
        val friendBonuses =
            if (eraInfo == null) null
            else eraInfo.friendBonus[otherCiv.cityStateType.name]
        friendBonusText += 
            if (friendBonuses != null) {
                friendBonuses.joinToString(separator = ", ") { it.tr() }
            } else {
                // Deprecated, assume Civ V values for compatibility
                val cultureBonus = if(viewingCiv.getEraNumber() in 0..1) "3" else if (viewingCiv.getEraNumber() in 2..3) "6" else "13"
                val happinessBonus = if(viewingCiv.getEraNumber() in 0..1) "2" else "3"
                when (otherCiv.cityStateType) {
                    CityStateType.Militaristic -> "Provides military units every [20] turns".tr()
                    CityStateType.Cultured -> ("Provides [" + cultureBonus + "] [Culture] per turn").tr()
                    CityStateType.Mercantile -> ("Provides [" + happinessBonus + "] Happiness").tr()
                    CityStateType.Maritime -> "Provides [2] [Food] [in capital]".tr()
                }
            }


        var allyBonusText = "When Allies: "
        val allyBonuses = 
            if (eraInfo == null) null
            else eraInfo.allyBonus[otherCiv.cityStateType.name]
        if (allyBonuses != null) {
            allyBonusText += allyBonuses.joinToString(separator = ", ") { it.tr() }
        } else {
            // Deprecated, assume Civ V values for compatibility
            val cultureBonus = if(viewingCiv.getEraNumber() in 0..1) "6" else if (viewingCiv.getEraNumber() in 2..3) "12" else "26"
            val happinessBonus = if(viewingCiv.getEraNumber() in 0..1) "2" else "3"
            allyBonusText += when (otherCiv.cityStateType) {
                CityStateType.Militaristic -> "Provides military units every [20] turns".tr()
                CityStateType.Cultured -> ("Provides [" + cultureBonus + "] [Culture] per turn").tr()
                CityStateType.Mercantile -> ("Provides [" + happinessBonus + "] Happiness").tr() + ", " + "Provides a unique luxury".tr()
                CityStateType.Maritime -> "Provides [2] [Food] [in capital]".tr() + ", " + "Provides [1] [Food] [in all cities]".tr()
            }
        }

        val friendBonusLabelColor: Color
        if (otherCivDiplomacyManager.relationshipLevel() >= RelationshipLevel.Friend) {
            friendBonusLabelColor = Color.GREEN
            // RelationshipChange = Ally -> Friend or Friend -> Favorable
            val turnsToRelationshipChange = otherCivDiplomacyManager.getTurnsToRelationshipChange()
            diplomacyTable.add("Relationship changes in another [$turnsToRelationshipChange] turns".toLabel())
                .row()
        } else
            friendBonusLabelColor = Color.GRAY

        val friendBonusLabel = friendBonusText.toLabel(friendBonusLabelColor)
            .apply { setAlignment(Align.center) }
        diplomacyTable.add(friendBonusLabel).row()
        val allyBonusLabelColor = if (otherCivDiplomacyManager.relationshipLevel() == RelationshipLevel.Ally) Color.GREEN else Color.GRAY
        val allyBonusLabel = allyBonusText.toLabel(allyBonusLabelColor)
            .apply { setAlignment(Align.center) }
        diplomacyTable.add(allyBonusLabel).row()
        
        return diplomacyTable
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
            if (isNotPlayersTurn()) revokeProtectionButton.disable()
        } else {
            val protectionButton = "Pledge to protect".toTextButton()
            protectionButton.onClick {
                YesNoPopup("Declare Protection of [${otherCiv.civName}]?", {
                    otherCiv.addProtectorCiv(viewingCiv)
                    updateLeftSideTable()
                    updateRightSide(otherCiv)
                }, this).open()
            }
            if (viewingCiv.isAtWarWith(otherCiv)) {
                protectionButton.disable()
            }
            diplomacyTable.add(protectionButton).row()
            if (isNotPlayersTurn()) protectionButton.disable()
        }

        val demandTributeButton = "Demand Tribute".toTextButton()
        demandTributeButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getDemandTributeTable(otherCiv)))
        }
        diplomacyTable.add(demandTributeButton).row()
        if (isNotPlayersTurn() || otherCiv.getTributeWillingness(viewingCiv) <= 0) demandTributeButton.disable()

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

        for (assignedQuest in otherCiv.questManager.assignedQuests.filter { it.assignee == viewingCiv.civName }) {
            diplomacyTable.addSeparator()
            diplomacyTable.add(getQuestTable(assignedQuest)).row()
        }

        return diplomacyTable
    }

    private fun getImproveTilesButton(
        otherCiv: CivilizationInfo,
        otherCivDiplomacyManager: DiplomacyManager
    ): TextButton? {
        if (otherCiv.cities.isEmpty()) return null
        val improvableTiles = otherCiv.getCapital().getImprovableTiles()
            .filterNot { it.getTileResource().resourceType == ResourceType.Bonus }.toList()
        val improvements =
            otherCiv.gameInfo.ruleSet.tileImprovements.filter { it.value.turnsToBuild != 0 }
        var needsImprovements = false

        for (improvableTile in improvableTiles)
            for (tileImprovement in improvements.values)
                if (improvableTile.canBuildImprovement(
                        tileImprovement,
                        otherCiv
                    ) && improvableTile.getTileResource().improvement == tileImprovement.name
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

    private fun getGoldGiftTable(otherCiv: CivilizationInfo): Table {
        val diplomacyTable = getCityStateDiplomacyTableHeader(otherCiv)
        diplomacyTable.addSeparator()

        for (giftAmount in listOf(250, 500, 1000)) {
            val influenceAmount = viewingCiv.influenceGainedByGift(giftAmount)
            val giftButton =
                "Gift [$giftAmount] gold (+[$influenceAmount] influence)".toTextButton()
            giftButton.onClick {
                viewingCiv.giveGoldGift(otherCiv, giftAmount)
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

    private fun getImprovementGiftTable(otherCiv: CivilizationInfo): Table {
        val improvementGiftTable = getCityStateDiplomacyTableHeader(otherCiv)
        improvementGiftTable.addSeparator()

        val improvableTiles = otherCiv.getCapital().getImprovableTiles()
            .filterNot { it.getTileResource().resourceType == ResourceType.Bonus }.toList()
        val tileImprovements =
            otherCiv.gameInfo.ruleSet.tileImprovements.filter { it.value.turnsToBuild != 0 }

        for (improvableTile in improvableTiles) {
            for (tileImprovement in tileImprovements.values) {
                if (improvableTile.canBuildImprovement(tileImprovement, otherCiv) &&
                    improvableTile.getTileResource().improvement == tileImprovement.name
                ) {
                    val improveTileButton =
                        "Build [${tileImprovement}] on [${improvableTile.getTileResource()}] (200 Gold)".toTextButton()
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

        val demandGoldButton = "Take [${otherCiv.goldGainedByTribute()}] gold (-15 Influence)".toTextButton()
        demandGoldButton.onClick {
            viewingCiv.demandGold(otherCiv)
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandGoldButton).row()
        if (otherCiv.getTributeWillingness(viewingCiv, demandingWorker = false) <= 0)   demandGoldButton.disable()

        val demandWorkerButton = "Take worker (-50 Influence)".toTextButton()
        demandWorkerButton.onClick {
            viewingCiv.demandWorker(otherCiv)
            rightSideTable.clear()
            rightSideTable.add(ScrollPane(getCityStateDiplomacyTable(otherCiv)))
        }
        diplomacyTable.add(demandWorkerButton).row()
        if (otherCiv.getTributeWillingness(viewingCiv, demandingWorker = true) <= 0)    demandWorkerButton.disable()

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
        val title = "[${quest.name}] (+[${quest.influece.toInt()}] influence)"
        val description = assignedQuest.getDescription()

        questTable.add(title.toLabel(fontSize = 24)).row()
        questTable.add(description.toLabel().apply { wrap = true; setAlignment(Align.center) })
            .width(stage.width / 2).row()
        if (quest.duration > 0)
            questTable.add("[${remainingTurns}] turns remaining".toLabel()).row()

        questTable.onClick {
            assignedQuest.onClickAction()
        }
        return questTable
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

            if (!diplomacyManager.hasFlag(DiplomacyFlags.Denunceation)
                && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
            ) {
                val denounceButton = "Denounce ([30] turns)".toTextButton()
                denounceButton.onClick {
                    YesNoPopup("Denounce [${otherCiv.civName}]?", {
                        diplomacyManager.denounce()
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
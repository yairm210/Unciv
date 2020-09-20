package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.civilization.diplomacy.DiplomacyManager
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers.*
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import kotlin.math.roundToInt
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class DiplomacyScreen(val viewingCiv:CivilizationInfo):CameraStageBaseScreen() {

    val leftSideTable = Table().apply { defaults().pad(10f) }
    val rightSideTable = Table()

    fun isNotPlayersTurn() = !UncivGame.Current.worldScreen.isPlayersTurn

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
        closeButton.x = 10f
        stage.addActor(closeButton) // This must come after the split pane so it will be above, that the button will be clickable
    }

    private fun updateLeftSideTable() {
        leftSideTable.clear()
        for (civ in UncivGame.Current.gameInfo.civilizations
                .filterNot { it.isDefeated() || it == viewingCiv || it.isBarbarian() || it.isSpectator() }) {
            if (!viewingCiv.knows(civ)) continue

            val civIndicator = ImageGetter.getNationIndicator(civ.nation,100f)

            val relationship = ImageGetter.getCircle()
            if(viewingCiv.isAtWarWith(civ)) relationship.color = Color.RED
            else relationship.color = Color.GREEN
            relationship.setSize(30f,30f)
            civIndicator.addActor(relationship)

            leftSideTable.add(civIndicator).row()

            civIndicator.onClick {
                updateRightSide(civ)
            }
        }
    }

    fun updateRightSide(otherCiv: CivilizationInfo) {
        rightSideTable.clear()
        if (otherCiv.isCityState()) rightSideTable.add(getCityStateDiplomacyTable(otherCiv))
        else rightSideTable.add(ScrollPane(getMajorCivDiplomacyTable(otherCiv))).height(stage.height)
    }

    fun setTrade(civ: CivilizationInfo): TradeTable {
        rightSideTable.clear()
        val tradeTable =TradeTable(civ, this)
        rightSideTable.add(tradeTable)
        return tradeTable
    }



    private fun getCityStateDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        diplomacyTable.add(otherCiv.getLeaderDisplayName().toLabel(fontSize = 24)).row()
        diplomacyTable.add(("Type: ".tr() + otherCiv.getCityStateType().toString().tr()).toLabel()).row()
        otherCiv.updateAllyCivForCityState()
        val ally = otherCiv.getAllyCiv()
        if (ally != "") {
            val allyString = "{Ally: }{$ally} {Influence: }".tr() +
                    otherCiv.getDiplomacyManager(ally).influence.toString()
            diplomacyTable.add(allyString.toLabel()).row()
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

        val friendBonusText = when (otherCiv.getCityStateType()) {
            CityStateType.Cultured -> ("Provides [" + (3 * (viewingCiv.getEraNumber() + 1)).toString() + "] culture at 30 Influence").tr()
            CityStateType.Maritime -> "Provides 3 food in capital and 1 food in other cities at 30 Influence".tr()
            CityStateType.Mercantile -> "Provides 3 happiness at 30 Influence".tr()
            CityStateType.Militaristic -> "Provides land units every 20 turns at 30 Influence".tr()
        }

        val friendBonusLabelColor: Color
        if (otherCivDiplomacyManager.relationshipLevel() >= RelationshipLevel.Friend) {
            friendBonusLabelColor = Color.GREEN
            // RelationshipChange = Ally -> Friend or Friend -> Favorable
            val turnsToRelationshipChange = otherCivDiplomacyManager.getTurnsToRelationshipChange()
            diplomacyTable.add("Relationship changes in another [$turnsToRelationshipChange] turns".toLabel()).row()
        } else
            friendBonusLabelColor = Color.GRAY

        val friendBonusLabel = friendBonusText.toLabel(friendBonusLabelColor)
        diplomacyTable.add(friendBonusLabel).row()


        diplomacyTable.addSeparator()

        val giftAmount = 250
        val influenceAmount = giftAmount / 10
        val giftButton = "Gift [$giftAmount] gold (+[$influenceAmount] influence)".toTextButton()
        giftButton.onClick {
            viewingCiv.giveGoldGift(otherCiv, giftAmount)
            updateRightSide(otherCiv)
        }
        diplomacyTable.add(giftButton).row()
        if (viewingCiv.gold < giftAmount || isNotPlayersTurn()) giftButton.disable()

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)

        if(!viewingCiv.gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.diplomaticRelationshipsCannotChange)) {
            if (viewingCiv.isAtWarWith(otherCiv)) {
                val peaceButton = "Negotiate Peace".toTextButton()
                peaceButton.onClick {
                    YesNoPopup("Peace with [${otherCiv.civName}]?".tr(), {
                        val tradeLogic = TradeLogic(viewingCiv, otherCiv)
                        tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))
                        tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty))
                        tradeLogic.acceptTrade()
                        updateLeftSideTable()
                        updateRightSide(otherCiv)
                    }, this).open()
                }
                diplomacyTable.add(peaceButton).row()
                val cityStatesAlly = otherCiv.getAllyCiv()
                val atWarWithItsAlly = viewingCiv.getKnownCivs().any { it.civName == cityStatesAlly && it.isAtWarWith(viewingCiv) }
                if (isNotPlayersTurn() || atWarWithItsAlly) peaceButton.disable()
            } else {
                val declareWarButton = getDeclareWarButton(diplomacyManager, otherCiv)
                if (isNotPlayersTurn()) declareWarButton.disable()
                diplomacyTable.add(declareWarButton).row()
            }
        }

        return diplomacyTable
    }

    private fun getMajorCivDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)

        diplomacyTable.add(otherCiv.getLeaderDisplayName().toLabel(fontSize = 24)).row()
        if (otherCivDiplomacyManager.relationshipLevel() <= RelationshipLevel.Enemy)
            diplomacyTable.add(otherCiv.nation.hateHello.toLabel()).row()
        else
            diplomacyTable.add(otherCiv.nation.neutralHello.toLabel()).row()
        diplomacyTable.addSeparator()

        val diplomaticRelationshipsCanChange = !viewingCiv.gameInfo.ruleSet.modOptions.uniques.contains(ModOptionsConstants.diplomaticRelationshipsCannotChange)

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
        } else if(diplomaticRelationshipsCanChange) {
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
                val declareFriendshipButton = "Offer Declaration of Friendship ([30] turns)".toTextButton()
                declareFriendshipButton.onClick {
                    otherCiv.popupAlerts.add(PopupAlert(AlertType.DeclarationOfFriendship, viewingCiv.civName))
                    declareFriendshipButton.disable()
                }
                diplomacyTable.add(declareFriendshipButton).row()
                if (isNotPlayersTurn() || otherCiv.popupAlerts
                                .any { it.type == AlertType.DeclarationOfFriendship && it.value == viewingCiv.civName })
                    declareFriendshipButton.disable()
            }


            if (viewingCiv.canSignResearchAgreementsWith(otherCiv)) {
                val researchAgreementButton = "Research Agreement".toTextButton()

                val requiredGold = viewingCiv.getResearchAgreementCost(otherCiv)
                researchAgreementButton.onClick {
                    val tradeTable = setTrade(otherCiv)
                    val researchAgreement = TradeOffer(Constants.researchAgreement, TradeType.Treaty, requiredGold)
                    val goldCostOfSignResearchAgreement = TradeOffer("Gold".tr(), TradeType.Gold, -requiredGold)
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
                    && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)) {
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
        }

        return diplomacyTable
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
        if(otherCiv.popupAlerts.any { it.type==AlertType.DemandToStopSettlingCitiesNear && it.value==viewingCiv.civName })
            dontSettleCitiesButton.disable()
        dontSettleCitiesButton.onClick {
            otherCiv.popupAlerts.add(PopupAlert(AlertType.DemandToStopSettlingCitiesNear, viewingCiv.civName))
            dontSettleCitiesButton.disable()
        }
        demandsTable.add(dontSettleCitiesButton).row()

        demandsTable.add(Constants.close.toTextButton().onClick { updateRightSide(otherCiv) })
        return demandsTable
    }

    fun getRelationshipTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val relationshipTable = Table()

        val opinionOfUs = if(otherCivDiplomacyManager.civInfo.isCityState()) otherCivDiplomacyManager.influence.toInt()
        else otherCivDiplomacyManager.opinionOfOtherCiv().toInt()

        relationshipTable.add("Our relationship: ".toLabel())
        val relationshipLevel = otherCivDiplomacyManager.relationshipLevel()
        val relationshipText = relationshipLevel.name.tr() + " ($opinionOfUs)"
        val relationshipColor = when (relationshipLevel) {
            RelationshipLevel.Neutral -> Color.WHITE
            RelationshipLevel.Favorable, RelationshipLevel.Friend,
            RelationshipLevel.Ally -> Color.GREEN
            else -> Color.RED
        }

        relationshipTable.add(relationshipText.toLabel(relationshipColor))
        return relationshipTable
    }

    private fun getDeclareWarButton(diplomacyManager: DiplomacyManager, otherCiv: CivilizationInfo): TextButton {
        val declareWarButton = "Declare war".toTextButton()
        declareWarButton.color = Color.RED
        val turnsToPeaceTreaty = diplomacyManager.turnsToPeaceTreaty()
        if (turnsToPeaceTreaty > 0) {
            declareWarButton.disable()
            declareWarButton.setText(declareWarButton.text.toString() + " ($turnsToPeaceTreaty${Fonts.turn})")
        }
        declareWarButton.onClick {
            YesNoPopup("Declare war on [${otherCiv.civName}]?".tr(), {
                diplomacyManager.declareWar()
                setRightSideFlavorText(otherCiv, otherCiv.nation.attacked, "Very well.")
                updateLeftSideTable()
                }, this).open()
        }
        return declareWarButton
    }

    private fun setRightSideFlavorText(otherCiv: CivilizationInfo, flavorText:String, response: String){
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        diplomacyTable.add(otherCiv.getLeaderDisplayName().toLabel())
        diplomacyTable.addSeparator()
        diplomacyTable.add(flavorText.toLabel()).row()

        val responseButton = response.toTextButton()
        responseButton.onClick { updateRightSide(otherCiv) }
        diplomacyTable.add(responseButton)

        rightSideTable.clear()
        rightSideTable.add(diplomacyTable)
    }

}

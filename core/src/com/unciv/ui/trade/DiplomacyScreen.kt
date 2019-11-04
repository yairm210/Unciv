package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UnCivGame
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
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable
import kotlin.math.roundToInt

class DiplomacyScreen(val viewingCiv:CivilizationInfo):CameraStageBaseScreen() {

    val leftSideTable = Table().apply { defaults().pad(10f) }
    val rightSideTable = Table()

    fun isNotPlayersTurn() = !UnCivGame.Current.worldScreen.isPlayersTurn

    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen() }
        val splitPane = SplitPane(ScrollPane(leftSideTable), rightSideTable, false, skin)
        splitPane.splitAmount = 0.2f

        updateLeftSideTable()

        splitPane.setFillParent(true)
        stage.addActor(splitPane)


        val closeButton = TextButton("Close".tr(), skin)
        closeButton.onClick { UnCivGame.Current.setWorldScreen() }
        closeButton.label.setFontSize(24)
        closeButton.labelCell.pad(10f)
        closeButton.pack()
        closeButton.y = stage.height - closeButton.height - 10
        closeButton.x = 10f
        stage.addActor(closeButton) // This must come after the split pane so it will be above, that the button will be clickable
    }

    private fun updateLeftSideTable() {
        leftSideTable.clear()
        for (civ in UnCivGame.Current.gameInfo.civilizations
                .filterNot { it.isDefeated() || it == viewingCiv || it.isBarbarian() }) {
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

    fun updateRightSide(otherCiv: CivilizationInfo){
        rightSideTable.clear()
        if(otherCiv.isCityState()) rightSideTable.add(getCityStateDiplomacyTable(otherCiv))
        else rightSideTable.add(getMajorCivDiplomacyTable(otherCiv))
    }

    fun setTrade(civ: CivilizationInfo): TradeTable {
        rightSideTable.clear()
        val tradeTable =TradeTable(civ, stage) { updateLeftSideTable() }
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
        if (ally != "")
        {
            diplomacyTable.add(("Ally: ".tr() + ally!!.tr() + " " + "Influence: ".tr()
                    + otherCiv.getDiplomacyManager(ally).influence.toString().tr()).toLabel()).row()
        }
        val nextLevelString: String
        if (otherCivDiplomacyManager.influence.toInt() < 30) {
            nextLevelString = "Reach 30 for friendship."
        } else if (ally == viewingCiv.civName) {
            nextLevelString = ""
        } else {
            nextLevelString = "Reach highest influence above 60 for alliance."
        }
        diplomacyTable.add(("Influence: ".tr() + otherCivDiplomacyManager.influence.toInt().toString().tr() + " "
                + nextLevelString.tr()).toLabel()).row()
        diplomacyTable.add(getRelationshipTable(otherCivDiplomacyManager)).row()

        val friendBonusText = when (otherCiv.getCityStateType()) {
            CityStateType.Cultured -> ("Provides [" + (3 * (viewingCiv.getEra().ordinal + 1)).toString() + "] culture at [30] Influence").tr()
            CityStateType.Maritime -> "Provides 3 food in capital and 1 food in other cities at [30] Influence".tr()
            CityStateType.Mercantile -> "Provides 3 happiness at [30] Influence".tr()
            CityStateType.Militaristic -> "Provides land units every 20 turns at [30] Influence".tr()
        }

        val friendBonusLabelColor:Color
        if (otherCivDiplomacyManager.relationshipLevel() >= RelationshipLevel.Friend) {
            friendBonusLabelColor = Color.GREEN
            val turnsToRelationshipChange = otherCivDiplomacyManager.influence.toInt() - 30 + 1
            diplomacyTable.add("Relationship changes in another [$turnsToRelationshipChange] turns".toLabel()).row()
        } else
            friendBonusLabelColor = Color.GRAY

        val friendBonusLabel = friendBonusText.toLabel(friendBonusLabelColor)
        diplomacyTable.add(friendBonusLabel).row()


        diplomacyTable.addSeparator()

        val giftAmount = 250
        val influenceAmount = giftAmount / 10
        val giftButton = TextButton("Gift [$giftAmount] gold (+[$influenceAmount] influence)".tr(), skin)
        giftButton.onClick {
            viewingCiv.giveGoldGift(otherCiv, giftAmount)
            updateRightSide(otherCiv)
        }
        diplomacyTable.add(giftButton).row()
        if (viewingCiv.gold < giftAmount || isNotPlayersTurn()) giftButton.disable()

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)

        if (viewingCiv.isAtWarWith(otherCiv)) {
            val peaceButton = TextButton("Negotiate Peace".tr(), skin)
            peaceButton.onClick {
                YesNoPopupTable("Peace with [${otherCiv.civName}]?".tr(), {
                    val tradeLogic = TradeLogic(viewingCiv, otherCiv)
                    tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty, 30))
                    tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty, 30))
                    tradeLogic.acceptTrade()
                    updateLeftSideTable()
                }, this)
            }
            diplomacyTable.add(peaceButton).row()
            if(isNotPlayersTurn()) peaceButton.disable()
        } else {
            val declareWarButton = getDeclareWarButton(diplomacyManager, otherCiv)
            if(isNotPlayersTurn()) declareWarButton.disable()
            diplomacyTable.add(declareWarButton).row()
        }

        return diplomacyTable
    }

    private fun getMajorCivDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(viewingCiv)

        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)

        diplomacyTable.add(otherCiv.getLeaderDisplayName().toLabel(fontSize = 24)).row()
        val translatedNation = otherCiv.getTranslatedNation()
        if(otherCivDiplomacyManager.relationshipLevel()<=RelationshipLevel.Enemy)
            diplomacyTable.add(translatedNation.hateHello.toLabel()).row()
        else
            diplomacyTable.add(translatedNation.neutralHello.toLabel()).row()
        diplomacyTable.addSeparator()

        if(!viewingCiv.isAtWarWith(otherCiv)) {
            val tradeButton = TextButton("Trade".tr(), skin)
            tradeButton.onClick { setTrade(otherCiv) }
            diplomacyTable.add(tradeButton).row()
            if(isNotPlayersTurn()) tradeButton.disable()
        }
        else{
            val negotiatePeaceButton = TextButton("Negotiate Peace".tr(),skin)
            negotiatePeaceButton.onClick {
                val tradeTable = setTrade(otherCiv)
                val peaceTreaty = TradeOffer(Constants.peaceTreaty,TradeType.Treaty,30)
                tradeTable.tradeLogic.currentTrade.theirOffers.add(peaceTreaty)
                tradeTable.tradeLogic.currentTrade.ourOffers.add(peaceTreaty)
                tradeTable.offerColumnsTable.update()
            }
            if (isNotPlayersTurn() || otherCivDiplomacyManager.hasFlag(DiplomacyFlags.DeclaredWar))
                negotiatePeaceButton.disable() // Can't trade for 10 turns after war was declared

            diplomacyTable.add(negotiatePeaceButton).row()
        }

        val diplomacyManager = viewingCiv.getDiplomacyManager(otherCiv)



        if (!viewingCiv.isAtWarWith(otherCiv)) {
            if(otherCivDiplomacyManager.relationshipLevel() > RelationshipLevel.Neutral
                    && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)
                    && !diplomacyManager.hasFlag(DiplomacyFlags.Denunceation)){
                val declareFriendshipButton = TextButton("Declare Friendship ([30] turns)".tr(),skin)
                declareFriendshipButton.onClick {
                    diplomacyManager.signDeclarationOfFriendship()
                        setRightSideFlavorText(otherCiv,"May our nations forever remain united!","Indeed!")
                }
                diplomacyTable.add(declareFriendshipButton).row()
                if(isNotPlayersTurn()) declareFriendshipButton.disable()
            }

            if(!diplomacyManager.hasFlag(DiplomacyFlags.Denunceation)
                            && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)){
                val denounceButton = TextButton("Denounce ([30] turns)".tr(),skin)
                denounceButton.onClick {
                    diplomacyManager.denounce()
                    setRightSideFlavorText(otherCiv,"We will remember this.","Very well.")
                }
                diplomacyTable.add(denounceButton).row()
                if(isNotPlayersTurn()) denounceButton.disable()
            }

            val declareWarButton = getDeclareWarButton(diplomacyManager, otherCiv)
            diplomacyTable.add(declareWarButton).row()
            if(isNotPlayersTurn()) declareWarButton.disable()
        }

        val demandsButton = TextButton("Demands".tr(),skin)
        demandsButton.onClick {
            rightSideTable.clear()
            rightSideTable.add(getDemandsTable(viewingCiv,otherCiv))
        }
        diplomacyTable.add(demandsButton).row()
        if(isNotPlayersTurn()) demandsButton.disable()

        if(!otherCiv.isPlayerCivilization()) { // human players make their own choices
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

        val dontSettleCitiesButton = TextButton("Please don't settle new cities near us.".tr(),skin)
        if(otherCiv.popupAlerts.any { it.type==AlertType.DemandToStopSettlingCitiesNear && it.value==viewingCiv.civName })
            dontSettleCitiesButton.disable()
        dontSettleCitiesButton.onClick {
            otherCiv.popupAlerts.add(PopupAlert(AlertType.DemandToStopSettlingCitiesNear, viewingCiv.civName))
            dontSettleCitiesButton.disable()
        }
        demandsTable.add(dontSettleCitiesButton).row()

        demandsTable.add(TextButton("Close".tr(),skin).onClick { updateRightSide(otherCiv) })
        return demandsTable
    }

    fun getRelationshipTable(otherCivDiplomacyManager: DiplomacyManager): Table {
        val relationshipTable = Table()

        val opinionOfUs = if(otherCivDiplomacyManager.civInfo.isCityState()) otherCivDiplomacyManager.influence.toInt()
        else otherCivDiplomacyManager.opinionOfOtherCiv().toInt()

        relationshipTable.add("Our relationship: ".toLabel())
        val relationshipLevel = otherCivDiplomacyManager.relationshipLevel()
        val relationshipText = relationshipLevel.name.tr() + " ($opinionOfUs)"
        val relationshipColor = when {
            relationshipLevel == RelationshipLevel.Neutral -> Color.WHITE
            relationshipLevel == RelationshipLevel.Favorable || relationshipLevel == RelationshipLevel.Friend
                    || relationshipLevel == RelationshipLevel.Ally -> Color.GREEN
            else -> Color.RED
        }

        relationshipTable.add(relationshipText.toLabel(relationshipColor))
        return relationshipTable
    }

    private fun getDeclareWarButton(diplomacyManager: DiplomacyManager, otherCiv: CivilizationInfo): TextButton {
        val declareWarButton = TextButton("Declare war".tr(), skin)
        declareWarButton.color = Color.RED
        val turnsToPeaceTreaty = diplomacyManager.turnsToPeaceTreaty()
        if (turnsToPeaceTreaty > 0) {
            declareWarButton.disable()
            declareWarButton.setText(declareWarButton.text.toString() + " ($turnsToPeaceTreaty)")
        }
        declareWarButton.onClick {
            YesNoPopupTable("Declare war on [${otherCiv.civName}]?".tr(), {
                diplomacyManager.declareWar()
                setRightSideFlavorText(otherCiv,otherCiv.getTranslatedNation().attacked,"Very well.")
                updateLeftSideTable()
            }, this)
        }
        return declareWarButton
    }

    private fun setRightSideFlavorText(otherCiv: CivilizationInfo, flavorText:String, response: String){
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        diplomacyTable.add(otherCiv.getLeaderDisplayName().toLabel())
        diplomacyTable.addSeparator()
        diplomacyTable.add(flavorText.toLabel()).row()

        val responseButton = TextButton(response.tr(),skin)
        responseButton.onClick { updateRightSide(otherCiv) }
        diplomacyTable.add(responseButton)

        rightSideTable.clear()
        rightSideTable.add(diplomacyTable)
    }

}
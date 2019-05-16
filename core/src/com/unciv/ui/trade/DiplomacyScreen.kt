package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.CivilizationInfo
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

class DiplomacyScreen:CameraStageBaseScreen() {

    val leftSideTable = Table().apply { defaults().pad(10f) }
    val rightSideTable = Table()

    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen() }
        val splitPane = SplitPane(ScrollPane(leftSideTable), rightSideTable, false, skin)
        splitPane.splitAmount = 0.2f

        updateLeftSideTable()

        splitPane.setFillParent(true)
        stage.addActor(splitPane)


        val closeButton = TextButton("Close".tr(), skin)
        closeButton.onClick { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 10
        closeButton.x = 10f
        stage.addActor(closeButton) // This must come after the split pane so it will be above, that the button will be clickable
    }

    private fun updateLeftSideTable() {
        leftSideTable.clear()
        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        for (civ in UnCivGame.Current.gameInfo.civilizations
                .filterNot { it.isDefeated() || it.isPlayerCivilization() || it.isBarbarianCivilization() }) {
            if (!currentPlayerCiv.diplomacy.containsKey(civ.civName)) continue

            val civIndicator = ImageGetter.getCircle().apply { color = civ.getNation().getSecondaryColor() }
                    .surroundWithCircle(100f).apply { circle.color = civ.getNation().getColor() }
            val relationship = ImageGetter.getCircle()
            if(currentPlayerCiv.isAtWarWith(civ)) relationship.color = Color.RED
            else relationship.color = Color.GREEN
            relationship.setSize(30f,30f)
            civIndicator.addActor(relationship)

            leftSideTable.add(civIndicator).row()

            civIndicator.onClick {
                updateRightSide(civ)
            }
        }
    }

    fun updateRightSide(civ: CivilizationInfo){
        rightSideTable.clear()
        if(civ.isCityState()) rightSideTable.add(getCityStateDiplomacyTable(civ))
        else rightSideTable.add(getMajorCivDiplomacyTable(civ))
    }

    fun setTrade(civ: CivilizationInfo): TradeTable {
        rightSideTable.clear()
        val tradeTable =TradeTable(civ, stage) { updateLeftSideTable() }
        rightSideTable.add(tradeTable)
        return tradeTable
    }

    fun giveGoldGift(otherCiv: CivilizationInfo, giftAmount: Int) {
        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        currentPlayerCiv.gold -= giftAmount
        otherCiv.getDiplomacyManager(currentPlayerCiv).influence += giftAmount/10
        rightSideTable.clear()
        rightSideTable.add(getCityStateDiplomacyTable(otherCiv))
    }


    private fun getCityStateDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        if (otherCiv.isCityState()) {
            diplomacyTable.add(otherCiv.getNation().getLeaderDisplayName().toLabel()).row()
            diplomacyTable.add(("Type : " + otherCiv.getCityStateType().toString()).toLabel()).row()
            diplomacyTable.add(("Influence : " + otherCiv.getDiplomacyManager(currentPlayerCiv).influence.toInt().toString()).toLabel()).row()
            if (otherCiv.getDiplomacyManager(currentPlayerCiv).influence >= 60) {
                when(otherCiv.getCityStateType()) {
                    CityStateType.Cultured -> diplomacyTable.add(
                            ("Providing " + (5.0f * currentPlayerCiv.getEra().ordinal).toString() + " culture each turn").toLabel())
                    CityStateType.Maritime -> diplomacyTable.add(
                            ("Providing 3 food in capital and 1 food in other cities.").toLabel())
                    CityStateType.Mercantile -> diplomacyTable.add(
                            ("Providing 3 happiness.").toLabel())
                }
            }
        } else {
            diplomacyTable.add(otherCiv.getNation().getLeaderDisplayName().toLabel())
        }
        diplomacyTable.addSeparator()

        val giftButton = TextButton("Give 100 gold".tr(), skin)
        val giftAmount = 100
        giftButton.onClick{ giveGoldGift(otherCiv,giftAmount ) }
        diplomacyTable.add(giftButton).row()
        if (currentPlayerCiv.gold < giftAmount ) giftButton.disable()

        val diplomacyManager = currentPlayerCiv.getDiplomacyManager(otherCiv)

        if (currentPlayerCiv.isAtWarWith(otherCiv)) {
                val PeaceButton = TextButton("Negotiate Peace".tr(), skin)
                PeaceButton.onClick {
                    YesNoPopupTable("Peace with [${otherCiv.civName}]?".tr(), {
                        val tradeLogic = TradeLogic(currentPlayerCiv, otherCiv)
                        tradeLogic.currentTrade.ourOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty, 30))
                        tradeLogic.currentTrade.theirOffers.add(TradeOffer(Constants.peaceTreaty, TradeType.Treaty, 30))
                        tradeLogic.acceptTrade()
                        updateLeftSideTable()
                    }, this)
                }
                diplomacyTable.add(PeaceButton).row()
        } else {
            val declareWarButton = getDeclareWarButton(diplomacyManager, otherCiv)
            diplomacyTable.add(declareWarButton).row()
        }

        return diplomacyTable
    }

    private fun getMajorCivDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        diplomacyTable.add(otherCiv.getNation().getLeaderDisplayName().toLabel())
        diplomacyTable.addSeparator()

        if(!currentPlayerCiv.isAtWarWith(otherCiv)) {
            val tradeButton = TextButton("Trade".tr(), skin)
            tradeButton.onClick { setTrade(otherCiv) }
            diplomacyTable.add(tradeButton).row()
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
            if (otherCiv.getDiplomacyManager(currentPlayerCiv).hasFlag(DiplomacyFlags.DeclaredWar))
                negotiatePeaceButton.disable() // Can't trade for 10 turns after war was declared

            diplomacyTable.add(negotiatePeaceButton).row()
        }

        val diplomacyManager = currentPlayerCiv.getDiplomacyManager(otherCiv)



        if (!currentPlayerCiv.isAtWarWith(otherCiv)) {
            if(otherCiv.getDiplomacyManager(currentPlayerCiv).relationshipLevel() > RelationshipLevel.Neutral
                    && !diplomacyManager.hasFlag(DiplomacyFlags.DeclarationOfFriendship)){
                val declareFriendshipButton = TextButton("Declare Friendship ([30] turns)".tr(),skin)
                declareFriendshipButton.onClick {
                    diplomacyManager.signDeclarationOfFriendship()
                        setRightSideFlavorText(otherCiv,"May our nations forever remain united!".tr(),"Indeed!".tr())
                }
                diplomacyTable.add(declareFriendshipButton).row()
            }

            val declareWarButton = getDeclareWarButton(diplomacyManager, otherCiv)
            diplomacyTable.add(declareWarButton).row()
        }


        val diplomacyModifiersTable = Table()
        val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(currentPlayerCiv)

        val relationshipTable = Table()
        relationshipTable.add("Our relationship: ".toLabel())
        val relationshipLevel = otherCivDiplomacyManager.relationshipLevel()
        val relationshipText = otherCivDiplomacyManager.relationshipLevel().toString().tr() + " (" + otherCivDiplomacyManager.opinionOfOtherCiv().toInt() + ")"
        val relationshipColor = when {
            relationshipLevel == RelationshipLevel.Neutral -> Color.WHITE
            relationshipLevel == RelationshipLevel.Favorable || relationshipLevel == RelationshipLevel.Friend
                    || relationshipLevel == RelationshipLevel.Ally -> Color.GREEN
            else -> Color.RED
        }
        relationshipTable.add(relationshipText.toLabel().setFontColor(relationshipColor))
        diplomacyModifiersTable.add(relationshipText.toLabel()).row()

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
            }
            text = text.tr() + " "
            if (modifier.value > 0) text += "+"
            text += modifier.value.toInt()
            val color = if (modifier.value < 0) Color.RED else Color.GREEN
            diplomacyModifiersTable.add(text.toLabel().setFontColor(color)).row()
        }
        diplomacyTable.add(diplomacyModifiersTable).row()

        return diplomacyTable
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
        diplomacyTable.add(otherCiv.getNation().getLeaderDisplayName().toLabel())
        diplomacyTable.addSeparator()
        diplomacyTable.add(flavorText.toLabel()).row()

        val responseButton = TextButton(response,skin)
        responseButton.onClick { updateRightSide(otherCiv) }
        diplomacyTable.add(responseButton)

        rightSideTable.clear()
        rightSideTable.add(diplomacyTable)
    }

}
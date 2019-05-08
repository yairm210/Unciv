package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CityStateType
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticModifiers.*
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.trade.TradeOffer
import com.unciv.logic.trade.TradeType
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.PopupTable
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
                rightSideTable.clear()
                rightSideTable.add(getDiplomacyTable(civ))
            }
        }
    }

    fun setTrade(civ: CivilizationInfo): TradeTable {
        rightSideTable.clear()
        val tradeTable =TradeTable(civ, stage) { updateLeftSideTable() }
        rightSideTable.add(tradeTable)
        return tradeTable
    }

    fun giveGoldGift(otherCiv: CivilizationInfo) {
        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        currentPlayerCiv.gold -= 100
        otherCiv.getDiplomacyManager(currentPlayerCiv).influence += 10
        rightSideTable.clear()
        rightSideTable.add(getDiplomacyTable(otherCiv))
    }

    private fun getDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        val leaderName: String
        if (otherCiv.isCityState()) {
            leaderName = "City-state of [" + otherCiv.civName + "]"
            diplomacyTable.add(leaderName.toLabel()).row()
            diplomacyTable.add(("Type : " + otherCiv.getCityStateType().toString()).toLabel()).row()
            diplomacyTable.add(("Influence : " + otherCiv.getDiplomacyManager(currentPlayerCiv).influence.toInt().toString()).toLabel()).row()
            if (otherCiv.getDiplomacyManager(currentPlayerCiv).influence > 60) {
                if (otherCiv.getCityStateType() == CityStateType.Cultured) {
                    diplomacyTable.add(("Providing " + (5.0f * currentPlayerCiv.getEra().ordinal).toString() + " culture each turn").toLabel())
                }
            }
        } else {
            leaderName = "[" + otherCiv.getNation().leaderName + "] of [" + otherCiv.civName + "]"
            diplomacyTable.add(leaderName.toLabel())
        }
        diplomacyTable.addSeparator()

        if(otherCiv.isCityState()) {
            val giftButton = TextButton("Give 100 gold".tr(), skin)
            giftButton.onClick{ giveGoldGift(otherCiv) }
            diplomacyTable.add(giftButton).row()
            if (currentPlayerCiv.gold < 1) giftButton.disable()
        } else {
            val tradeButton = TextButton("Trade".tr(), skin)
            tradeButton.onClick { setTrade(otherCiv) }
            diplomacyTable.add(tradeButton).row()
        }

        val diplomacyManager = currentPlayerCiv.getDiplomacyManager(otherCiv)

        if (currentPlayerCiv.isAtWarWith(otherCiv)) {
            if (otherCiv.isCityState()) {
                val PeaceButton = TextButton("Negociate Peace".tr(), skin)
                PeaceButton.onClick {
                    YesNoPopupTable("Peace with [${otherCiv.civName}]?".tr(), {
                        val tradeLogic = TradeLogic(currentPlayerCiv, otherCiv)
                        tradeLogic.currentTrade.ourOffers.add(TradeOffer("Peace Treaty", TradeType.Treaty, 20))
                        tradeLogic.currentTrade.theirOffers.add(TradeOffer("Peace Treaty", TradeType.Treaty, 20))
                        tradeLogic.acceptTrade()
                        updateLeftSideTable()
                    }, this)
                }
                diplomacyTable.add(PeaceButton).row()
            }
        } else {
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

                    val responsePopup = PopupTable(this)
                    val otherCivLeaderName: String
                    if (otherCiv.isCityState()) {
                        otherCivLeaderName = "City-state [" + otherCiv.civName + "]"
                    } else {
                        otherCivLeaderName = "[" + otherCiv.getNation().leaderName + "] of [" + otherCiv.civName + "]"
                    }
                    responsePopup.add(otherCivLeaderName.toLabel())
                    responsePopup.addSeparator()
                    responsePopup.addGoodSizedLabel(otherCiv.getNation().attacked).row()
                    responsePopup.addButton("Very well.".tr()) { responsePopup.remove() }
                    responsePopup.open()

                    updateLeftSideTable()
                }, this)
            }
            diplomacyTable.add(declareWarButton).row()
        }

        if(!otherCiv.isCityState()){
            val diplomacyModifiersTable = Table()
            val otherCivDiplomacyManager = otherCiv.getDiplomacyManager(currentPlayerCiv)

            val relationshipTable = Table()
            relationshipTable.add("Our relationship: ".toLabel())
            val relationshipLevel = otherCivDiplomacyManager.relationshipLevel()
            val relationshipText = otherCivDiplomacyManager.relationshipLevel().toString().tr()+" ("+otherCivDiplomacyManager.opinionOfOtherCiv().toInt()+")"
            val relationshipColor = when{
                relationshipLevel==RelationshipLevel.Neutral -> Color.WHITE
                relationshipLevel==RelationshipLevel.Favorable || relationshipLevel==RelationshipLevel.Friend
                        || relationshipLevel==RelationshipLevel.Ally -> Color.GREEN
                else -> Color.RED
            }
            relationshipTable.add(relationshipText.toLabel().setFontColor(relationshipColor))
            diplomacyModifiersTable.add(relationshipText.toLabel()).row()

            for(modifier in otherCivDiplomacyManager.diplomaticModifiers){
                var text = when(valueOf(modifier.key)){
                    DeclaredWarOnUs -> "You declared war on us!"
                    WarMongerer -> "Your warmongering ways are unacceptable to us."
                    CapturedOurCities -> "You have captured our cities!"
                    YearsOfPeace -> "Years of peace have strengthened our relations."
                    SharedEnemy -> "Our mutual military struggle brings us closer together."
                    DeclarationOfFriendship -> "We have signed a public declaration of friendship"
                    DeclaredFriendshipWithOurEnemies -> "You have declared friendship with our enemies!"
                    DeclaredFriendshipWithOurAllies -> "You have declared friendship with our allies"
                }
                text = text.tr()+" "
                if(modifier.value>0) text += "+"
                text += modifier.value.toInt()
                val color = if(modifier.value<0) Color.RED else Color.GREEN
                diplomacyModifiersTable.add(text.toLabel().setFontColor(color)).row()
            }
            diplomacyTable.add(diplomacyModifiersTable).row()
        }

        return diplomacyTable
    }
}
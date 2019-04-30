package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
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

    private fun getDiplomacyTable(otherCiv: CivilizationInfo): Table {
        val currentPlayerCiv = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()
        val diplomacyTable = Table()
        diplomacyTable.defaults().pad(10f)
        val leaderName: String
        if (otherCiv.isCityState()) {
            leaderName = otherCiv.civName
        } else {
            leaderName = "[" + otherCiv.getNation().leaderName + "] of [" + otherCiv.civName + "]"
        }
        diplomacyTable.add(leaderName.toLabel())
        diplomacyTable.addSeparator()

        if(!otherCiv.isCityState()) {
            val tradeButton = TextButton("Trade".tr(), skin)
            tradeButton.onClick { setTrade(otherCiv) }
            diplomacyTable.add(tradeButton).row()
        }

        val diplomacyManager = currentPlayerCiv.getDiplomacyManager(otherCiv)

        if (!currentPlayerCiv.isAtWarWith(otherCiv)) {
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
                    val otherCivLeaderName = otherCiv.getNation().leaderName + " of " + otherCiv.civName
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
            diplomacyModifiersTable.add(("Current opinion: "+otherCivDiplomacyManager.opinionOfOtherCiv()).toLabel()).row()
            for(modifier in otherCivDiplomacyManager.diplomaticModifiers){
                diplomacyModifiersTable.add((modifier.key+" "+modifier.value).toLabel()).row()
            }
            diplomacyTable.add(diplomacyModifiersTable).row()
        }

        return diplomacyTable
    }
}
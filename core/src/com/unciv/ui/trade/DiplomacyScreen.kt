package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.PopupTable
import com.unciv.ui.worldscreen.optionstable.YesNoPopupTable

class DiplomacyScreen:CameraStageBaseScreen(){

    val leftSideTable = Table().apply { defaults().pad(10f) }
    val rightSideTable = Table()

    init{
        onBackButtonClicked { UnCivGame.Current.setWorldScreen() }
        val splitPane = SplitPane(ScrollPane(leftSideTable),rightSideTable,false, skin)
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
            val civDiplomacy = currentPlayerCiv.diplomacy[civ.civName]!!

            val civTable = Table().apply { background = ImageGetter.getBackground(civ.getNation().getColor()) }
            civTable.pad(10f)
            civTable.defaults().pad(10f)
            val peaceWarStatus = civDiplomacy.diplomaticStatus.toString()
            civTable.add(Label(civ.civName.tr() + " ({$peaceWarStatus})".tr(), skin)
                    .setFontSize(22).setFontColor(civ.getNation().getSecondaryColor())).row()
            civTable.addSeparator()

            val tradeButton = TextButton("Trade".tr(), skin)
            tradeButton.onClick {
                rightSideTable.clear()
                rightSideTable.add(TradeTable(civ, stage){updateLeftSideTable()})
            }
            civTable.add(tradeButton).row()

            if (!currentPlayerCiv.isAtWarWith(civ)) {
                val declareWarButton = TextButton("Declare war".tr(), skin)
                declareWarButton.color = Color.RED
                val turnsToPeaceTreaty = civDiplomacy.turnsToPeaceTreaty()
                if(turnsToPeaceTreaty>0){
                    declareWarButton.disable()
                    declareWarButton.setText(declareWarButton.text.toString() + " ($turnsToPeaceTreaty)")
                }
                declareWarButton.onClick {
                    YesNoPopupTable("Declare war on [${civ.civName}]?".tr(), {
                        civDiplomacy.declareWar()

                        val responsePopup = PopupTable(this)
                        val otherCivLeaderName = civ.getNation().leaderName + " of " + civ.civName
                        responsePopup.add(otherCivLeaderName.toLabel())
                        responsePopup.addSeparator()
                        responsePopup.addGoodSizedLabel(civ.getNation().attacked).row()
                        responsePopup.addButton("Very well.".tr()) { responsePopup.remove() }
                        responsePopup.open()

                        updateLeftSideTable()
                    }, this)
                }
                civTable.add(declareWarButton).row()
            }
            leftSideTable.add(civTable).row()
        }
    }
}

package com.unciv.ui.trade

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UnCivGame
import com.unciv.ui.utils.*

class DiplomacyScreen():CameraStageBaseScreen(){

    val leftSideTable = Table().apply { defaults().pad(10f) }
    val rightSideTable = Table()

    init{
        val splitPane = SplitPane(ScrollPane(leftSideTable),rightSideTable,false, skin)
        splitPane.setSplitAmount(0.2f)

        updateLeftSideTable()

        splitPane.setFillParent(true)
        stage.addActor(splitPane)


        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 10
        closeButton.x = 10f
        stage.addActor(closeButton) // This must come after the split pane so it will be above, that the button will be clickable
    }

    private fun updateLeftSideTable() {
        leftSideTable.clear()
        val playerCiv = UnCivGame.Current.gameInfo.getPlayerCivilization()
        for (civ in UnCivGame.Current.gameInfo.civilizations
                .filterNot { it.isDefeated() || it.isPlayerCivilization() || it.isBarbarianCivilization() }) {
            if (!playerCiv.diplomacy.containsKey(civ.civName)) continue
            val civDiplomacy = playerCiv.diplomacy[civ.civName]!!

            val civTable = Table().apply { background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f)) }
            civTable.pad(10f)
            civTable.defaults().pad(10f)
            val peaceWarStatus = civDiplomacy.diplomaticStatus.toString()
            civTable.add(Label(civ.civName.tr() + " ({$peaceWarStatus})".tr(), skin).apply { setFont(22); setFontColor(Color.WHITE) }).row()

            val tradeButton = TextButton("Trade".tr(), skin)
            tradeButton.addClickListener {
                rightSideTable.clear()
                rightSideTable.add(TradeTable(civ, stage){updateLeftSideTable()})
            }
            civTable.add(tradeButton).row()

            if (!playerCiv.isAtWarWith(civ)) {
                val declareWarButton = TextButton("Declare war".tr(), skin)
                val turnsToPeaceTreaty = civDiplomacy.turnsToPeaceTreaty()
                if(turnsToPeaceTreaty>0){
                    declareWarButton.disable()
                    declareWarButton.setText(declareWarButton.text.toString() + " ($turnsToPeaceTreaty)")
                }
                declareWarButton.addClickListener {
                    civDiplomacy.declareWar()
                    updateLeftSideTable()
                }
                civTable.add(declareWarButton).row()
            }
            leftSideTable.add(civTable).row()
        }
    }
}

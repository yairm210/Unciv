package com.unciv.ui.trade

import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UnCivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.center
import com.unciv.ui.utils.tr

class DiplomacyScreen():CameraStageBaseScreen(){
    init{
        val rightSideTable = Table()
        val leftSideTable = Table()
        val splitPane = SplitPane(rightSideTable,leftSideTable,false, skin)
        splitPane.setSplitAmount(0.2f)

        val playerCiv = UnCivGame.Current.gameInfo.getPlayerCivilization()
        for(civ in UnCivGame.Current.gameInfo.civilizations
                .filterNot { it.isDefeated() || it.isPlayerCivilization() || it.isBarbarianCivilization() }){
            if(!playerCiv.diplomacy.containsKey(civ.civName)) continue
            val tb = TextButton("Trade with [${civ.civName}]".tr(),skin)
            tb.addClickListener { leftSideTable.clear(); leftSideTable.add(TradeTable(civ,stage)) }
            rightSideTable.add(tb).pad(10f).row()
        }
        splitPane.setFillParent(true)
        stage.addActor(splitPane)


        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton) // This must come after the split pane so it will be above, that the button will be clickable
    }
}

class TradeScreen(otherCivilization: CivilizationInfo) : CameraStageBaseScreen(){

    init {
        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        stage.addActor(closeButton)


        val generalTable = TradeTable(otherCivilization, stage)
        generalTable.center(stage)

        stage.addActor(generalTable)
    }

}



package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.GameParameters
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontSize
import com.unciv.ui.utils.toLabel

class PlayerPickerTable: Table(){
    val playerList = ArrayList<Player>()
    val selectedPlayer: Player?=null

    init {
        update()
    }

    fun update(){
        clear()
        val playerListTable = Table()
        for(player in playerList)
            playerListTable.add(getPlayerTable(player)).row()
        playerListTable.add("+".toLabel().setFontSize(24).onClick { playerList.add(Player()); update() })
        add(playerListTable)

        if(selectedPlayer!=null){
            val nationsTable = Table()
            for(nation in GameBasics.Nations.values){
                if(selectedPlayer.chosenCiv!=nation.name && playerList.any { it.chosenCiv==nation.name })
                    continue

                nationsTable.add(NationTable(nation, GameParameters(), width / 3) {
                    selectedPlayer.chosenCiv = nation.name
                    update()
                })
            }
        }
    }

    fun getPlayerTable(player: Player): Table {
        val table = Table()
        val playerTypeTextbutton = TextButton(player.playerType.name, CameraStageBaseScreen.skin)
        playerTypeTextbutton.onClick {
            if (player.playerType == PlayerType.AI)
                player.playerType = PlayerType.Human
            else player.playerType = PlayerType.AI
            update()
        }
        table.add(playerTypeTextbutton)
        table.add(TextButton("Remove".tr(), CameraStageBaseScreen.skin).onClick { playerList.remove(player); update() })
        return table
    }
}


class Player{
    var playerType: PlayerType=PlayerType.AI
    var chosenCiv = Constants.random
}

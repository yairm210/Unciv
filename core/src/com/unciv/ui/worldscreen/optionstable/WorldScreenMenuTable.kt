package com.unciv.ui.worldscreen.optionstable

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.UnCivGame
import com.unciv.logic.map.RoadStatus
import com.unciv.models.gamebasics.tr
import com.unciv.ui.CivilopediaScreen
import com.unciv.ui.VictoryScreen
import com.unciv.ui.mapeditor.MapEditorScreen
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.SaveGameScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.setFontColor
import com.unciv.ui.utils.toLabel
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*
import kotlin.collections.ArrayList

class WorldScreenMenuTable(val worldScreen: WorldScreen) : PopupTable(worldScreen) {

    init {
        addButton("Map editor".tr()){
            val tileMapClone = worldScreen.gameInfo.tileMap.clone()
            for(tile in tileMapClone.values){
                tile.militaryUnit=null
                tile.civilianUnit=null
                tile.airUnits=ArrayList()
                tile.improvement=null
                tile.improvementInProgress=null
                tile.turnsToImprovement=0
                tile.roadStatus=RoadStatus.None
            }
            UnCivGame.Current.screen = MapEditorScreen(tileMapClone)
            remove()
        }

        addButton("Civilopedia".tr()){
            UnCivGame.Current.screen = CivilopediaScreen()
            remove()
        }

        addButton("Load game".tr()){
            UnCivGame.Current.screen = LoadGameScreen()
            remove()
        }

        addButton("Save game".tr()) {
            UnCivGame.Current.screen = SaveGameScreen()
            remove()
        }

        addButton("Start new game".tr()){ UnCivGame.Current.screen = NewGameScreen() }


        addButton("Multiplayer".tr()) { openMultiplayerPopup() }

        addButton("Victory status".tr()) { UnCivGame.Current.screen = VictoryScreen() }

        addButton("Options".tr()){
            UnCivGame.Current.worldScreen.stage.addActor(WorldScreenOptionsTable(worldScreen))
            remove()
        }

        addButton("Community"){
            WorldScreenCommunityTable(worldScreen)
            remove()
        }

        addCloseButton()

        open()
    }


    fun openMultiplayerPopup(){

        close()
        val multiplayerPopup = PopupTable(screen)

        multiplayerPopup.addGoodSizedLabel("HIGHLY EXPERIMENTAL - YOU HAVE BEEN WARNED!").row()
        multiplayerPopup.addGoodSizedLabel("To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.").row()
        multiplayerPopup.addGoodSizedLabel("You can assign your own user ID there easily, and othr players can copy their user IDs here and send them to you for you to include them in the game.").row()

        multiplayerPopup.addButton("Copy User Id"){ Gdx.app.clipboard.contents = UnCivGame.Current.settings.userId }.row()

        multiplayerPopup.addGoodSizedLabel("Once you've created your game, enter this screen again to copy the Game ID and send it to the other players.").row()

        val copyGameIdButton = multiplayerPopup.addButton("Copy game ID".tr()) {
            Gdx.app.clipboard.contents = worldScreen.gameInfo.gameId }.apply { row() }
        if(!worldScreen.gameInfo.gameParameters.isOnlineMultiplayer)
            copyGameIdButton.actor.disable()

        multiplayerPopup.addGoodSizedLabel("Players can enter you game by copying the game ID to the clipboard, and clicking on the Join Game button").row()
        val badGameIdLabel = "".toLabel().setFontColor(Color.RED)
        badGameIdLabel.isVisible = false
        multiplayerPopup.addButton("Join Game") {
            val gameId = Gdx.app.clipboard.contents
            try {
                UUID.fromString(gameId.trim())
            } catch (ex: Exception) {
                badGameIdLabel.setText("Invalid game ID!")
                badGameIdLabel.isVisible = true
                return@addButton
            }
            try {
                val game = OnlineMultiplayer().tryDownloadGame(gameId)
                UnCivGame.Current.loadGame(game)
            } catch (ex: Exception) {
                badGameIdLabel.setText("Could not download game!".tr())
                badGameIdLabel.isVisible = true
                return@addButton
            }
        }.row()
        multiplayerPopup.add(badGameIdLabel).row()
        multiplayerPopup.addCloseButton()
        multiplayerPopup.open()
    }
}

class WorldScreenCommunityTable(val worldScreen: WorldScreen) : PopupTable(worldScreen) {
    init{
        addButton("Discord"){
            Gdx.net.openURI("https://discord.gg/bjrB4Xw")
            remove()
        }

        addButton("Github"){
            Gdx.net.openURI("https://github.com/yairm210/UnCiv")
            remove()
        }

        addCloseButton()

        open()
    }
}

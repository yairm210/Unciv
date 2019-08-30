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

        if(worldScreen.gameInfo.gameParameters.isOnlineMultiplayer){
            addButton("Copy game ID".tr()){ Gdx.app.clipboard.contents = worldScreen.gameInfo.gameId }
        }

        if(UnCivGame.Current.mutiplayerEnabled)
            addJoinMultiplayerButton()

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

    private fun addJoinMultiplayerButton() {
        addButton("Join multiplayer".tr()) {
            close()
            val joinMultiplayerPopup = PopupTable(screen)
            joinMultiplayerPopup.addGoodSizedLabel("Copy the game ID to your clipboard, and click the Join Game button!").row()
            val badGameIdLabel = "".toLabel().setFontColor(Color.RED)
            badGameIdLabel.isVisible = false
            joinMultiplayerPopup.addButton("Join Game") {
                val gameId = Gdx.app.clipboard.contents.trim()
                try {
                    UUID.fromString(gameId)
                } catch (ex: Exception) {
                    badGameIdLabel.setText("Invalid game ID!")
                    badGameIdLabel.isVisible = true
                    return@addButton
                }
                try {
                    val game = OnlineMultiplayer().tryDownloadGame(gameId)
                    UnCivGame.Current.loadGame(game)
                } catch (ex: Exception) {
                    badGameIdLabel.setText("Could not download game1!")
                    badGameIdLabel.isVisible = true
                    return@addButton
                }
            }.row()
            joinMultiplayerPopup.add(badGameIdLabel).row()
            joinMultiplayerPopup.addCloseButton()
            joinMultiplayerPopup.open()
        }
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

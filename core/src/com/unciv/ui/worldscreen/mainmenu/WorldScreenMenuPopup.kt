package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.CivilopediaScreen
import com.unciv.ui.VictoryScreen
import com.unciv.ui.mapeditor.LoadMapScreen
import com.unciv.ui.mapeditor.NewMapScreen
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.SaveGameScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.WorldScreen
import java.util.*
import kotlin.concurrent.thread

class WorldScreenMenuPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {

    init {
        val width = 200f
        val height = 30f
        addSquareButton("Map editor".tr()){
            openMapEditorPopup()
            close()
        }.size(width,height)
        addSeparator()

        addSquareButton("Civilopedia".tr()){
            UncivGame.Current.setScreen(CivilopediaScreen(worldScreen.gameInfo.ruleSet))
            close()
        }.size(width,height)
        addSeparator()

        addSquareButton("Load game".tr()){
            UncivGame.Current.setScreen(LoadGameScreen())
            close()
        }.size(width,height)
        addSeparator()

        addSquareButton("Save game".tr()) {
            UncivGame.Current.setScreen(SaveGameScreen())
            close()
        }.size(width,height)
        addSeparator()

        addSquareButton("Start new game".tr()){ UncivGame.Current.setScreen(NewGameScreen()) }.size(width,height)
        addSeparator()


        addSquareButton("Multiplayer".tr()) { openMultiplayerPopup() }.size(width,height)
        addSeparator()

        addSquareButton("Victory status".tr()) { UncivGame.Current.setScreen(VictoryScreen()) }.size(width,height)
        addSeparator()

        addSquareButton("Options".tr()){
            WorldScreenOptionsPopup(worldScreen).open()
            close()
        }.size(width,height)
        addSeparator()

        addSquareButton("Community"){
            WorldScreenCommunityPopup(worldScreen).open()
            close()
        }.size(width,height)
        addSeparator()

        addSquareButton("Close"){
            close()
        }.size(width,height)
    }



    fun openMultiplayerPopup(){

        close()
        val multiplayerPopup = Popup(screen)

        multiplayerPopup.addGoodSizedLabel("To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.").row()
        multiplayerPopup.addGoodSizedLabel("You can assign your own user ID there easily, and other players can copy their user IDs here and send them to you for you to include them in the game.").row()

        multiplayerPopup.addButton("Copy User ID"){ Gdx.app.clipboard.contents = UncivGame.Current.settings.userId }.row()

        multiplayerPopup.addGoodSizedLabel("Once you've created your game, enter this screen again to copy the Game ID and send it to the other players.").row()

        val copyGameIdButton = multiplayerPopup.addButton("Copy Game ID".tr()) {
            Gdx.app.clipboard.contents = worldScreen.gameInfo.gameId }.apply { row() }
        if(!worldScreen.gameInfo.gameParameters.isOnlineMultiplayer)
            copyGameIdButton.actor.disable()

        multiplayerPopup.addGoodSizedLabel("Players can enter your game by copying the game ID to the clipboard, and clicking on the Join Game button").row()
        val badGameIdLabel = "".toLabel(Color.RED)
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
            thread(name="MultiplayerDownload") {
                try {
                    // The tryDownload can take more than 500ms. Therefore, to avoid ANRs,
                    // we need to run it in a different thread.
                    val game = OnlineMultiplayer().tryDownloadGame(gameId.trim())
                    // The loadGame creates a screen, so it's a UI action,
                    // therefore it needs to run on the main thread so it has a GL context
                    Gdx.app.postRunnable { UncivGame.Current.loadGame(game) }
                } catch (ex: Exception) {
                    badGameIdLabel.setText("Could not download game!".tr())
                    badGameIdLabel.isVisible = true
                }
            }
        }.row()
        multiplayerPopup.add(badGameIdLabel).row()
        multiplayerPopup.addCloseButton()
        multiplayerPopup.open()
    }

    /** Shows the [Popup] with the map editor initialization options */
    private fun openMapEditorPopup() {

        close()
        val mapEditorPopup = Popup(screen)

        mapEditorPopup.addGoodSizedLabel("Map editor".tr()).row()

        // Create a new map
        mapEditorPopup.addButton("New map") {
            UncivGame.Current.setScreen(NewMapScreen())
            mapEditorPopup.close()
        }

        // Load the map
        mapEditorPopup.addButton("Load map") {
            val loadMapScreen = LoadMapScreen(null)
            loadMapScreen.closeButton.isVisible = true
            loadMapScreen.closeButton.onClick {
                UncivGame.Current.setWorldScreen()
                loadMapScreen.dispose() }
            UncivGame.Current.setScreen(loadMapScreen)
            mapEditorPopup.close()
        }

        mapEditorPopup.addCloseButton()
        mapEditorPopup.open()
    }

}

class WorldScreenCommunityPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init{
        addButton("Discord"){
            Gdx.net.openURI("https://discord.gg/bjrB4Xw")
            close()
        }

        addButton("Github"){
            Gdx.net.openURI("https://github.com/yairm210/UnCiv")
            close()
        }

        addCloseButton()
    }
}

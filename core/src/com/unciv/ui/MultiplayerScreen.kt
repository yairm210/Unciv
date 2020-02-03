package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.logic.GameSaver
import com.unciv.logic.GameInfo
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import java.util.*
import kotlin.concurrent.thread

class MultiplayerScreen() : PickerScreen() {

    private lateinit var selectedGame: GameInfo
    private val rightSideTable = Table()
    private val leftSideTable = Table()
    private val editButton = TextButton("Edit Session Info".tr(), skin).apply { disable() }
    private val addSessionButton = TextButton("Add Multiplayer Session".tr(), skin)
    private var sessionCount = 1

    init {
        setDefaultCloseAction()

        //TopTable Setup
        topTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).height(stage.height*2/3)
        topTable.add(rightSideTable)
        scrollPane.setScrollingDisabled(false, true)

        //rightTable
        //TODO ADD INFO

        rightSideTable.add(
                TextButton("Copy User ID".tr(), skin).onClick { Gdx.app.clipboard.contents = UncivGame.Current.settings.userId }
        ).pad(10f).row()

        var copyGameIdButton = TextButton("Copy Game ID".tr(), skin).apply { onClick { Gdx.app.clipboard.contents = game.gameInfo.gameId } }
        if(!game.gameInfo.gameParameters.isOnlineMultiplayer)
            copyGameIdButton.disable()
        rightSideTable.add(copyGameIdButton).pad(10f).row()

        rightSideTable.add(
                addSessionButton.onClick {
                    downloadMultiplayerGame(Gdx.app.clipboard.contents)
                }
        ).pad(10f).row()

        rightSideTable.add(
                editButton.onClick {
                    //TODO EDIT SCREEN
                    //GameSaver().deleteSave()
                }
        ).pad(10f).row()

        //SessionBrowserTable Setup
        updateSessionList()

        //RightSideButton Setup
        rightSideButton.setText("Join Game".tr())
        rightSideButton.onClick {
            joinMultiplaerGame()
        }

        //TODO ADD SESSION INFO
    }

    private fun downloadMultiplayerGame(gameId: String){
        val errorPopup = Popup(this)
        try {
            UUID.fromString(gameId.trim())
        } catch (ex: Exception) {
            errorPopup.addGoodSizedLabel("Invalid game ID!".tr())
            errorPopup.open()
            errorPopup.addCloseButton()
            return
        }
        thread(name="MultiplayerDownload") {
            try {
                addSessionButton.setText("loading...")
                addSessionButton.disable()
                // The tryDownload can take more than 500ms. Therefore, to avoid ANRs,
                // we need to run it in a different thread.
                val game = OnlineMultiplayer().tryDownloadGame(gameId.trim())
                GameSaver().saveGame(game, "MultiplayerSession-Multiplayer-Game-$sessionCount")
                updateSessionList()
                addSessionButton.setText("Add Multiplayer Session")
                addSessionButton.enable()

            } catch (ex: Exception) {
                errorPopup.addGoodSizedLabel("Could not download game!".tr())
                errorPopup.open()
                errorPopup.addCloseButton()
            }
        }
    }

    private fun joinMultiplaerGame(){
        val errorPopup = Popup(this)
        val gameId = selectedGame.gameId
        try {
            UUID.fromString(gameId.trim())
        } catch (ex: Exception) {
            errorPopup.addGoodSizedLabel("Invalid game ID!".tr()).row()//TODO closebutton should be in next row
            errorPopup.addCloseButton()
            errorPopup.open()
            return
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
                errorPopup.addGoodSizedLabel("Could not download game!".tr()).row()//TODO closebutton should be in next row
                errorPopup.addCloseButton()
                errorPopup.open()

            }
        }
    }

    private fun updateSessionList(){
        leftSideTable.clear()
        sessionCount = 1
        for (game in GameSaver().getSaves()) {
            if (game.startsWith("MultiplayerSession")) {
                val sessionName = game.removePrefix("MultiplayerSession-")
                leftSideTable.add(TextButton(sessionName, skin).apply {
                    onClick {
                        selectedGame = GameSaver().loadGameByName(game)
                        editButton.enable()
                        rightSideButton.enable()
                    }
                }).pad(5f).row()
                sessionCount++
            }
        }
    }
}

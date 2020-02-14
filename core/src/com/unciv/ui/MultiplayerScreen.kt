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
    private lateinit var selectedGameName: String
    private val rightSideTable = Table()
    private val leftSideTable = Table()
    private var sessionCount = 1

    private val editButtonText = "Edit Session Info".tr()
    private val addSessionText = "Add Multiplayer Session".tr()

    private val editButton = TextButton(editButtonText, skin).apply { disable() }
    private val addSessionButton = TextButton(addSessionText, skin)
    private val copyGameIdButton = TextButton("Copy Game ID".tr(), skin).apply { disable() }

    init {
        setDefaultCloseAction()

        //Help Button Setup
        val tab = Table()
        val helpButton = TextButton("?", skin)
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)
        stage.addActor(tab)

        //TopTable Setup
        topTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).height(stage.height*2/3)
        topTable.add(rightSideTable)
        scrollPane.setScrollingDisabled(false, true)

        //rightTable Setup
        rightSideTable.add(
                TextButton("Copy User ID".tr(), skin).onClick {
                    Gdx.app.clipboard.contents = UncivGame.Current.settings.userId
                    ResponsePopup("userID copied", this)
                }
        ).pad(10f).row()

        rightSideTable.add(
                copyGameIdButton.onClick {
                    Gdx.app.clipboard.contents = selectedGame.gameId
                    ResponsePopup("gameID copied", this)
                }
        ).pad(10f).row()

        rightSideTable.add(
                editButton.onClick {
                    UncivGame.Current.setScreen(EditSessionScreen(selectedGame, selectedGameName, this))
                }
        ).pad(10f).row()

        rightSideTable.add(
                addSessionButton.onClick {
                    downloadMultiplayerGame(Gdx.app.clipboard.contents, "Multiplayer-Game-$sessionCount")
                }
        ).pad(10f).row()

        //LeftTable Setup
        reloadSessionListUI()

        //RightSideButton Setup
        rightSideButton.setText("Join Game".tr())
        rightSideButton.onClick {
            joinMultiplaerGame()
        }
    }

    fun updateAllGames(){ //TODO anderer Name?
        thread (name = "serialGameDownload"){
            for (gameSaveName in GameSaver().getSaves(true)) {
                try {
                    var game = GameSaver().loadGameByName(gameSaveName)
                    game = OnlineMultiplayer().tryDownloadGame(game.gameId.trim())
                    GameSaver().saveGame(game, gameSaveName, true)
                }
                catch (ex: Exception){
                    continue
                }
            }
        }
    }

    private fun downloadMultiplayerGame(gameId: String, saveName: String){
        val errorPopup = Popup(this)
        try {
            UUID.fromString(gameId.trim())
        } catch (ex: Exception) {
            errorPopup.addGoodSizedLabel("Invalid game ID!".tr())
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
            return
        }
        thread(name="MultiplayerDownload") {
            try {
                addSessionButton.setText("Working...".tr())//TODO Shouldnt be here
                addSessionButton.disable()
                // The tryDownload can take more than 500ms. Therefore, to avoid ANRs,
                // we need to run it in a different thread.
                val game = OnlineMultiplayer().tryDownloadGame(gameId.trim())
                GameSaver().saveGame(game, saveName, true)

                reloadSessionListUI()
                addSessionButton.setText(addSessionText)
                addSessionButton.enable()
            } catch (ex: Exception) {
                    errorPopup.addGoodSizedLabel("Could not download game!".tr())
                    errorPopup.row()
                    errorPopup.addCloseButton()
                    errorPopup.open()

                    addSessionButton.setText(addSessionText)
                    addSessionButton.enable()
            }
        }
    }

    private fun joinMultiplaerGame(){
        val errorPopup = Popup(this)
        val gameId = selectedGame.gameId
        try {
            UUID.fromString(gameId.trim())
        } catch (ex: Exception) {
            errorPopup.addGoodSizedLabel("Invalid game ID!".tr())//TODO closebutton should be in next row
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
            return
        }
        UncivGame.Current.loadGame(selectedGame)
    }

    fun reloadSessionListUI(){
        leftSideTable.clear()
        sessionCount = 1
        for (gameSaveName in GameSaver().getSaves(true)) {
            leftSideTable.add(TextButton(gameSaveName, skin).apply {
                onClick {
                    selectedGame = GameSaver().loadGameByName(gameSaveName, true)
                    selectedGameName = gameSaveName
                    copyGameIdButton.enable()
                    editButton.enable()
                    rightSideButton.enable()

                    //TODO ADD SESSION INFO
                    //get Minutes since last modified
                    var lastSavedMinutesAgo = (System.currentTimeMillis() - GameSaver().getSave(gameSaveName, true).lastModified()) / 60000
                    var descriptionText = "Last Game State Updated: $lastSavedMinutesAgo minutes ago \r\n"
                    descriptionText += "Current Turn: " + selectedGame.currentPlayer + "\r\n"
                    descriptionLabel.setText(descriptionText)
                }
            }).pad(5f).row()
            sessionCount++
        }
    }
}

class EditSessionScreen(game: GameInfo, gameName: String, backScreen: MultiplayerScreen): PickerScreen(){
    init {
        var textField = TextField(gameName, skin)

        topTable.add(Label("Rename".tr(), skin)).row()
        topTable.add(textField).pad(10f).padBottom(30f).width(backScreen.stage.width/2).row()

        topTable.add(
                TextButton("Delete", skin).onClick {

                }
        )

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            UncivGame.Current.setScreen(backScreen)
        }

        //RightSideButton Setup
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            GameSaver().saveGame(game, textField.text, true)
            UncivGame.Current.setScreen(backScreen)
            backScreen.reloadSessionListUI()
        }
    }
}

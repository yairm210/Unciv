package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.disable
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import java.util.*
import kotlin.concurrent.thread

class MultiplayerScreen() : PickerScreen() {

    private lateinit var selectedSessionId:String
    private val idGetterTable = Table()
    private val sessionBrowserTable = Table()

    init {
        setDefaultCloseAction()

        //TopTable Setup
        topTable.add("Session Browser".toLabel(Color.WHITE,30)).pad(10f)
        topTable.add("".toLabel()).row()
        topTable.add(ScrollPane(sessionBrowserTable).apply { setScrollingDisabled(true, false) }).height(stage.height*2/3)
        topTable.add(idGetterTable)
        scrollPane.setScrollingDisabled(false, true)
        //topTable.debug()

        //TODO SHIT
        //rightTable
        //addGoodSizedLabel(idGetterTable, "To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.")
        //(idGetterTable, "You can assign your own user ID there easily, and other players can copy their user IDs here and send them to you for you to include them in the game.")

        idGetterTable.add(
                TextButton("Copy User ID", skin).onClick { Gdx.app.clipboard.contents = UncivGame.Current.settings.userId }
        ).pad(10f).row()

        //addGoodSizedLabel(idGetterTable, "Once you've created your game, enter this screen again to copy the Game ID and send it to the other players.")

        var copyGameIdButton = TextButton("Copy Game ID".tr(), skin).apply { onClick { Gdx.app.clipboard.contents = game.gameInfo.gameId } }
        if(!game.gameInfo.gameParameters.isOnlineMultiplayer)
            copyGameIdButton.disable()
        idGetterTable.add(copyGameIdButton).pad(10f).row()

        //addGoodSizedLabel(idGetterTable, "Players can enter your game by copying the game ID to the clipboard, and clicking on the Add Game button")
        //TODO END OF SHIT*/

        //SessionBrowserTable Setup
        updateSessions()

        //RightSideButton Setup
        val errorPopup = Popup(this)
        rightSideButton.setText("Join Game".tr())
        rightSideButton.onClick {
            val gameId = selectedSessionId
            try {
                UUID.fromString(gameId.trim())
            } catch (ex: Exception) {
                errorPopup.addGoodSizedLabel("Invalid game ID!".tr())
                errorPopup.open()
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
                    errorPopup.addGoodSizedLabel("Could not download game!".tr())
                    errorPopup.open()
                }
            }
        }
    }

    fun updateSessions(){
        var i = 0;
        while (i < 20){
            sessionBrowserTable.add(TextButton("Session $i", skin).apply {
                onClick {

                }
            }).pad(5f).row()
            i++
        }

    }

    fun addGoodSizedLabel(table:Table, text:String) {
        table.add(text.toLabel().apply {
            //width = stage.width / 2 //TODO WHY IS STAGE NULL?
            setWrap(true)
        }).row()
    }
}

package com.unciv.ui.newgamescreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.Player
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.optionstable.PopupTable
import java.util.*

class PlayerPickerTable(val newGameScreen: NewGameScreen, val newGameParameters: GameParameters): Table() {
    val playerListTable = Table()
    val halfWidth = newGameScreen.stage.width / 2.5f

    init {
        add(ScrollPane(playerListTable)).width(newGameScreen.stage.width/2)
                .height(newGameScreen.stage.height * 0.6f)
        update()
    }

    fun update() {
        playerListTable.clear()
        for (player in newGameParameters.players)
            playerListTable.add(getPlayerTable(player)).pad(10f).row()
        if(newGameParameters.players.count() < GameBasics.Nations.values.count { it.isMajorCiv() }) {
            playerListTable.add("+".toLabel().setFontSize(30).apply { this.setAlignment(Align.center) }
                    .setFontColor(Color.BLACK).surroundWithCircle(50f).onClick { newGameParameters.players.add(Player()); update() })
        }
    }

    fun getPlayerTable(player: Player): Table {
        val playerTable = Table()
        playerTable.pad(20f)
        playerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.8f))

        val nationTable = getNationTable(player)
        playerTable.add(nationTable)

        val playerTypeTextbutton = TextButton(player.playerType.name, CameraStageBaseScreen.skin)
        playerTypeTextbutton.onClick {
            if (player.playerType == PlayerType.AI)
                player.playerType = PlayerType.Human
            else player.playerType = PlayerType.AI
            update()
        }
        playerTable.add(playerTypeTextbutton).pad(20f)
        playerTable.add(TextButton("Remove".tr(), CameraStageBaseScreen.skin)
                .onClick { newGameParameters.players.remove(player); update() }).row()
        if(newGameParameters.isOnlineMultiplayer && player.playerType==PlayerType.Human) {
            val playerIdTable = Table()
            playerIdTable.add("Player ID:".toLabel())

            val playerIdTextfield = TextField(player.playerId, CameraStageBaseScreen.skin)
            playerIdTable.add(playerIdTextfield)
            val errorLabel = "Not a valid user id!".toLabel().setFontColor(Color.RED)
            errorLabel.isVisible=false
            playerIdTable.add(errorLabel)
            playerIdTextfield.addListener {
                try {
                    val uuid = UUID.fromString(playerIdTextfield.text)
                    player.playerId = playerIdTextfield.text
                    errorLabel.isVisible=false
                } catch (ex: Exception) {
                    errorLabel.isVisible=true
                }
                true
            }

            playerIdTable.row()

            val currentUserId = UnCivGame.Current.settings.userId
            val setCurrentUserButton = TextButton("Set current user", CameraStageBaseScreen.skin)
            setCurrentUserButton.onClick {
                playerIdTextfield.text = currentUserId
                errorLabel.isVisible = false
            }
            playerIdTable.add(setCurrentUserButton)

            playerTable.add(playerIdTable).colspan(playerTable.columns)
        }

        return playerTable
    }

    private fun getNationTable(player: Player): Table {
        val nationTable = Table()
        val nationImage = if (player.chosenCiv == "Random") "?".toLabel()
                .apply { this.setAlignment(Align.center) }.setFontSize(30)
                .setFontColor(Color.BLACK).surroundWithCircle(50f)
        else ImageGetter.getNationIndicator(GameBasics.Nations[player.chosenCiv]!!, 50f)
        nationTable.add(nationImage)
        nationTable.add(player.chosenCiv.toLabel()).pad(20f)
        nationTable.touchable = Touchable.enabled
        nationTable.onClick {
            val nationsPopup = PopupTable(newGameScreen)
            val nationListTable = Table()
            for (nation in GameBasics.Nations.values.filter { !it.isCityState() && it.name != "Barbarians" }) {
                if (player.chosenCiv != nation.name && newGameParameters.players.any { it.chosenCiv == nation.name })
                    continue

                nationListTable.add(NationTable(nation, halfWidth) {
                    player.chosenCiv = nation.name
                    nationsPopup.close()
                    update()
                }).pad(10f).width(halfWidth).row()
            }
            nationsPopup.add(ScrollPane(nationListTable)).height(newGameScreen.stage.height * 0.8f)
            nationsPopup.open()
            update()
        }
        return nationTable
    }
}



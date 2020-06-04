package com.unciv.ui.newgamescreen

import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.IdChecker
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*
import java.util.*

class PlayerPickerTable(val previousScreen: GameParametersPreviousScreen, var gameParameters: GameParameters): Table() {
    val playerListTable = Table()
    val nationsPopupWidth = previousScreen.stage.width / 2f
    val civBlocksWidth = previousScreen.stage.width / 3
    var locked = false


    init {
        top()
        add("Civilizations".toLabel(fontSize = 24)).padBottom(20f).row()
        add(ScrollPane(playerListTable).apply { setOverscroll(false, false) }).width(civBlocksWidth)
        update()
    }

    fun update(desiredCiv: String = "") {
        playerListTable.clear()
        val gameBasics = previousScreen.ruleset // the mod picking changes this ruleset

        reassignRemovedModReferences()
        val newRulesetPlayableCivs = previousScreen.ruleset.nations.count { it.key != Constants.barbarians }
        if (gameParameters.players.size > newRulesetPlayableCivs)
            gameParameters.players = ArrayList(gameParameters.players.subList(0, newRulesetPlayableCivs))
        if (desiredCiv.isNotEmpty()) assignDesiredCiv(desiredCiv)

        for (player in gameParameters.players) {
            playerListTable.add(getPlayerTable(player, gameBasics)).width(civBlocksWidth).padBottom(20f).row()
        }
        if (gameParameters.players.count() < gameBasics.nations.values.count { it.isMajorCiv() }
                && !locked) {
            playerListTable.add("+".toLabel(Color.BLACK, 30).apply { this.setAlignment(Align.center) }
                    .surroundWithCircle(50f).onClick { gameParameters.players.add(Player()); update() }).pad(10f)
        }
        previousScreen.setRightSideButtonEnabled(gameParameters.players.size > 1)
    }

    private fun reassignRemovedModReferences() {
        for (player in gameParameters.players) {
            if (!previousScreen.ruleset.nations.containsKey(player.chosenCiv))
                player.chosenCiv = "Random"
        }
    }

    private fun assignDesiredCiv(desiredCiv: String) {
        // No auto-select if desiredCiv already used
        if (gameParameters.players.any { it.chosenCiv == desiredCiv }) return
        // Do auto-select, silently no-op if no suitable slot (human with 'random' choice)
        gameParameters.players.firstOrNull { it.chosenCiv == "Random" && it.playerType == PlayerType.Human }?.chosenCiv = desiredCiv
    }

    fun getPlayerTable(player: Player, ruleset: Ruleset): Table {
        val playerTable = Table()
        playerTable.pad(5f)
        playerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.8f))

        val nationTable = getNationTable(player)
        playerTable.add(nationTable).left()

        val playerTypeTextbutton = player.playerType.name.toTextButton()
        playerTypeTextbutton.onClick {
            if (locked) return@onClick
            if (player.playerType == PlayerType.AI)
                player.playerType = PlayerType.Human
            else player.playerType = PlayerType.AI
            update()
        }
        playerTable.add(playerTypeTextbutton).width(100f).pad(5f).right()
        if (!locked) {
            playerTable.add("-".toLabel(Color.BLACK, 30).apply { this.setAlignment(Align.center) }
                    .surroundWithCircle(40f)
                    .onClick { gameParameters.players.remove(player); update() }).pad(5f).right().row()
        }
        if (gameParameters.isOnlineMultiplayer && player.playerType == PlayerType.Human) {

            val playerIdTextfield = TextField(player.playerId, CameraStageBaseScreen.skin)
            playerIdTextfield.messageText = "Please input Player ID!".tr()
            playerTable.add(playerIdTextfield).colspan(2).fillX().pad(5f)
            var errorLabel = "✘".toLabel(Color.RED)
            playerTable.add(errorLabel).pad(5f).row()

            fun onPlayerIdTextUpdated() {
                try {
                    UUID.fromString(IdChecker.checkAndReturnPlayerUuid(playerIdTextfield.text))
                    player.playerId = playerIdTextfield.text.trim()
                    errorLabel.apply { setText("✔");setFontColor(Color.GREEN) }
                } catch (ex: Exception) {
                    errorLabel.apply { setText("✘");setFontColor(Color.RED) }
                }
            }

            playerIdTextfield.addListener { onPlayerIdTextUpdated(); true }
            val currentUserId = UncivGame.Current.settings.userId
            val setCurrentUserButton = "Set current user".toTextButton()
            setCurrentUserButton.onClick {
                playerIdTextfield.text = currentUserId
                onPlayerIdTextUpdated()
            }
            playerTable.add(setCurrentUserButton).colspan(3).fillX().pad(5f).row()

            val copyFromClipboardButton = "Player ID from clipboard".toTextButton()
            copyFromClipboardButton.onClick {
                playerIdTextfield.text = Gdx.app.clipboard.contents
                onPlayerIdTextUpdated()
            }
            playerTable.add(copyFromClipboardButton).colspan(3).fillX().pad(5f)
        }

        return playerTable
    }

    private fun getNationTable(player: Player): Table {
        val nationTable = Table()
        val nationImage = if (player.chosenCiv == "Random") "?".toLabel(Color.WHITE, 25)
                .apply { this.setAlignment(Align.center) }
                .surroundWithCircle(36f).apply { circle.color = Color.BLACK }
                .surroundWithCircle(40f, false).apply { circle.color = Color.WHITE }
        else ImageGetter.getNationIndicator(previousScreen.ruleset.nations[player.chosenCiv]!!, 40f)
        nationTable.add(nationImage).pad(5f)
        nationTable.add(player.chosenCiv.toLabel()).pad(5f)
        nationTable.touchable = Touchable.enabled
        nationTable.onClick {
            popupNationPicker(player)
        }
        return nationTable
    }

    private fun popupNationPicker(player: Player) {
        val nationsPopup = Popup(previousScreen)
        val nationListTable = Table()

        val randomPlayerTable = Table()
        randomPlayerTable.background = ImageGetter.getBackground(Color.BLACK)

        randomPlayerTable.add("?".toLabel(Color.WHITE, 30)
                .apply { this.setAlignment(Align.center) }
                .surroundWithCircle(45f).apply { circle.color = Color.BLACK }
                .surroundWithCircle(50f, false).apply { circle.color = Color.WHITE }).pad(10f)
        randomPlayerTable.add("Random".toLabel())
        randomPlayerTable.touchable = Touchable.enabled
        randomPlayerTable.onClick {
            player.chosenCiv = "Random"
            nationsPopup.close()
            update()
        }
        nationListTable.add(randomPlayerTable).pad(10f).width(nationsPopupWidth).row()


        for (nation in previousScreen.ruleset.nations.values
                .filter { !it.isCityState() && it.name != Constants.barbarians }) {
            if (player.chosenCiv != nation.name && gameParameters.players.any { it.chosenCiv == nation.name })
                continue

            nationListTable.add(NationTable(nation, nationsPopupWidth, previousScreen.ruleset).onClick {
                player.chosenCiv = nation.name
                nationsPopup.close()
                update()
            }).pad(10f).width(nationsPopupWidth).row()
        }

        nationsPopup.add(ScrollPane(nationListTable)).height(previousScreen.stage.height * 0.8f)
        nationsPopup.pack()

        val closeImage = ImageGetter.getImage("OtherIcons/Close")
        closeImage.setSize(30f,30f)
        val closeImageHolder = Group() // This is to add it some more clickable space, to make it easier to click on the phone
        closeImageHolder.setSize(50f,50f)
        closeImage.center(closeImageHolder)
        closeImageHolder.addActor(closeImage)
        closeImageHolder.onClick { nationsPopup.close() }
        closeImageHolder.setPosition(0f, nationsPopup.height, Align.topLeft)
        nationsPopup.addActor(closeImageHolder)

        nationsPopup.open()
        update()
    }
}
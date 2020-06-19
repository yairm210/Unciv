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
import com.unciv.models.ruleset.Nation
import com.unciv.models.translations.tr
import com.unciv.ui.mapeditor.GameParametersScreen
import com.unciv.ui.utils.*
import java.util.*

/**
 * This [Table] is used to pick or edit players information for new game/scenario creation.
 * Could be inserted to [NewGameScreen], [GameParametersScreen] or any other [Screen]
 * which provides [GameSetupInfo] for ruleset and updates.
 * Upon player changes updates property [gameParameters]. Also updates available nations when mod changes.
 * In case it is used in map editor, as a part of [GameParametersScreen], additionally tries to
 * update units/starting locations on the [previousScreen] when player deleted or
 * switched nation.
 * @param previousScreen screen where player table is inserted, should provide [GameSetupInfo] as property,
 *          updated when player added/deleted/changed
 * @param gameParameters contains info about number of players and their chosen nation.
 */
class PlayerPickerTable(val previousScreen: PreviousScreenInterface, var gameParameters: GameParameters): Table() {
    /**
     * Main table containing list of players, their nations,
     * AI or Human player types.
     */
    val playerListTable = Table()
    val nationsPopupWidth = previousScreen.stage.width / 2f
    val civBlocksWidth = previousScreen.stage.width / 3

    /**
     * Locks player table for editing, used during new game creation with scenario.
     */
    var locked = false

    /**
     * No random civilization is available, used during map editing.
     */
    var noRandom = false

    init {
        top()
        add("Civilizations".toLabel(fontSize = 24)).padBottom(20f).row()
        add(ScrollPane(playerListTable).apply { setOverscroll(false, false) }).width(civBlocksWidth)
        update()
    }


    /**
     * Updates view of main player table. Used when mod picked or player changed.
     * Also sets desired civilization, that is preferable for human players.
     * @param desiredCiv desired civilization name
     */
    fun update(desiredCiv: String = "") {
        playerListTable.clear()
        val oldRuleset = previousScreen.gameSetupInfo.ruleset // the mod picking changes this ruleset

        reassignRemovedModReferences()
        val newRulesetPlayableCivs = previousScreen.gameSetupInfo.ruleset.nations.count { it.key != Constants.barbarians }
        if (gameParameters.players.size > newRulesetPlayableCivs)
            gameParameters.players = ArrayList(gameParameters.players.subList(0, newRulesetPlayableCivs))
        if (desiredCiv.isNotEmpty()) assignDesiredCiv(desiredCiv)

        for (player in gameParameters.players) {
            playerListTable.add(getPlayerTable(player)).width(civBlocksWidth).padBottom(20f).row()
        }
        if (gameParameters.players.count() < oldRuleset.nations.values.count { it.isMajorCiv() }
                && !locked) {
            playerListTable.add("+".toLabel(Color.BLACK, 30).apply { this.setAlignment(Align.center) }
                    .surroundWithCircle(50f).onClick {
                        var player = Player()
                        if (noRandom) { player.apply { chosenCiv = getAvailablePlayerCivs().first().name } }
                        gameParameters.players.add(player)
                        update()
                    }).pad(10f)
        }
        previousScreen.setRightSideButtonEnabled(gameParameters.players.size > 1)
    }

    /**
     * If new mod removes nations already chosen by some player
     * sets default player civilization
     */
    private fun reassignRemovedModReferences() {
        for (player in gameParameters.players) {
            if (!previousScreen.gameSetupInfo.ruleset.nations.containsKey(player.chosenCiv))
                player.chosenCiv = "Random"
        }
    }

    /**
     * Assigns desired civilization for human players with 'random' choice
     * @param desiredCiv string containing desired civilization name
     */
    private fun assignDesiredCiv(desiredCiv: String) {
        // No auto-select if desiredCiv already used
        if (gameParameters.players.any { it.chosenCiv == desiredCiv }) return
        // Do auto-select, silently no-op if no suitable slot (human with 'random' choice)
        gameParameters.players.firstOrNull { it.chosenCiv == "Random" && it.playerType == PlayerType.Human }?.chosenCiv = desiredCiv
    }

    /**
     * Creates [Table] for single player containing clickable
     * player type button ("AI" or "Human"), nation [Table]
     * and "-" remove player button.*
     * @param player for which [Table] is generated
     * @return [Table] containing the all the elements
     */
    fun getPlayerTable(player: Player): Table {
        val playerTable = Table()
        playerTable.pad(5f)
        playerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.8f))

        val nationTable = getNationTable(player)
        playerTable.add(nationTable).left()

        val playerTypeTextbutton = player.playerType.name.toTextButton()
        playerTypeTextbutton.onClick {
//            if (locked) return@onClick
            if (player.playerType == PlayerType.AI)
                player.playerType = PlayerType.Human
            else player.playerType = PlayerType.AI
            update()
        }
        playerTable.add(playerTypeTextbutton).width(100f).pad(5f).right()
        if (!locked) {
            playerTable.add("-".toLabel(Color.BLACK, 30).apply { this.setAlignment(Align.center) }
                    .surroundWithCircle(40f)
                    .onClick {
                        gameParameters.players.remove(player)
                        if (previousScreen is GameParametersScreen) previousScreen.apply{
                            mapEditorScreen.tileMap.stripPlayer(player)
                        }
                        update()
                    }).pad(5f).right().row()
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

    /**
     * Creates clickable icon and nation name for some [Player]
     * as a [Table]. Clicking creates [popupNationPicker] to choose new nation.
     * @param player [Player] for which generated
     * @return [Table] containing nation icon and name
     */
    private fun getNationTable(player: Player): Table {
        val nationTable = Table()
        val nationImage = if (player.chosenCiv == "Random") "?".toLabel(Color.WHITE, 25)
                .apply { this.setAlignment(Align.center) }
                .surroundWithCircle(36f).apply { circle.color = Color.BLACK }
                .surroundWithCircle(40f, false).apply { circle.color = Color.WHITE }
        else ImageGetter.getNationIndicator(previousScreen.gameSetupInfo.ruleset.nations[player.chosenCiv]!!, 40f)
        nationTable.add(nationImage).pad(5f)
        nationTable.add(player.chosenCiv.toLabel()).pad(5f)
        nationTable.touchable = Touchable.enabled
        nationTable.onClick {
            if (!locked) popupNationPicker(player)
        }
        return nationTable
    }

    /**
     * Opens Nation picking popup with all nations,
     * currently available for [player] to choose, depending on current
     * ruleset and other players nation choice.
     * @param player current player
     */
    private fun popupNationPicker(player: Player) {
        val nationsPopup = Popup(previousScreen as CameraStageBaseScreen)
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

        if (!noRandom) { nationListTable.add(randomPlayerTable).pad(10f).width(nationsPopupWidth).row() }

        for (nation in getAvailablePlayerCivs()) {
            if (player.chosenCiv == nation.name)
                continue

            nationListTable.add(NationTable(nation, nationsPopupWidth, previousScreen.gameSetupInfo.ruleset).onClick {
                if (previousScreen is GameParametersScreen) {
                    previousScreen.mapEditorScreen.tileMap.switchPlayerNation(player, nation)
                }
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

    /**
     * Returns list of available civilization for all players, according
     * to current ruleset, with exeption of city states nations and barbarians
     * @return [ArrayList] of available [Nation]s
     */
    private fun getAvailablePlayerCivs(): ArrayList<Nation> {
        var nations = ArrayList<Nation>()
        for (nation in previousScreen.gameSetupInfo.ruleset.nations.values
                .filter { !it.isCityState() && it.name != Constants.barbarians }) {
            if (gameParameters.players.any { it.chosenCiv == nation.name })
                continue
            nations.add(nation)
        }
        return nations
    }

}

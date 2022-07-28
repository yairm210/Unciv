package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.IdChecker
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.multiplayer.FriendList
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.mapeditor.GameParametersScreen
import com.unciv.ui.multiplayer.FriendPickerList
import com.unciv.ui.pickerscreens.PickerPane
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.*
import com.unciv.ui.utils.extensions.*
import java.util.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

/**
 * This [Table] is used to pick or edit players information for new game creation.
 * Could be inserted to [NewGameScreen], [GameParametersScreen] or any other [Screen][BaseScreen]
 * which provides [GameSetupInfo] and [Ruleset].
 * Upon player changes updates property [gameParameters]. Also updates available nations when mod changes.
 * In case it is used in map editor, as a part of [GameParametersScreen], additionally tries to
 * update units/starting location on the [previousScreen] when player deleted or
 * switched nation.
 * @param previousScreen A [Screen][BaseScreen] where the player table is inserted, should provide [GameSetupInfo] as property, updated when a player is added/deleted/changed
 * @param gameParameters contains info about number of players.
 * @param blockWidth sets a width for the Civ "blocks". If too small a third of the stage is used.
 */
class PlayerPickerTable(
    val previousScreen: IPreviousScreen,
    var gameParameters: GameParameters,
    blockWidth: Float = 0f
): Table() {
    val playerListTable = Table()
    val civBlocksWidth = if(blockWidth <= 10f) previousScreen.stage.width / 3 - 5f else blockWidth

    /** Locks player table for editing, currently unused, was previously used for scenarios and could be useful in the future.*/
    var locked = false

    /** No random civilization is available, used during map editing.*/
    var noRandom = false

    private val friendList = FriendList()

    init {
        for (player in gameParameters.players)
            player.playerId = "" // This is to stop people from getting other users' IDs and cheating with them in multiplayer games

        top()
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
        val gameBasics = previousScreen.ruleset // the mod picking changes this ruleset

        reassignRemovedModReferences()
        val newRulesetPlayableCivs = previousScreen.ruleset.nations.count { it.key != Constants.barbarians }
        if (gameParameters.players.size > newRulesetPlayableCivs)
            gameParameters.players = ArrayList(gameParameters.players.subList(0, newRulesetPlayableCivs))
        if (desiredCiv.isNotEmpty()) assignDesiredCiv(desiredCiv)

        for (player in gameParameters.players) {
            playerListTable.add(getPlayerTable(player)).width(civBlocksWidth).padBottom(20f).row()
        }
        if (!locked && gameParameters.players.size < gameBasics.nations.values.count { it.isMajorCiv() }) {
            val addPlayerButton = "+".toLabel(Color.BLACK, 30).apply { this.setAlignment(Align.center) }
                    .surroundWithCircle(50f)
                    .onClick {
                        var player = Player()
                        // no random mode - add first not spectator civ if still available
                        if (noRandom) {
                            val availableCiv = getAvailablePlayerCivs().firstOrNull()
                            if (availableCiv != null) player = Player(availableCiv.name)
                            // Spectators only Humans
                            else player = Player(Constants.spectator).apply { playerType = PlayerType.Human }
                        }
                        gameParameters.players.add(player)
                        update()
                    }
            playerListTable.add(addPlayerButton).pad(10f)
        }
        // enable start game when more than 1 active player
        val moreThanOnePlayer = 1 < gameParameters.players.count { it.chosenCiv != Constants.spectator }
        (previousScreen as? PickerScreen)?.setRightSideButtonEnabled(moreThanOnePlayer)
    }

    /**
     * Reassigns removed mod references to random civilization
     */
    private fun reassignRemovedModReferences() {
        for (player in gameParameters.players) {
            if (!previousScreen.ruleset.nations.containsKey(player.chosenCiv) || previousScreen.ruleset.nations[player.chosenCiv]!!.isCityState())
                player.chosenCiv = Constants.random
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
        gameParameters.players.firstOrNull { it.chosenCiv == Constants.random && it.playerType == PlayerType.Human }?.chosenCiv = desiredCiv
    }

    /**
     * Creates [Table] for single player containing clickable
     * player type button ("AI" or "Human"), nation [Table]
     * and "-" remove player button.*
     * @param player for which [Table] is generated
     * @return [Table] containing the all the elements
     */
    private fun getPlayerTable(player: Player): Table {
        val playerTable = Table()
        playerTable.pad(5f)
        playerTable.background = ImageGetter.getBackground(ImageGetter.getBlue().darken(0.8f))

        val nationTable = getNationTable(player)
        playerTable.add(nationTable).left()

        val playerTypeTextButton = player.playerType.name.toTextButton()
        playerTypeTextButton.onClick {
            if (player.playerType == PlayerType.AI)
                player.playerType = PlayerType.Human
            // we cannot change Spectator player to AI type, robots not allowed to spectate :(
            else if (player.chosenCiv != Constants.spectator)
                player.playerType = PlayerType.AI
            update()
        }
        playerTable.add(playerTypeTextButton).width(100f).pad(5f).right()
        if (!locked) {
            playerTable.add("-".toLabel(Color.BLACK, 30).apply { this.setAlignment(Align.center) }
                    .surroundWithCircle(40f)
                    .onClick {
                        gameParameters.players.remove(player)
                        if (previousScreen is GameParametersScreen) previousScreen.mapEditorScreen.tileMap.stripPlayer(player)
                        update()
                    }).pad(5f).right().row()
        }
        if (gameParameters.isOnlineMultiplayer && player.playerType == PlayerType.Human) {

            val playerIdTextField = UncivTextField.create("Please input Player ID!", player.playerId)
            playerTable.add(playerIdTextField).colspan(2).fillX().pad(5f)
            val errorLabel = "✘".toLabel(Color.RED)
            playerTable.add(errorLabel).pad(5f).row()

            fun onPlayerIdTextUpdated() {
                try {
                    UUID.fromString(IdChecker.checkAndReturnPlayerUuid(playerIdTextField.text))
                    player.playerId = playerIdTextField.text.trim()
                    errorLabel.apply { setText("✔");setFontColor(Color.GREEN) }
                } catch (ex: Exception) {
                    errorLabel.apply { setText("✘");setFontColor(Color.RED) }
                }
            }
            onPlayerIdTextUpdated()

            playerIdTextField.addListener { onPlayerIdTextUpdated(); true }
            val currentUserId = UncivGame.Current.settings.multiplayer.userId
            val setCurrentUserButton = "Set current user".toTextButton()
            setCurrentUserButton.onClick {
                playerIdTextField.text = currentUserId
                onPlayerIdTextUpdated()
            }
            playerTable.add(setCurrentUserButton).colspan(3).fillX().pad(5f).row()

            val copyFromClipboardButton = "Player ID from clipboard".toTextButton()
            copyFromClipboardButton.onClick {
                playerIdTextField.text = Gdx.app.clipboard.contents
                onPlayerIdTextUpdated()
            }
            playerTable.add(copyFromClipboardButton).right().colspan(3).fillX().pad(5f).row()

            //check if friends list is empty before adding the select friend button
            if (friendList.friendList.isNotEmpty()) {
                val selectPlayerFromFriendsList = "Player ID from friends list".toTextButton()
                selectPlayerFromFriendsList.onClick {
                    popupFriendPicker(player)
                }
                playerTable.add(selectPlayerFromFriendsList).left().colspan(3).fillX().pad(5f)
            }
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
        val nationImage =
            if (player.chosenCiv == Constants.random)
                ImageGetter.getRandomNationIndicator(40f)
            else ImageGetter.getNationIndicator(previousScreen.ruleset.nations[player.chosenCiv]!!, 40f)
        nationTable.add(nationImage).pad(5f)
        nationTable.add(player.chosenCiv.toLabel()).pad(5f)
        nationTable.touchable = Touchable.enabled
        nationTable.onClick {
            if (!locked) popupNationPicker(player)
        }
        return nationTable
    }

    /**
     * Opens Friend picking popup with all friends,
     * currently available for [player] to choose, depending on current
     * friends list and if another friend is selected.
     * @param player current player
     */
    private fun popupFriendPicker(player: Player) {
        FriendSelectionPopup(this, player, previousScreen as BaseScreen ).open()
        update()
    }

    /**
     * Opens Nation picking popup with all nations,
     * currently available for [player] to choose, depending on current
     * ruleset and other players nation choice.
     * @param player current player
     */
    private fun popupNationPicker(player: Player) {
        NationPickerPopup(this, player).open()
        update()
    }

    /**
     * Returns a list of available civilization for all players, according
     * to current ruleset, with exception of city states nations, spectator and barbarians.
     *
     * Skips nations already chosen by a player, unless parameter [dontSkipNation] says to keep a
     * specific one. That is used so the picker can be used to inspect and confirm the current selection.
     *
     * @return [Sequence] of available [Nation]s
     */
    internal fun getAvailablePlayerCivs(dontSkipNation: String? = null) =
        previousScreen.ruleset.nations.values.asSequence()
            .filter { it.isMajorCiv() }
            .filter { it.name == dontSkipNation || gameParameters.players.none { player -> player.chosenCiv == it.name } }

    /**
     * Returns a list of available friends.
     * Skips friends already chosen.
     *
     * @return [Sequence] of available [FriendList.Friend]s
     */
    internal fun getAvailableFriends(): Sequence<FriendList.Friend> {
        val friendListWithRemovedFriends = friendList.friendList.toMutableList()
        for (index in gameParameters.players.indices) {
            val currentFriendId = previousScreen.gameSetupInfo.gameParameters.players[index].playerId
            friendListWithRemovedFriends.remove(friendList.getFriendById(currentFriendId))
        }
        return friendListWithRemovedFriends.asSequence()
    }
}

class FriendSelectionPopup(
    private val playerPicker: PlayerPickerTable,
    player: Player,
    screen: BaseScreen,
) : Popup(screen) {

    val pickerPane = PickerPane()
    private var selectedFriendId: String? = null

    init {
        val pickerCell = add()
            .width(700f).fillX().expandX()
            .minHeight(screen.stage.height * 0.5f)
            .maxHeight(screen.stage.height * 0.8f)

        val friendList = FriendPickerList(playerPicker, ::friendSelected)
        pickerPane.topTable.add(friendList)
        pickerPane.rightSideButton.setText("Select friend".tr())
        pickerPane.closeButton.onClick(::close)
        pickerCell.setActor<PickerPane>(pickerPane)
        pickerPane.rightSideButton.onClick {
            close()
            val friendId = selectedFriendId
            if (friendId != null) {
                player.playerId = selectedFriendId.toString()
                close()
                playerPicker.update()
            }
        }
    }

    private fun friendSelected(friendName: String) {
        val friendsList = FriendList()
        val friend = friendsList.getFriendByName(friendName)
        if (friend != null) {
            selectedFriendId = friend.playerID
        }
        pickerPane.setRightSideButtonEnabled(true)
        pickerPane.rightSideButton.setText("Select [$friendName]".tr())
    }

}

private class NationPickerPopup(
    private val playerPicker: PlayerPickerTable,
    private val player: Player
) : Popup(playerPicker.previousScreen as BaseScreen) {
    companion object {
        // These are used for the Close/OK buttons in the lower left/right corners:
        const val buttonsCircleSize = 70f
        const val buttonsIconSize = 50f
        const val buttonsOffsetFromEdge = 5f
        val buttonsBackColor: Color = Color.BLACK.cpy().apply { a = 0.67f }
    }

    private val previousScreen = playerPicker.previousScreen
    private val ruleset = previousScreen.ruleset
    // This Popup's body has two halves of same size, either side by side or arranged vertically
    // depending on screen proportions - determine height for one of those
    private val partHeight = stageToShowOn.height * (if (stageToShowOn.isNarrowerThan4to3()) 0.45f else 0.8f)
    private val civBlocksWidth = playerPicker.civBlocksWidth
    private val nationListTable = Table()
    private val nationListScroll = ScrollPane(nationListTable)
    private val nationDetailsTable = Table()
    private val nationDetailsScroll = ScrollPane(nationDetailsTable)
    private var selectedNation: Nation? = null

    init {
        nationListScroll.setOverscroll(false, false)
        add(nationListScroll).size( civBlocksWidth + 10f, partHeight )
            // +10, because the nation table has a 5f pad, for a total of +10f
        if (stageToShowOn.isNarrowerThan4to3()) row()
        nationDetailsScroll.setOverscroll(false, false)
        add(nationDetailsScroll).size(civBlocksWidth + 10f, partHeight) // Same here, see above

        val randomNation = Nation().apply {
            name = Constants.random
            innerColor = listOf(255, 255, 255)
            outerColor = listOf(0, 0, 0)
            setTransients()
        }
        val nations = ArrayList<Nation>()
        if (!playerPicker.noRandom) nations += randomNation
        val spectator = previousScreen.ruleset.nations[Constants.spectator]
        if (spectator != null) nations += spectator

        nations += playerPicker.getAvailablePlayerCivs(player.chosenCiv)
            .sortedWith(compareBy(UncivGame.Current.settings.getCollatorFromLocale()) { it.name.tr() })

        var nationListScrollY = 0f
        var currentY = 0f
        for (nation in nations) {
            // only humans can spectate, sorry robots
            if (player.playerType == PlayerType.AI && nation.isSpectator())
                continue
            if (player.chosenCiv == nation.name)
                nationListScrollY = currentY
            val nationTable = NationTable(nation, civBlocksWidth, 0f) // no need for min height
            val cell = nationListTable.add(nationTable)
            currentY += cell.padBottom + cell.prefHeight + cell.padTop
            cell.row()
            nationTable.onClick {
                setNationDetails(nation)
            }
            if (player.chosenCiv == nation.name)
                setNationDetails(nation)
        }

        nationListScroll.layout()
        pack()
        if (nationListScrollY > 0f) {
            // center the selected nation vertically, getRowHeight safe because nationListScrollY > 0f ensures at least 1 row
            nationListScrollY -= (nationListScroll.height - nationListTable.getRowHeight(0)) / 2
            nationListScroll.scrollY = nationListScrollY.coerceIn(0f, nationListScroll.maxY)
        }

        val closeButton = "OtherIcons/Close".toImageButton(Color.FIREBRICK)
        closeButton.onActivation { close() }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)
        closeButton.setPosition(buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomLeft)
        innerTable.addActor(closeButton)

        val okButton = "OtherIcons/Checkmark".toImageButton(Color.LIME)
        okButton.onClick { returnSelected() }
        okButton.setPosition(innerTable.width - buttonsOffsetFromEdge, buttonsOffsetFromEdge, Align.bottomRight)
        innerTable.addActor(okButton)

        nationDetailsTable.touchable = Touchable.enabled
        nationDetailsTable.onClick { returnSelected() }
    }

    private fun String.toImageButton(overColor: Color): Group {
        val style = ImageButton.ImageButtonStyle()
        val image = ImageGetter.getDrawable(this)
        style.imageUp = image
        style.imageOver = image.tint(overColor)
        val button = ImageButton(style)
        button.setSize(buttonsIconSize, buttonsIconSize)

        return button.surroundWithCircle(buttonsCircleSize, false, buttonsBackColor)
    }

    private fun setNationDetails(nation: Nation) {
        nationDetailsTable.clearChildren()  // .clear() also clears listeners!

        nationDetailsTable.add(NationTable(nation, civBlocksWidth, partHeight, ruleset))
        selectedNation = nation
    }

    private fun returnSelected() {
        if (selectedNation == null) return

        UncivGame.Current.musicController.chooseTrack(selectedNation!!.name, MusicMood.themeOrPeace, MusicTrackChooserFlags.setSelectNation)

        if (previousScreen is GameParametersScreen)
            previousScreen.mapEditorScreen.tileMap.switchPlayersNation(
                player,
                selectedNation!!
            )
        player.chosenCiv = selectedNation!!.name
        close()
        playerPicker.update()
    }
}

package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.GameOverviewResponse
import com.unciv.models.translations.tr
import com.unciv.ui.components.ArrowButton
import com.unciv.ui.components.ChatButton
import com.unciv.ui.components.NewButton
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.screens.worldscreen.status.MultiplayerStatusButtonV2
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import java.util.UUID

/**
 * Screen holding an overview of the current game's multiplayer functionality
 *
 * It's mainly used in the [MultiplayerStatusButtonV2], but could be embedded
 * somewhere else as well. It requires the [UUID] of the currently playing user,
 * the initially shown chat room, which should be the game's chat room, and
 * the list of playing major civilisations in the game. Note that this list
 * should include the fallen major civilisations, because it allows
 * communication even with players who already lost the game. Those players
 * will still receive game updates, but won't be able to perform any moves
 * and aren't required to perform turns, i.e. the game will silently continue
 * without them properly.
 */
class MultiplayerGameScreen(private val me: UUID, initialChatRoom: Triple<UUID, ChatRoomType, String>? = null, civilizations: List<Civilization>) : PickerScreen(horizontally = true), Disposable {
    private val playerTable = Table()
    private val friendList = FriendListV2(
        this,
        me,
        requests = true,
        chat = { _, a, c -> startFriendChatting(c, a.displayName) },
        edit = { f, a -> FriendListV2.showRemoveFriendshipPopup(f, a, this) }
    )
    private val helpButton = "Help".toTextButton().onClick {
        val helpPopup = Popup(this)
        helpPopup.addGoodSizedLabel("It would be nice if this screen was documented.").row()
        helpPopup.addCloseButton()
        helpPopup.open()
    }
    private val gameList = GameListV2(this, ::loadGame)
    private val gameListButton = "Games".toTextButton().onClick {
        val gameListPopup = Popup(this)
        gameListPopup.add(gameList).row()
        gameListPopup.addCloseButton()
        gameListPopup.open()
    }

    init {
        Concurrency.run {
            Concurrency.runOnGLThread { stage }  // accessing the stage here avoids errors later
            populatePlayerTable(civilizations, stage)
        }

        topTable.add(playerTable).padRight(10f).expandY()
        if (initialChatRoom != null) {
            topTable.add(
                ChatTable(
                    ChatMessageList(true, Pair(ChatRoomType.Game, initialChatRoom.third), initialChatRoom.first, this.game.onlineMultiplayer)
                )
            ).growX().expandY()
        } else {
            topTable.add("Chat not found".toLabel()).grow().expandY()
        }

        setDefaultCloseAction()
        rightSideButton.setText("Friends".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            Concurrency.run {
                friendList.triggerUpdate(true)
            }
            val popup = Popup(this)
            popup.add(friendList).growX().minWidth(this.stage.width * 0.5f).row()
            popup.addCloseButton()
            popup.open()
        }
        rightSideGroup.addActor(Container(gameListButton).padRight(5f).padLeft(5f))
        rightSideGroup.addActor(Container(helpButton).padRight(5f).padLeft(5f))
    }

    private fun startFriendChatting(chatRoom: UUID, name: String) {
        val popup = Popup(this)
        popup.add(
            ChatTable(
                ChatMessageList(true, Pair(ChatRoomType.Friend, name), chatRoom, this.game.onlineMultiplayer)
            )
        ).row()
        popup.addCloseButton()
        popup.open(force = true)
    }

    private suspend fun populatePlayerTable(civilizations: List<Civilization>, stage: Stage) {
        val friendsOnline = InfoPopup.wrap(stage) {
            game.onlineMultiplayer.api.friend.list()
        }?.first
        val playerMap: MutableMap<String, AccountResponse> = mutableMapOf()
        for (civ in civilizations) {
            if (civ.playerType != PlayerType.Human) {
                continue
            }
            val playerAccount = InfoPopup.wrap(stage) {
                game.onlineMultiplayer.api.account.lookup(UUID.fromString(civ.playerId))
            }
            if (playerAccount != null) {
                playerMap[civ.playerId] = playerAccount
            }
        }

        Concurrency.runOnGLThread {
            var firstDone = false
            for (civ in civilizations) {
                if (civ.playerType != PlayerType.Human) {
                    continue
                }
                val playerAccount = playerMap[civ.playerId] ?: throw RuntimeException("Player ID ${civ.playerId} not found")
                if (firstDone) {
                    playerTable.addSeparator(color = Color.LIGHT_GRAY).colspan(4).padLeft(30f).padRight(30f).padTop(10f).padBottom(10f).row()
                }
                firstDone = true

                val identifiactionTable = Table(skin)
                identifiactionTable.add(civ.civName).padBottom(5f).padLeft(15f).padRight(15f).colspan(2).row()
                val playerNameCell = identifiactionTable.add(playerAccount.displayName).padLeft(15f).padRight(10f)
                if (friendsOnline != null && UUID.fromString(civ.playerId) in friendsOnline.filter { it.friend.online }.map { it.friend.uuid }) {
                    identifiactionTable.add("Online").padRight(15f)
                } else if (friendsOnline != null && UUID.fromString(civ.playerId) in friendsOnline.filter { !it.friend.online }.map { it.friend.uuid }) {
                    identifiactionTable.add("Offline").padRight(15f)
                } else {
                    playerNameCell.colspan(2).padRight(15f)
                }

                val civImage = Stack()
                civImage.addActor(ImageGetter.getNationPortrait(civ.nation, 50f))
                if (!civ.isAlive()) {
                    civImage.addActor(ImageGetter.getImage("OtherIcons/Close").apply {
                        setOrigin(Align.center)
                        setSize(50f)
                        color = Color.RED
                    })
                }
                playerTable.add(civImage).padLeft(20f).padRight(5f)
                playerTable.add(identifiactionTable).padRight(5f)

                if (civ.playerId != me.toString()) {
                    playerTable.add(ChatButton().apply {
                        onClick {
                            // TODO: Implement 1:1 chats (also not supported by runciv at the moment)
                            Log.debug("The 1:1 in-game chat with ${civ.playerId} is not implemented yet")
                            ToastPopup("Sorry, 1:1 in-game chats are not implemented yet", stage).open()
                        }
                    }).padLeft(5f).padRight(5f)
                    if (friendsOnline != null && UUID.fromString(civ.playerId) in friendsOnline.map { it.friend.uuid }) {
                        playerTable.add(ArrowButton().apply {
                            onClick {
                                val friend = friendsOnline.filter { it.friend.uuid == UUID.fromString(civ.playerId) }[0]
                                startFriendChatting(friend.chatUUID, friend.friend.displayName)
                            }
                        }).padRight(20f).row()
                    } else if (friendsOnline != null) {
                        playerTable.add(NewButton().apply {
                            onClick {
                                ConfirmPopup(
                                    stage,
                                    "Do you want to send [${playerAccount.username}] a friend request?",
                                    "Yes",
                                    true
                                ) {
                                    InfoPopup.load(stage) {
                                        game.onlineMultiplayer.api.friend.request(playerAccount.uuid)
                                    }
                                }.open(force = true)
                            }
                        }).padRight(20f).row()
                    } else {
                        playerTable.add().padRight(20f).row()
                    }
                } else {
                    playerTable.add()
                    playerTable.add().row()
                }
            }
        }
    }

    private fun loadGame(gameOverview: GameOverviewResponse) {
        val gameInfo = InfoPopup.load(stage) {
            game.onlineMultiplayer.downloadGame(gameOverview.gameUUID.toString())
        }
        if (gameInfo != null) {
            Concurrency.runOnNonDaemonThreadPool {
                game.loadGame(gameInfo)
            }
        }
    }

    override fun dispose() {
        gameList.dispose()
        super.dispose()
    }
}

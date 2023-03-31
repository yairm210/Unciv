package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.multiplayer.apiv2.ChatMessage
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.AutoScrollPane
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.newgamescreen.GameOptionsTable
import com.unciv.ui.screens.newgamescreen.MapOptionsInterface
import com.unciv.ui.screens.newgamescreen.MapOptionsTable
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Log
import java.util.*


/**
 * Lobby screen for open lobbies
 *
 * On the left side, it provides a list of players and their selected civ.
 * On the right side, it provides a chat bar for multiplayer lobby chats.
 * Between those, there are three menu buttons for a) game settings,
 * b) map settings and c) to start the game. It also has a footer section
 * like the [PickerScreen] but smaller, with a leave button on the left and
 * two buttons for the social tab and the in-game help on the right side.
 */
class LobbyScreen(private val lobbyUUID: UUID, private val lobbyChatUUID: UUID, override val gameSetupInfo: GameSetupInfo): BaseScreen(), MapOptionsInterface {

    constructor(lobbyUUID: UUID, lobbyChatUUID: UUID) : this(lobbyUUID, lobbyChatUUID, GameSetupInfo.fromSettings())

    override var ruleset = RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters)

    private val gameOptionsTable = GameOptionsTable(this, updatePlayerPickerTable = { x ->
        Log.error("Updating player picker table with '%s' is not implemented yet.", x)
    })
    private val mapOptionsTable = MapOptionsTable(this)

    private val lobbyName: String = "My new lobby"  // TODO: Get name by looking up the UUID
    private val chatMessages: MutableList<ChatMessage> = mutableListOf()
    private val players: MutableList<Player> = mutableListOf()

    private val screenTitle = "Lobby: $lobbyName".toLabel(fontSize = Constants.headingFontSize)
    private val chatMessageList = ChatMessageList(lobbyChatUUID)
    private val menuButtonGameOptions = "Game options".toTextButton()
    private val menuButtonMapOptions = "Map options".toTextButton()
    private val menuButtonStartGame = "Start game".toTextButton()
    private val bottomButtonLeave = "Leave".toTextButton()
    private val bottomButtonSocial = "Social".toTextButton()
    private val bottomButtonHelp = "Help".toTextButton()

    init {
        menuButtonGameOptions.onClick {
            WrapPopup(stage, gameOptionsTable)
        }
        menuButtonMapOptions.onClick {
            WrapPopup(stage, mapOptionsTable)
        }
        menuButtonStartGame.onActivation {
            ToastPopup("The start game feature has not been implemented yet.", stage)
        }

        bottomButtonLeave.keyShortcuts.add(KeyCharAndCode.ESC)
        bottomButtonLeave.keyShortcuts.add(KeyCharAndCode.BACK)
        bottomButtonLeave.onActivation {
            game.popScreen()
        }
        bottomButtonSocial.onActivation {
            ToastPopup("The social feature has not been implemented yet.", stage)
        }
        bottomButtonHelp.keyShortcuts.add(Input.Keys.F1)
        bottomButtonHelp.onActivation {
            ToastPopup("The help feature has not been implemented yet.", stage)
        }

        recreate()
    }

    private class WrapPopup(stage: Stage, other: Actor, action: (() -> Unit)? = null) : Popup(stage) {
        init {
            innerTable.add(other).center().expandX().row()
            addCloseButton(action = action)
            open()
        }
    }

    fun recreate(): BaseScreen {
        val table = Table()
        table.setFillParent(true)
        stage.addActor(table)

        val players = VerticalGroup()
        val playerScroll = AutoScrollPane(players)
        playerScroll.setScrollingDisabled(true, false)

        val optionsTable = VerticalGroup().apply {
            align(Align.center)
            space(10f)
        }
        optionsTable.addActor(menuButtonGameOptions)
        optionsTable.addActor(menuButtonMapOptions)
        optionsTable.addActor(menuButtonStartGame)

        val chatScroll = AutoScrollPane(chatMessageList, skin)
        chatScroll.setScrollingDisabled(true, false)

        val menuBar = Table()
        menuBar.align(Align.bottom)
        menuBar.add(bottomButtonLeave).pad(10f)
        menuBar.add().fillX().expandX()
        menuBar.add(bottomButtonSocial).pad(5f)  // half padding since the help button has padding as well
        menuBar.add(bottomButtonHelp).pad(10f)

        // Construct the table which makes up the whole lobby screen
        table.row()
        table.add(Container(screenTitle).pad(10f)).colspan(4).fillX()
        table.row().expandX().expandY()
        table.add(playerScroll).fillX().expandY().prefWidth(stage.width * 0.475f).padLeft(5f)
        table.add(optionsTable).prefWidth(0.05f * stage.width)
        table.addSeparatorVertical(skinStrings.skinConfig.baseColor.brighten(0.1f), width = 0.5f).height(0.5f * stage.height)
        table.add(chatScroll).fillX().expandY().prefWidth(stage.width * 0.475f).padRight(5f)
        table.addSeparator(skinStrings.skinConfig.baseColor.brighten(0.1f), height = 0.5f).width(stage.width * 0.85f).padTop(10f).row()
        table.row().bottom().fillX().maxHeight(stage.height / 8)
        table.add(menuBar).colspan(4).fillX()
        table.pack()
        table.invalidate()
        return this
    }

    override fun lockTables() {
        Log.error("Not yet implemented")
    }

    override fun unlockTables() {
        Log.error("Not yet implemented")
    }

    override fun updateTables() {
        Log.error("Not yet implemented")
    }

    override fun updateRuleset() {
        Log.error("Not yet implemented")
    }

}

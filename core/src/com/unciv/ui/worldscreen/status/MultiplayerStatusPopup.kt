package com.unciv.ui.worldscreen.status

import com.unciv.UncivGame
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.models.translations.tr
import com.unciv.ui.multiplayer.GameList
import com.unciv.ui.multiplayer.MultiplayerHelpers
import com.unciv.ui.pickerscreens.PickerPane
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.onClick

class MultiplayerStatusPopup(
    screen: BaseScreen,
) : Popup(screen) {

    val pickerPane = PickerPane()
    var selectedGame: OnlineMultiplayerGame? = null

    init {
        val pickerCell = add()
            .width(700f).fillX().expandX()
            .minHeight(screen.stage.height * 0.5f)
            .maxHeight(screen.stage.height * 0.8f)

        val gameList = GameList(::gameSelected)
        pickerPane.topTable.add(gameList)
        pickerPane.rightSideButton.setText("Load game".tr())
        pickerPane.closeButton.onClick(::close)
        pickerCell.setActor<PickerPane>(pickerPane)
        pickerPane.rightSideButton.onClick {
            close()
            val game = selectedGame
            if (game != null) {
                MultiplayerHelpers.loadMultiplayerGame(screen, game)
            }
        }
    }

    private fun gameSelected(gameName: String) {
        val multiplayerGame = UncivGame.Current.onlineMultiplayer.getGameByName(gameName)!!
        selectedGame = multiplayerGame
        pickerPane.setRightSideButtonEnabled(true)
        pickerPane.rightSideButton.setText("Load [$gameName]".tr())
        pickerPane.descriptionLabel.setText(MultiplayerHelpers.buildDescriptionText(multiplayerGame))
    }

}

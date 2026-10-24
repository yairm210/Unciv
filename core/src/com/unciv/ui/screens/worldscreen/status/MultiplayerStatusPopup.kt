package com.unciv.ui.screens.worldscreen.status

import com.unciv.UncivGame
import com.unciv.logic.multiplayer.MultiplayerGamePreview
import com.unciv.models.translations.tr
import com.unciv.ui.screens.multiplayerscreens.GameList
import com.unciv.ui.screens.multiplayerscreens.MultiplayerHelpers
import com.unciv.ui.screens.pickerscreens.PickerPane
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.input.onClick

class MultiplayerStatusPopup(
    screen: BaseScreen,
) : Popup(screen) {

    val pickerPane = PickerPane()
    var selectedGame: MultiplayerGamePreview? = null

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
        val multiplayerGame = UncivGame.Current.onlineMultiplayer.multiplayerFiles.getGameByName(gameName)!!
        selectedGame = multiplayerGame
        pickerPane.setRightSideButtonEnabled(true)
        pickerPane.rightSideButton.setText("Load [$gameName]".tr())
        pickerPane.descriptionLabel.setText(MultiplayerHelpers.buildDescriptionText(multiplayerGame))
    }

}

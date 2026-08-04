package com.unciv.ui.screens.pickerscreens

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.multiplayer.AuthoritativeUnitActions
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

class GreatPersonPickerScreen(val worldScreen: WorldScreen, val civInfo: Civilization) : PickerScreen() {
    private var theChosenOne: BaseUnit? = null

    init {
        worldScreen.autoPlay.stopAutoPlay()
        closeButton.isVisible = false
        rightSideButton.setText("Choose a free great person".tr())

        val greatPersonUnits = civInfo.greatPeople.getGreatPeople()
        val useMayaLongCount = civInfo.greatPeople.mayaLimitedFreeGP > 0

        for (unit in greatPersonUnits) {
            val button =
                PickerPane.getPickerOptionButton(ImageGetter.getUnitIcon(unit), unit.name)
            button.pack()
            button.isEnabled = !useMayaLongCount || unit.name in civInfo.greatPeople.longCountGPPool
            if (button.isEnabled) {
                button.onClick {
                    theChosenOne = unit
                    pick("Get [${unit.name}]".tr())
                    descriptionLabel.setText(unit.getShortDescription())
                }

                button.onDoubleClick(UncivSound.Choir) { confirmAction(useMayaLongCount) }
            }
            topTable.add(button).pad(10f).row()
        }

        rightSideButton.onClick(UncivSound.Choir) {
            confirmAction(useMayaLongCount)
        }

    }

    private fun confirmAction(useMayaLongCount: Boolean) {
        val unit = theChosenOne!!
        civInfo.units.addUnit(unit, civInfo.getCapital())
        civInfo.greatPeople.freeGreatPeople--
        if (useMayaLongCount) {
            civInfo.greatPeople.mayaLimitedFreeGP--
            civInfo.greatPeople.longCountGPPool.remove(unit.name)
        }
        syncGreatPersonToServer(unit.name, useMayaLongCount)
        UncivGame.Current.popScreen()
    }

    private fun syncGreatPersonToServer(unitName: String, mayaLimited: Boolean) {
        if (!AuthoritativeUnitActions.isEnabled(civInfo.gameInfo)) return
        val gameId = civInfo.gameInfo.gameId
        Concurrency.run("AuthChooseGreatPerson") {
            var errMsg = "Server-authoritative chooseGreatPerson failed"
            val newSave = AuthoritativeUnitActions.requestChooseGreatPerson(
                gameId, unitName, mayaLimited
            ) { errMsg = it }
            launchOnGLThread {
                if (newSave == null) {
                    val screen = UncivGame.Current.screen
                    if (screen != null) ToastPopup(errMsg, screen)
                    AuthoritativeUnitActions.scheduleMidTurnSync()
                    return@launchOnGLThread
                }
                UncivGame.Current.loadGame(AuthoritativeUnitActions.loadReturnedSave(newSave))
            }
        }
    }
}

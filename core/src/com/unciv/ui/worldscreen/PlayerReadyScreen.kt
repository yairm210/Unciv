package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import java.security.MessageDigest

class PlayerReadyScreen(worldScreen: WorldScreen) : BaseScreen() {
    var startTurn = true
    val curCiv = worldScreen.viewingCiv
    var enteredPassword = curCiv.hotseatPassword

    init {
        val table = Table()
        table.touchable = Touchable.enabled
        table.background = ImageGetter.getBackground(curCiv.nation.getOuterColor())

        table.add("[$curCiv] ready?".toLabel(curCiv.nation.getInnerColor(), Constants.headingFontSize)).padBottom(20f).row()

        val createPasswordButton = "Set password (optional)".toTextButton()
        val savePasswordButton = "Save password".toTextButton()
        savePasswordButton.onClick {
            startTurn = false
            curCiv.hotseatPassword = hash(enteredPassword)
        }

        createPasswordButton.onClick {
            startTurn = false
            table.removeActor(createPasswordButton)
            table.add(getPasswordTable()).row()
            if (enteredPassword == "")
                table.add(savePasswordButton).padTop(10f).row()
        }

        if (enteredPassword == "")
            table.add(createPasswordButton).row()
        else
            table.add(getPasswordTable())

        table.onClick {
            if (startTurn) {
                if (enteredPassword == curCiv.hotseatPassword)
                    game.replaceCurrentScreen(worldScreen)
                if (hash(enteredPassword) == curCiv.hotseatPassword)
                    game.replaceCurrentScreen(worldScreen)
                else
                    ToastPopup("Wrong password!", this)
            }
            else
                startTurn = true
        }
        table.setFillParent(true)
        stage.addActor(table)
    }

    private fun getPasswordTable(): Table {
        val passwordTable = Table()

        passwordTable.add("Password: ".toLabel())
        val passwordTextField = UncivTextField.create("Password")
        passwordTextField.addListener { enteredPassword = passwordTextField.text; true }

        passwordTextField.onClick { startTurn = false }
        passwordTable.add(passwordTextField).row()

        return passwordTable
    }

    fun hash(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}

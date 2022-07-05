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
    var startTurn = true // used to disable screen change when clicking on the textbox or buttons
    val currentCiv = worldScreen.viewingCiv
    var password = currentCiv.hotseatPassword
    var confirmPassword = ""

    init {
        val table = Table()
        table.touchable = Touchable.enabled
        table.background = ImageGetter.getBackground(currentCiv.nation.getOuterColor())

        table.add("[$currentCiv] ready?".toLabel(currentCiv.nation.getInnerColor(), Constants.headingFontSize)).padBottom(20f).row()

        val savePasswordButton = "Save password".toTextButton()
        savePasswordButton.onClick {
            startTurn = false
            if (password == confirmPassword) {
                currentCiv.hotseatPassword = hash(password)
                game.replaceCurrentScreen(worldScreen)
            } else {
                ToastPopup("Passwords do not match!", this)
            }
        }

        val createPasswordButton = "Set password (optional)".toTextButton()
        createPasswordButton.onClick {
            startTurn = false
            table.removeActor(createPasswordButton)
            table.add(getPasswordTable()).row()
            if (password == "")
                table.add(savePasswordButton).padTop(10f).row()
        }

        val removePasswordButton = "Remove password".toTextButton()
        removePasswordButton.onClick {
            if (hash(password) == currentCiv.hotseatPassword) {
                currentCiv.hotseatPassword = ""
            } else {
                startTurn = false
                ToastPopup("Wrong password!", this)
            }
        }

        if (password == "") {
            table.add(createPasswordButton).row()
        } else {
            table.add(getPasswordTable()).row()
            table.add(removePasswordButton).padTop(10f)
        }

        table.onClick {
            if (startTurn) {
                if (currentCiv.hotseatPassword == "")
                    game.replaceCurrentScreen(worldScreen)
                if (hash(password) == currentCiv.hotseatPassword)
                    game.replaceCurrentScreen(worldScreen)
                else
                    ToastPopup("Wrong password!", this)
            }
            else {
                startTurn = true
            }
        }
        table.setFillParent(true)
        stage.addActor(table)
    }

    private fun getPasswordTable(): Table {
        val passwordTable = Table()

        passwordTable.add("Password ".toLabel(currentCiv.nation.getInnerColor(), Constants.headingFontSize)).padTop(10f)

        val passwordTextField = UncivTextField.create("Password")
        passwordTextField.isPasswordMode = true
        passwordTextField.setPasswordCharacter('*')
        passwordTextField.addListener { password = passwordTextField.text; true }
        passwordTextField.onClick { startTurn = false }

        passwordTable.add(passwordTextField).padTop(10f).row()

        if (currentCiv.hotseatPassword == "") {
            passwordTable.add("Confirm password ".toLabel(currentCiv.nation.getInnerColor(), Constants.headingFontSize)).padTop(10f)

            val confirmPasswordTextField = UncivTextField.create("Confirm password")
            confirmPasswordTextField.isPasswordMode = true
            confirmPasswordTextField.setPasswordCharacter('*')
            confirmPasswordTextField.addListener { confirmPassword = confirmPasswordTextField.text; true }
            confirmPasswordTextField.onClick { startTurn = false }

            passwordTable.add(confirmPasswordTextField).padTop(10f).row()
        }

        return passwordTable
    }

    fun hash(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}

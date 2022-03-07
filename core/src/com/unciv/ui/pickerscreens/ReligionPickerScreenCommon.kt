package com.unciv.ui.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.ReligionManager
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.BeliefType
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

abstract class ReligionPickerScreenCommon(
    private val choosingCiv: CivilizationInfo,
    disableScroll: Boolean = false
) : PickerScreen(disableScroll) {

    protected val gameInfo = choosingCiv.gameInfo
    protected val ruleset = gameInfo.ruleSet

    protected class Selection {
        var button: Button? = null
            private set
        fun switch(newButton: Button) {
            button?.enable()
            button = newButton
            newButton.disable()
        }
        fun clear() { button = null }
        fun isEmpty() = button == null
    }

    init {
        closeButton.isVisible = true
        setDefaultCloseAction()
    }

    protected fun setOKAction(buttonText: String, action: ReligionManager.() -> Unit) {
        rightSideButton.setText(buttonText.tr())
        rightSideButton.onClick(UncivSound.Choir) {
            choosingCiv.religionManager.action()
            UncivGame.Current.setWorldScreen()
            dispose()
        }
    }

    protected fun getBeliefButton(
        belief: Belief? = null,
        beliefType: BeliefType? = null,
        withTypeLabel: Boolean = true
    ): Button {
        val labelWidth = stage.width * 0.5f - 52f  // 32f empirically measured padding inside button, 20f outside padding
        return Button(skin).apply {
            when {
                belief != null -> {
                    if (withTypeLabel)
                        add(belief.type.name.toLabel(fontColor = Color.valueOf(belief.type.color))).row()
                    val nameLabel = WrappableLabel(belief.name, labelWidth, fontSize = Constants.headingFontSize)
                    add(nameLabel.apply { wrap = true }).row()
                    val effectLabel = WrappableLabel(belief.uniques.joinToString("\n") { it.tr() }, labelWidth)
                    add(effectLabel.apply { wrap = true })
                }
                beliefType == BeliefType.Any ->
                    add("Choose any belief!".toLabel())
                beliefType != null ->
                    add("Choose a [$beliefType] belief!".toLabel())
                else -> throw(IllegalArgumentException("getBeliefButton must have one non-null parameter"))
            }
        }
    }

    protected fun Button.onClickSelect(selection: Selection, function: () -> Unit) {
        onClick {
            selection.switch(this)
            function()
        }
    }

    /** Disable a [Button] by setting its [touchable][Button.touchable] and the given [color] */
    protected fun Button.disable(color: Color) {
        touchable = Touchable.disabled
        this.color = color
    }

    // This forces this label to use an own clone of the default style. If not, hovering over the button will gray
    // out all the default-styled Labels on the screen - exact cause and why this does not affect other buttons unknown.
    // Note - only used for "Choose a [$beliefType] belief!"," "Choose an Icon and name..", "Found [$newReligionName]"
    // and displayName, and those are the labels _not_ affected by the quirk!
    protected fun String.toLabel() = Label(this.tr(), LabelStyle(skin[LabelStyle::class.java]))

    protected companion object {
        val redDisableColor = Color.RED.darken(0.25f)
        val greenDisableColor = Color.GREEN.darken(0.25f)
    }
}

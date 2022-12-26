package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerX
import com.unciv.ui.utils.extensions.toLabel

class TurnProcessingWidget(val worldScreen: WorldScreen) : Table() {

    private val iconGroup = Group()
    private val textLabel = "".toLabel()

    init {

        iconGroup.setSize(50f, 50f)

        add(iconGroup).padBottom(5f).row()
        add(textLabel)
        pack()
    }

    fun update(activeCiv: CivilizationInfo? = null) {
        isVisible = UncivGame.Current.isTurnProcessing

        if (isVisible && activeCiv != null) {
            val selectedCiv = worldScreen.selectedCiv
            val doWeKnowEachOther = selectedCiv.knows(activeCiv)
            val icon = when {
                doWeKnowEachOther || activeCiv.isCityState() || activeCiv.isBarbarian() -> ImageGetter.getNationIndicator(activeCiv.nation, 50f)
                else -> ImageGetter.getRandomNationIndicator(50f)
            }
            val text = when {
                activeCiv.isCityState() ->"Process Turn for [{City-States}]".tr()
                doWeKnowEachOther || activeCiv.isBarbarian() -> "Process Turn for [$activeCiv]".tr()
                else -> "Process Turn for [{Unknown civilization}]".tr()
            }

            setIcon(icon)
            setText(text)

            y = 15f
            centerX(stage)
        }
    }

    fun setIcon(icon: Group) {
        iconGroup.clearChildren()
        iconGroup.addActor(icon)
        icon.center(iconGroup)
    }

    fun setText(text: String) {
        textLabel.setText(text)
    }

}

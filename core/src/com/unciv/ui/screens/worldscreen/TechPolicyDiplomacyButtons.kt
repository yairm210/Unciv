package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.UncivSound
import com.unciv.models.translations.tr
import com.unciv.ui.components.BaseScreen
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.overviewscreen.EspionageOverviewScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.TechButton
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.utils.concurrency.Concurrency


/** A holder for Tech, Policies and Diplomacy buttons going in the top left of the WorldScreen just under WorldScreenTopBar */
class TechPolicyDiplomacyButtons(val worldScreen: WorldScreen) : Table(BaseScreen.skin) {
    private val techButtonHolder = Container<Table?>()
    private val pickTechButton = Table(skin)
    private val pickTechLabel = "".toLabel(Color.WHITE, 30)

    private val policyButtonHolder = Container<Button?>()
    private val policyScreenButton = Button(skin)
    private val diplomacyButtonHolder = Container<Button?>()
    private val diplomacyButton = Button(skin)
    private val undoButtonHolder = Container<Button?>()
    private val undoButton = Button(skin)
    private val espionageButtonHolder = Container<Button?>()
    private val espionageButton = Button(skin)

    private val viewingCiv = worldScreen.viewingCiv
    private val game = worldScreen.game

    init {
        defaults().left()
        add(techButtonHolder).colspan(4).row()
        add(undoButtonHolder).padTop(10f).padRight(10f)
        add(policyButtonHolder).padTop(10f).padRight(10f)
        add(diplomacyButtonHolder).padTop(10f).padRight(10f)
        add(espionageButtonHolder).padTop(10f)
        add().growX()  // Allows Policy and Diplo buttons to keep to the left

        pickTechButton.background = BaseScreen.skinStrings.getUiBackground("WorldScreen/PickTechButton", BaseScreen.skinStrings.roundedEdgeRectangleShape, colorFromRGB(7, 46, 43))
        pickTechButton.defaults().pad(20f)
        pickTechButton.add(pickTechLabel)
        techButtonHolder.onClick(UncivSound.Paper) {
            game.pushScreen(TechPickerScreen(viewingCiv))
        }

        undoButton.add(ImageGetter.getImage("OtherIcons/Resume")).size(30f).pad(15f)
        undoButton.onClick {
            Concurrency.run {
                // Most of the time we won't load this, so we only set transients once we see it's relevant
                worldScreen.preActionGameInfo.setTransients()
                game.loadGame(worldScreen.preActionGameInfo)
            }
        }

        policyScreenButton.add(ImageGetter.getImage("PolicyIcons/Constitution")).size(30f).pad(15f)
        policyButtonHolder.onClick {
            game.pushScreen(PolicyPickerScreen(worldScreen))
        }

        diplomacyButton.add(ImageGetter.getImage("OtherIcons/DiplomacyW")).size(30f).pad(15f)
        diplomacyButtonHolder.onClick {
            game.pushScreen(DiplomacyScreen(viewingCiv))
        }
        if (game.gameInfo!!.isEspionageEnabled()) {
            espionageButton.add(ImageGetter.getImage("OtherIcons/Spy_White")).size(30f).pad(15f)
            espionageButtonHolder.onClick {
                game.pushScreen(EspionageOverviewScreen(viewingCiv))
            }
        }
    }

    fun update(): Boolean {
        updateUndoButton()
        updateTechButton()
        updatePolicyButton()
        val result = updateDiplomacyButton()
        if (game.gameInfo!!.isEspionageEnabled())
            updateEspionageButton()
        pack()
        setPosition(10f, worldScreen.topBar.y - height - 15f)
        return result
    }

    private fun updateTechButton() {
        techButtonHolder.touchable = Touchable.disabled
        techButtonHolder.actor = null
        if (worldScreen.gameInfo.ruleset.technologies.isEmpty() || viewingCiv.cities.isEmpty()) return
        techButtonHolder.touchable = Touchable.enabled

        if (viewingCiv.tech.currentTechnology() != null) {
            val currentTech = viewingCiv.tech.currentTechnologyName()!!
            val innerButton = TechButton(currentTech, viewingCiv.tech)
            innerButton.setButtonColor(colorFromRGB(7, 46, 43))
            techButtonHolder.actor = innerButton
            val turnsToTech = viewingCiv.tech.turnsToTech(currentTech)
            innerButton.text.setText(currentTech.tr())
            innerButton.turns.setText(turnsToTech + Fonts.turn)
        } else {
            val canResearch = viewingCiv.tech.canResearchTech()
            if (canResearch || viewingCiv.tech.researchedTechnologies.size != 0) {
                val text = if (canResearch) "{Pick a tech}!" else "Technologies"
                pickTechLabel.setText(text.tr())
                techButtonHolder.actor = pickTechButton
            }
        }
    }

    private fun updateUndoButton() {
        // Don't show policies until they become relevant
        if (worldScreen.gameInfo != worldScreen.preActionGameInfo) {
            undoButtonHolder.touchable = Touchable.enabled
            undoButtonHolder.actor = undoButton
        } else {
            undoButtonHolder.touchable = Touchable.disabled
            undoButtonHolder.actor = null
        }
    }

    private fun updatePolicyButton() {
        // Don't show policies until they become relevant
        if (viewingCiv.policies.adoptedPolicies.isNotEmpty() || viewingCiv.policies.canAdoptPolicy()) {
            policyButtonHolder.touchable = Touchable.enabled
            policyButtonHolder.actor = policyScreenButton
        } else {
            policyButtonHolder.touchable = Touchable.disabled
            policyButtonHolder.actor = null
        }
    }

    private fun updateDiplomacyButton(): Boolean {
        return if (viewingCiv.isDefeated() || viewingCiv.isSpectator()
                || viewingCiv.getKnownCivs().filterNot { it == viewingCiv || it.isBarbarian() }.none()
        ) {
            diplomacyButtonHolder.touchable = Touchable.disabled
            diplomacyButtonHolder.actor = null
            false
        } else {
            diplomacyButtonHolder.touchable = Touchable.enabled
            diplomacyButtonHolder.actor = diplomacyButton
            true
        }
    }

    private fun updateEspionageButton() {
        if (viewingCiv.espionageManager.spyCount == 0) {
            espionageButtonHolder.touchable = Touchable.disabled
            espionageButtonHolder.actor = null
        } else {
            espionageButtonHolder.touchable = Touchable.enabled
            espionageButtonHolder.actor = espionageButton
        }
    }
}

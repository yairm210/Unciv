package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.UncivSlider
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

fun autoPlayTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings
    fun addAutoPlaySections() {
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Military",
            settings.autoPlayMilitary, false
        ) { settings.autoPlayMilitary = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Civilian",
            settings.autoPlayCivilian, false
        ) { settings.autoPlayCivilian = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Economy",
            settings.autoPlayEconomy, false
        ) { settings.autoPlayEconomy = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Diplomacy",
            settings.autoPlayDiplomacy, false
        ) { settings.autoPlayDiplomacy = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Technology",
            settings.autoPlayTechnology, false
        ) { settings.autoPlayTechnology = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Policies",
            settings.autoPlayPolicies, false
        ) { settings.autoPlayPolicies = it }
        optionsPopup.addCheckbox(
            this,
            "AutoPlay Religion",
            settings.autoPlayReligion, false
        ) { settings.autoPlayReligion = it }
    }
    
    addAutoPlayMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth)
    optionsPopup.addCheckbox(
        this,
        "Full AutoPlay AI",
        settings.fullAutoPlayAI, false
    ) { settings.fullAutoPlayAI = it
        if (!it) addAutoPlaySections() 
        else optionsPopup.tabs.replacePage(optionsPopup.tabs.activePage, autoPlayTab(optionsPopup))
    }
    if (!settings.fullAutoPlayAI)
        addAutoPlaySections()
}

private fun addAutoPlayMaxTurnsSlider(
    table: Table,
    settings: GameSettings,
    selectBoxMinWidth: Float
) {
    table.add("Max turns to AutoPlay".toLabel()).left().fillX()

    val minimapSlider = UncivSlider(
        1f, 1000f, 5f,
        initial = settings.autoPlayMaxTurns.toFloat()
    ) {
        val turns = it.toInt()
        settings.autoPlayMaxTurns = turns
    }
    table.add(minimapSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}


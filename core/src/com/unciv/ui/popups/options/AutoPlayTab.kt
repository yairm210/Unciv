package com.unciv.ui.popups.options

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.components.widgets.UncivSlider
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.screens.basescreen.BaseScreen

fun autoPlayTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings
//    fun addAutoPlaySections() {
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Military",
//            settings.autoPlay.autoPlayMilitary, false
//        ) { settings.autoPlay.autoPlayMilitary = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Civilian",
//            settings.autoPlay.autoPlayCivilian, false
//        ) { settings.autoPlay.autoPlayCivilian = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Economy",
//            settings.autoPlay.autoPlayEconomy, false
//        ) { settings.autoPlay.autoPlayEconomy = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Diplomacy",
//            settings.autoPlay.autoPlayDiplomacy, false
//        ) { settings.autoPlay.autoPlayDiplomacy = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Technology",
//            settings.autoPlay.autoPlayTechnology, false
//        ) { settings.autoPlay.autoPlayTechnology = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Policies",
//            settings.autoPlay.autoPlayPolicies, false
//        ) { settings.autoPlay.autoPlayPolicies = it }
//        optionsPopup.addCheckbox(
//            this,
//            "AutoPlay Religion",
//            settings.autoPlay.autoPlayReligion, false
//        ) { settings.autoPlay.autoPlayReligion = it }
//    }

    optionsPopup.addCheckbox(
        this,
        "Show AutoPlay button",
        settings.autoPlay.showAutoPlayButton, true
    ) { settings.autoPlay.showAutoPlayButton = it
        settings.autoPlay.stopAutoPlay() }

    
    optionsPopup.addCheckbox(
        this,
        "AutoPlay until victory",
        settings.autoPlay.autoPlayUntilEnd, false
    ) { settings.autoPlay.autoPlayUntilEnd = it
        if (!it) addAutoPlayMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth) 
        else optionsPopup.tabs.replacePage(optionsPopup.tabs.activePage, autoPlayTab(optionsPopup))}


    if (!settings.autoPlay.autoPlayUntilEnd)
        addAutoPlayMaxTurnsSlider(this, settings, optionsPopup.selectBoxMinWidth)
    
//    optionsPopup.addCheckbox(
//        this,
//        "Full AutoPlay AI",
//        settings.autoPlay.fullAutoPlayAI, false
//    ) { settings.autoPlay.fullAutoPlayAI = it
//        if (!it) addAutoPlaySections() 
//        else optionsPopup.tabs.replacePage(optionsPopup.tabs.activePage, autoPlayTab(optionsPopup))
//    }
//    if (!settings.autoPlay.fullAutoPlayAI)
//        addAutoPlaySections()
}

private fun addAutoPlayMaxTurnsSlider(
    table: Table,
    settings: GameSettings,
    selectBoxMinWidth: Float
) {
    table.add("Multi-turn AutoPlay amount".toLabel()).left().fillX()

    val minimapSlider = UncivSlider(
        1f, 200f, 1f,
        initial = settings.autoPlay.autoPlayMaxTurns.toFloat()
    ) {
        val turns = it.toInt()
        settings.autoPlay.autoPlayMaxTurns = turns
    }
    table.add(minimapSlider).minWidth(selectBoxMinWidth).pad(10f).row()
}


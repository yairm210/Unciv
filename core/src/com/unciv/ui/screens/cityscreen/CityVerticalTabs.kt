package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick

class CityVerticalTabs(cityScreen: CityScreen) : HorizontalGroup() {
    private val constructionButton = "Constructions".toTextButton()
    private val viewCityButton = "View City".toTextButton()
    private val viewBuildingsButton = "Buildings".toTextButton()
    init {
        align(Align.bottom)
        space(10f)

        constructionButton.onClick { cityScreen.selectConstructionPanel() }
        viewCityButton.onClick { cityScreen.selectCityPanel() }
        viewBuildingsButton.onClick { cityScreen.selectCityBuildingsPanel() }

        addActor(constructionButton)
        addActor(viewCityButton)
        addActor(viewBuildingsButton)
    }
}
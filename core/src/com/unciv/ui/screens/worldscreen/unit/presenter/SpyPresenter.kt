package com.unciv.ui.screens.worldscreen.unit.presenter

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.map.HexCoord
import com.unciv.models.Spy
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.unit.UnitTable

class SpyPresenter(private val unitTable: UnitTable) : UnitTable.Presenter {

    var selectedSpy: Spy? = null

    override val position: HexCoord?
        get() = selectedSpy?.getCityOrNull()?.location
    
    fun selectSpy(spy: Spy?) {
        selectedSpy = spy
    }

    override fun shouldBeShown(): Boolean = selectedSpy != null

    override fun updateWhenNeeded() = with(unitTable) {
        val spy = selectedSpy!!
        unitNameLabel.clearListeners()
        unitNameLabel.setText(spy.name.tr(hideIcons = true))
        descriptionTable.clear()

        unitIconHolder.clear()
        unitIconHolder.add(ImageGetter.getImage("OtherIcons/Spy_White").apply {
            color = Color.WHITE
        }).size(30f)

        separator.isVisible = true
        val displayRank = spy.getEffectiveRank()
        val color = when (displayRank) {
            1 -> Color.BROWN
            2 -> Color.LIGHT_GRAY
            3 -> Color.GOLD
            else -> ImageGetter.CHARCOAL
        }
        repeat(displayRank) {
            val star = ImageGetter.getImage("OtherIcons/Star")
            star.color = color
            descriptionTable.add(star).size(20f).pad(1f)
        }
    }
}

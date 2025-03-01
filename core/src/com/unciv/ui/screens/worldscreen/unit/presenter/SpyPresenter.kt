package com.unciv.ui.screens.worldscreen.unit.presenter

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.unciv.models.Spy
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.unit.UnitTable

class SpyPresenter(private val unitTable: UnitTable) : UnitTable.Presenter {

    var selectedSpy: Spy? = null

    override val position: Vector2?
        get() = selectedSpy?.getCityOrNull()?.location
    
    fun selectSpy(spy: Spy?) {
        selectedSpy = spy
    }

    override fun shouldBeShown(): Boolean = selectedSpy != null

    override fun updateWhenNeeded() = with(unitTable) {
        val spy = selectedSpy!!
        unitNameLabel.clearListeners()
        unitNameLabel.setText(spy.name)
        descriptionTable.clear()

        unitIconHolder.clear()
        unitIconHolder.add(ImageGetter.getImage("OtherIcons/Spy_White").apply {
            color = Color.WHITE
        }).size(30f)

        separator.isVisible = true
        val color = when (spy.rank) {
            1 -> Color.BROWN
            2 -> Color.LIGHT_GRAY
            3 -> Color.GOLD
            else -> ImageGetter.CHARCOAL
        }
        repeat(spy.rank) {
            val star = ImageGetter.getImage("OtherIcons/Star")
            star.color = color
            descriptionTable.add(star).size(20f).pad(1f)
        }
    }
}

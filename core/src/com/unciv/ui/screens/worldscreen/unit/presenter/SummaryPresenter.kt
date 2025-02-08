package com.unciv.ui.screens.worldscreen.unit.presenter

import com.badlogic.gdx.math.Vector2
import com.unciv.models.translations.tr
import com.unciv.ui.screens.worldscreen.unit.UnitTable

class SummaryPresenter(private val unitTable: UnitTable) : UnitTable.Presenter {
    
    override val position: Vector2? = null

    override fun update() {
        unitTable.closeButton.isVisible = false
    }
    
    override fun updateWhenNeeded() {
        unitTable.apply {
            descriptionTable.clear()
         
            unitNameLabel.setText("Units".tr())

            val idleCount = worldScreen.viewingCiv.units.getIdleUnits().count { it.due }
            val waitingCount = worldScreen.viewingCiv.units.getIdleUnits().count { !it.due }
            
            val subText = mutableListOf<String>().apply {
                if (idleCount > 0) add("[$idleCount] idle".tr())
                if (waitingCount > 0) add("[$waitingCount] waiting".tr())
            }.joinToString(", ")
            
            if(subText!="") {
                separator.isVisible = true
                descriptionTable.add(subText)
            } else {
                separator.isVisible = false
            }
        }
    }
}

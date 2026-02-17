package com.unciv.ui.components.tilegroups.citybutton

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.TutorialTrigger
import com.unciv.ui.images.ImageGetter

/**
 *  Bottom-most decoration showing zero or more icons
 *  (blockaded, connected to capital, resistance, puppet, razing, WLTK),
 *  goes below main button and city-state influence bar.
 */
internal class StatusTable(
    city: City,
    selectedCiv: Civilization,
    iconSize: Float = 18f
) : Table() {
    init {
        defaults().space(2f)

        if (city.civ == selectedCiv) {
            if (city.isBlockaded()) {
                val connectionImage = ImageGetter.getImage("OtherIcons/Blockade")
                add(connectionImage).size(iconSize)
                GUI.getWorldScreen().displayTutorial(TutorialTrigger.CityBlockade)
            } else if (!city.isCapital() && city.isConnectedToCapital()) {
                val connectionImage = ImageGetter.getStatIcon("CityConnection")
                add(connectionImage).size(iconSize)
            }
        }

        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            add(resistanceImage).size(iconSize)
        }

        if (city.isPuppet) {
            val puppetImage = ImageGetter.getImage("OtherIcons/Puppet")
            add(puppetImage).size(iconSize)
        }

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            add(fireImage).size(iconSize)
        }

        if (city.civ == selectedCiv && city.isWeLoveTheKingDayActive()) {
            val wltkdImage = ImageGetter.getImage("OtherIcons/WLTKD")
            add(wltkdImage).size(iconSize)
        }
    }
}

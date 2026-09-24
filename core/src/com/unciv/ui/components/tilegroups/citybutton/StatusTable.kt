package com.unciv.ui.components.tilegroups.citybutton

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.models.TutorialTrigger
import com.unciv.ui.images.ImageGetter
import com.unciv.view.ForeignCityView

/**
 *  Bottom-most decoration showing zero or more icons
 *  (blockaded, connected to capital, resistance, puppet, razing, WLTK),
 *  goes below main button and city-state influence bar.
 */
internal class StatusTable(
    cityView: ForeignCityView,
    iconSize: Float = 18f
) : Table() {
    init {
        defaults().space(2f)

        if (cityView.isOwnedByViewer()) {
            if (cityView.isBlockaded()) {
                val connectionImage = ImageGetter.getImage("OtherIcons/Blockade")
                add(connectionImage).size(iconSize)
                GUI.getWorldScreen().displayTutorial(TutorialTrigger.CityBlockade)
            } else if (!cityView.isCapital() && cityView.isConnectedToCapital()) {
                val connectionImage = ImageGetter.getStatIcon("CityConnection")
                add(connectionImage).size(iconSize)
            }
        }

        if (cityView.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            add(resistanceImage).size(iconSize)
        }

        if (cityView.isPuppet()) {
            val puppetImage = ImageGetter.getImage("OtherIcons/Puppet")
            add(puppetImage).size(iconSize)
        }

        if (cityView.isBeingRazed()) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            add(fireImage).size(iconSize)
        }

        if (cityView.isOwnedByViewer() && cityView.isWeLoveTheKingDayActive()) {
            val wltkdImage = ImageGetter.getImage("OtherIcons/WLTKD")
            add(wltkdImage).size(iconSize)
        }
    }
}

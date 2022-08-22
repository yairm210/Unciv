package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.AskTextPopup
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel

/** Widget for the City Screen -
 *  the panel at bottom center showing the city name and offering arrows to cycle through the cities. */
class CityScreenCityPickerTable(private val cityScreen: CityScreen) : Table() {

    fun update() {
        val city = cityScreen.city
        val civInfo = city.civInfo
        background = ImageGetter.getRoundedEdgeRectangle(civInfo.nation.getOuterColor())
        clear()

        if (civInfo.cities.size > 1) {
            val prevCityButton = Table() // so we get a wider clickable area than just the image itself
            val image = ImageGetter.getImage("OtherIcons/BackArrow")
            image.color = civInfo.nation.getInnerColor()
            prevCityButton.add(image).size(25f).pad(10f)
            prevCityButton.touchable = Touchable.enabled
            prevCityButton.onClick { cityScreen.page(-1) }
            add(prevCityButton).pad(10f)
        } else add()

        val cityNameTable = Table()

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            cityNameTable.add(fireImage).size(20f).padRight(5f)
        }

        if (city.isCapital()) {
            val starImage = ImageGetter.getImage("OtherIcons/Star").apply { color = Color.LIGHT_GRAY }
            cityNameTable.add(starImage).size(20f).padRight(5f)
        }

        if (city.isPuppet) {
            val starImage = ImageGetter.getImage("OtherIcons/Puppet").apply { color = Color.LIGHT_GRAY }
            cityNameTable.add(starImage).size(20f).padRight(5f)
        }

        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            cityNameTable.add(resistanceImage).size(20f).padRight(5f)
        }

        val currentCityLabel = city.name.toLabel(fontSize = 30, fontColor = civInfo.nation.getInnerColor())
        if (cityScreen.canChangeState) currentCityLabel.onClick {
            AskTextPopup(
                cityScreen,
                label = "Please enter a new name for your city",
                defaultText = city.name,
                validate = { it != "" },
                actionOnOk = { text ->
                    city.name = text
                    cityScreen.game.replaceCurrentScreen(CityScreen(city))
                }
            ).open()
        }

        cityNameTable.add(currentCityLabel)

        add(cityNameTable).width(stage.width / 4)

        if (civInfo.cities.size > 1) {
            val nextCityButton = Table() // so we gt a wider clickable area than just the image itself
            val image = ImageGetter.getImage("OtherIcons/BackArrow")
            image.setSize(25f, 25f)
            image.setOrigin(Align.center)
            image.rotation = 180f
            image.color = civInfo.nation.getInnerColor()
            nextCityButton.add(image).size(25f).pad(10f)
            nextCityButton.touchable = Touchable.enabled
            nextCityButton.onClick { cityScreen.page(1) }
            add(nextCityButton).pad(10f)
        } else add()

        pack()
    }

}

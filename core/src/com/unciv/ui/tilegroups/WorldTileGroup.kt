package com.unciv.ui.tilegroups

import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {

    fun setIsViewable(isViewable: Boolean) {
        if (isViewable) {
            setColor(0f, 0f, 0f, 1f) // Only alpha really changes anything
            tileInfo.explored = true
            update()
        } else
            setColor(0f, 0f, 0f, 0.5f)
    }


    fun update(worldScreen: WorldScreen) {
        super.update()

        if (tileInfo.workingCity != null && populationImage == null) addPopulationIcon()
        if (tileInfo.workingCity == null && populationImage != null) removePopulationIcon()


        if (tileInfo.owner != null && hexagon == null) {
            hexagon = ImageGetter.getImage("TerrainIcons/Hexagon.png")
            val imageScale = terrainImage.width * 1.3f / hexagon!!.width
            hexagon!!.setScale(imageScale)
            hexagon!!.setPosition((width - hexagon!!.width * imageScale) / 2,
                    (height - hexagon!!.height * imageScale) / 2)
            addActor(hexagon!!)
            hexagon!!.zIndex = 0
        }


        val city = tileInfo.city
        if (tileInfo.isCityCenter) {
            val buttonScale = 0.7f
            if (cityButton == null) {
                cityButton = Container()
                cityButton!!.actor = TextButton("", CameraStageBaseScreen.skin)

                cityButton!!.actor.label.setFontScale(buttonScale)

                val game = worldScreen.game
                cityButton!!.actor.addClickListener { game.screen = CityScreen(city!!)
                    }

                addActor(cityButton!!)
                zIndex = parent.children.size // so this tile is rendered over neighboring tiles
            }

            val cityButtonText = city!!.name + " (" + city.population.population + ")"
            val button = cityButton!!.actor
            button.setText(cityButtonText)
            button.setSize(button.prefWidth, button.prefHeight)

            cityButton!!.setPosition((width - cityButton!!.width) / 2,
                    height * 0.9f)
            cityButton!!.zIndex = cityButton!!.parent.children.size // so city button is rendered over everything else in this tile
        }

    }
}

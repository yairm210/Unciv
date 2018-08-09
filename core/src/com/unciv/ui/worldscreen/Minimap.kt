package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.colorFromRGB

class Minimap(val tileMapHolder: TileMapHolder) : ScrollPane(null){
    val allTiles = Group()
    val tileImages = HashMap<TileInfo, Image>()


    fun setScrollToTileMapHolder(){
        scrollPercentX = tileMapHolder.scrollPercentX
        scrollPercentY = tileMapHolder.scrollPercentY
    }

    init{
        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        for (tileInfo in tileMapHolder.tileMap.values) {
            val hex = ImageGetter.getImage("TerrainIcons/Hexagon.png")

            val positionalVector = HexMath().Hex2WorldCoords(tileInfo.position)
            val groupSize = 10f
            hex.setSize(groupSize,groupSize)
            hex.setPosition(positionalVector.x * 0.5f * groupSize,
                    positionalVector.y * 0.5f * groupSize)
            hex.addClickListener {
                tileMapHolder.setCenterPosition(tileInfo.position)
                setScrollToTileMapHolder()
            }
            allTiles.addActor(hex)
            tileImages.put(tileInfo,hex)

            topX = Math.max(topX, hex.x + groupSize)
            topY = Math.max(topY, hex.y + groupSize)
            bottomX = Math.min(bottomX, hex.x)
            bottomY = Math.min(bottomY, hex.y)
        }

        for (group in allTiles.children) {
            group.moveBy(-bottomX, -bottomY)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(10 + topX - bottomX, 10 + topY - bottomY)

        widget = allTiles
        layout()
        updateVisualScroll()
        tileMapHolder.addListener(object : InputListener(){
            override fun handle(e: Event?): Boolean {
                setScrollToTileMapHolder()
                return true
            }
        })
        update()
    }

    fun update(){
        val exploredTiles = tileMapHolder.worldScreen.civInfo.exploredTiles
        for(tileInfo in tileMapHolder.tileMap.values) {
            val RGB = tileInfo.getBaseTerrain().RGB!!
            val hex = tileImages[tileInfo]!!
            if (!(exploredTiles.contains(tileInfo.position) || UnCivGame.Current.viewEntireMapForDebug)) hex.color = Color.BLACK
            else if (tileInfo.isCityCenter()) hex.color = Color.WHITE
            else if (tileInfo.getCity() != null) hex.color = tileInfo.getOwner()!!.getNation().getColor()
            else hex.color = colorFromRGB(RGB[0], RGB[1], RGB[2]).lerp(Color.GRAY, 0.5f) // Todo add to baseterrain as function
        }
    }
}
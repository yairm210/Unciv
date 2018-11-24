package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.surroundWithCircle

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

            val positionalVector = HexMath().hex2WorldCoords(tileInfo.position)
            val groupSize = 10f
            hex.setSize(groupSize,groupSize)
            hex.setPosition(positionalVector.x * 0.5f * groupSize,
                    positionalVector.y * 0.5f * groupSize)
            hex.onClick {
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
    }

    fun update(cloneCivilization: CivilizationInfo) {
        val exploredTiles = cloneCivilization.exploredTiles
        for(tileInfo in tileMapHolder.tileMap.values) {
            val hex = tileImages[tileInfo]!!
            if (!(exploredTiles.contains(tileInfo.position) || UnCivGame.Current.viewEntireMapForDebug)) hex.color = Color.BLACK
            else if (tileInfo.isCityCenter() && !tileInfo.isWater()) hex.color = tileInfo.getOwner()!!.getNation().getSecondaryColor()
            else if (tileInfo.getCity() != null && !tileInfo.isWater()) hex.color = tileInfo.getOwner()!!.getNation().getColor()
            else hex.color = tileInfo.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f) // Todo add to baseterrain as function
        }
    }
}

class MinimapHolder(val tileMapHolder: TileMapHolder): Table(){
    val minimap = Minimap(tileMapHolder)
    val worldScreen = tileMapHolder.worldScreen

    init{
        add(getToggleIcons()).align(Align.bottom)
        add(getWrappedMinimap())
        pack()
    }

    fun getWrappedMinimap(): Table {
        val internalMinimapWrapper = Table()
        internalMinimapWrapper.add(minimap).size(worldScreen.stage.width/5,worldScreen.stage.height/5)
        internalMinimapWrapper.background=ImageGetter.getBackground(Color.GRAY)
        internalMinimapWrapper.pack()

        val externalMinimapWrapper = Table()
        externalMinimapWrapper.add(internalMinimapWrapper).pad(5f)
        externalMinimapWrapper.background=ImageGetter.getBackground(Color.WHITE)
        externalMinimapWrapper.pack()

        return externalMinimapWrapper
    }

    fun getToggleIcons():Table{
        val toggleIconTable=Table()
        val settings = UnCivGame.Current.settings

        val populationImage = ImageGetter.getStatIcon("Population").surroundWithCircle(40f)
        populationImage.circle.color = Color.BLACK
        populationImage.image.color.a = if(settings.showWorkedTiles) 1f else 0.5f
        populationImage.onClick {
            settings.showWorkedTiles = !settings.showWorkedTiles
            populationImage.image.color.a = if(settings.showWorkedTiles) 1f else 0.5f
            worldScreen.update()
        }
        toggleIconTable.add(populationImage).row()

        val resourceImage = ImageGetter.getResourceImage("Cattle",30f).surroundWithCircle(40f)
        resourceImage.circle.color = Color.BLACK
        resourceImage.image.color.a = if(settings.showResourcesAndImprovements) 1f else 0.5f
        resourceImage.onClick {
            settings.showResourcesAndImprovements = !settings.showResourcesAndImprovements
            resourceImage.image.color.a = if(settings.showResourcesAndImprovements) 1f else 0.5f
            worldScreen.update()
        }
        toggleIconTable.add(resourceImage)
        toggleIconTable.pack()
        return toggleIconTable
    }

    fun update(civInfo:CivilizationInfo){minimap.update(civInfo)}
}
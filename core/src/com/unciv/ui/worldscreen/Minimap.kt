package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.surroundWithCircle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class Minimap(val mapHolder: WorldMapHolder) : ScrollPane(null){
    val allTiles = Group()
    val tileImages = HashMap<TileInfo, Image>()


    fun setScrollTomapHolder(){
        scrollPercentX = mapHolder.scrollPercentX
        scrollPercentY = mapHolder.scrollPercentY
    }

    init{
        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        for (tileInfo in mapHolder.tileMap.values) {
            val hex = ImageGetter.getImage("OtherIcons/Hexagon")

            val positionalVector = HexMath.hex2WorldCoords(tileInfo.position)
            val groupSize = 10f
            hex.setSize(groupSize,groupSize)
            hex.setPosition(positionalVector.x * 0.5f * groupSize,
                    positionalVector.y * 0.5f * groupSize)
            hex.onClick {
                mapHolder.setCenterPosition(tileInfo.position)
                setScrollTomapHolder()
            }
            allTiles.addActor(hex)
            tileImages[tileInfo] = hex

            topX = max(topX, hex.x + groupSize)
            topY = max(topY, hex.y + groupSize)
            bottomX = min(bottomX, hex.x)
            bottomY = min(bottomY, hex.y)
        }

        for (group in allTiles.children) {
            group.moveBy(-bottomX, -bottomY)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(topX - bottomX, topY - bottomY)

        actor = allTiles
        layout()
        updateVisualScroll()
        mapHolder.addListener(object : InputListener(){
            override fun handle(e: Event?): Boolean {
                setScrollTomapHolder()
                return true
            }
        })
    }

    fun update(cloneCivilization: CivilizationInfo) {
        for(tileInfo in mapHolder.tileMap.values) {
            val hex = tileImages[tileInfo]!!
            if (!(UncivGame.Current.viewEntireMapForDebug || cloneCivilization.exploredTiles.contains(tileInfo.position)))
                hex.color = Color.DARK_GRAY
            else if (tileInfo.isCityCenter() && !tileInfo.isWater)
                hex.color = tileInfo.getOwner()!!.nation.getInnerColor()
            else if (tileInfo.getCity() != null && !tileInfo.isWater)
                hex.color = tileInfo.getOwner()!!.nation.getOuterColor()
            else hex.color = tileInfo.getBaseTerrain().getColor().lerp(Color.GRAY, 0.5f)
            if (tileInfo.isCityCenter() && tileInfo.owningCity!!.getTiles().any { cloneCivilization.exploredTiles.contains(it.position) }) {
                val nationIcon= ImageGetter.getNationIndicator(tileInfo.owningCity!!.civInfo.nation,hex.width * 3)
                nationIcon.setPosition(hex.x - nationIcon.width/3,hex.y - nationIcon.height/3)
                nationIcon.onClick {
                    mapHolder.setCenterPosition(tileInfo.position)
                    setScrollTomapHolder()
                }
                allTiles.addActor(nationIcon)
            }
        }
    }
}

class MinimapHolder(mapHolder: WorldMapHolder): Table(){
    val minimap = Minimap(mapHolder)
    val worldScreen = mapHolder.worldScreen

    init{
        add(getToggleIcons()).align(Align.bottom)
        add(getWrappedMinimap())
        pack()
    }

    fun getWrappedMinimap(): Table {
        val internalMinimapWrapper = Table()

        val sizePercent = worldScreen.game.settings.minimapSize
        val sizeWinX = worldScreen.stage.width * sizePercent / 100
        val sizeWinY = worldScreen.stage.height * sizePercent / 100
        val isSquare = worldScreen.game.settings.minimapSquare
        val sizeX = if (isSquare) sqrt(sizeWinX * sizeWinY) else sizeWinX
        val sizeY = if (isSquare) sizeX else sizeWinY
        internalMinimapWrapper.add(minimap).size(sizeX,sizeY)

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
        val settings = UncivGame.Current.settings

        val populationImage = ImageGetter.getStatIcon("Population").surroundWithCircle(40f)
        populationImage.circle.color = Color.BLACK
        populationImage.actor.color.a = if(settings.showWorkedTiles) 1f else 0.5f
        populationImage.onClick {
            settings.showWorkedTiles = !settings.showWorkedTiles
            populationImage.actor.color.a = if(settings.showWorkedTiles) 1f else 0.5f
            worldScreen.shouldUpdate=true
        }
        toggleIconTable.add(populationImage).row()

        val resourceImage = ImageGetter.getImage("ResourceIcons/Cattle").surroundWithCircle(40f)
        resourceImage.actor.color.a = if(settings.showResourcesAndImprovements) 1f else 0.5f
        resourceImage.onClick {
            settings.showResourcesAndImprovements = !settings.showResourcesAndImprovements
            resourceImage.actor.color.a = if(settings.showResourcesAndImprovements) 1f else 0.5f
            worldScreen.shouldUpdate=true
        }
        toggleIconTable.add(resourceImage)
        toggleIconTable.pack()
        return toggleIconTable
    }

    fun update(civInfo:CivilizationInfo){
        isVisible = UncivGame.Current.settings.showMinimap
        minimap.update(civInfo)
    }
}

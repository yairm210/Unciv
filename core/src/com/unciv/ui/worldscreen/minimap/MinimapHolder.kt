package com.unciv.ui.worldscreen.minimap

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.worldscreen.WorldMapHolder

class MinimapHolder(val mapHolder: WorldMapHolder) : Table() {
    private val worldScreen = mapHolder.worldScreen
    private var minimapSize = Int.MIN_VALUE
    lateinit var minimap: Minimap

    /** Button, next to the minimap, to toggle the unit movement map overlay. */
    val movementsImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("StatIcons/Movement").apply { setColor(0f, 0f, 0f, 1f) },
        getter = { UncivGame.Current.settings.showUnitMovements },
        setter = { UncivGame.Current.settings.showUnitMovements = it },
        backgroundColor = Color.GREEN
    )
    /** Button, next to the minimap, to toggle the tile yield map overlay. */
    private val yieldImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("StatIcons/Food"),
        // This is a use in the UI that has little to do with the stat… These buttons have more in common with each other than they do with other uses of getStatIcon().
        getter = { UncivGame.Current.settings.showTileYields },
        setter = { UncivGame.Current.settings.showTileYields = it }
    )
    /** Button, next to the minimap, to toggle the worked tiles map overlay. */
    val populationImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("StatIcons/Population"),
        getter = { UncivGame.Current.settings.showWorkedTiles },
        setter = { UncivGame.Current.settings.showWorkedTiles = it }
    )
    /** Button, next to the minimap, to toggle the resource icons map overlay. */
    val resourceImageButton = MapOverlayToggleButton(
        ImageGetter.getImage("ResourceIcons/Cattle"),
        getter = { UncivGame.Current.settings.showResourcesAndImprovements },
        setter = { UncivGame.Current.settings.showResourcesAndImprovements = it },
        backgroundColor = Color.GREEN
    )

    init {
        rebuildIfSizeChanged()
    }

    private fun rebuildIfSizeChanged() {
        val newMinimapSize = worldScreen.game.settings.minimapSize
        if (newMinimapSize == minimapSize) return
        minimapSize = newMinimapSize
        this.clear()
        minimap = Minimap(mapHolder, minimapSize)
        add(getToggleIcons()).align(Align.bottom)
        add(getWrappedMinimap())
        pack()
        if (stage != null) x = stage.width - width
    }

    private fun getWrappedMinimap(): Table {
        val internalMinimapWrapper = Table()
        internalMinimapWrapper.add(minimap)

        internalMinimapWrapper.background = ImageGetter.getBackground(Color.GRAY)
        internalMinimapWrapper.pack()

        val externalMinimapWrapper = Table()
        externalMinimapWrapper.add(internalMinimapWrapper).pad(5f)
        externalMinimapWrapper.background = ImageGetter.getBackground(Color.WHITE)
        externalMinimapWrapper.pack()

        return externalMinimapWrapper
    }

    /** @return Layout table for the little green map overlay toggle buttons, show to the left of the minimap. */
    private fun getToggleIcons(): Table {
        val toggleIconTable = Table()

        toggleIconTable.add(movementsImageButton.actor).row()
        toggleIconTable.add(yieldImageButton.actor).row()
        toggleIconTable.add(populationImageButton.actor).row()
        toggleIconTable.add(resourceImageButton.actor).row()

        return toggleIconTable
    }

    fun update(civInfo: CivilizationInfo) {
        rebuildIfSizeChanged()
        isVisible = UncivGame.Current.settings.showMinimap
        if (isVisible) {
            minimap.update(civInfo)
            movementsImageButton.update()
            yieldImageButton.update()
            populationImageButton.update()
            resourceImageButton.update()
        }
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}

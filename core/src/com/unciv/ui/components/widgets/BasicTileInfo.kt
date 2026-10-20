package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.objectdescriptions.TileDescription
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.civilopediascreen.FormattedLine.IconDisplay
import com.unciv.ui.screens.civilopediascreen.MarkupRenderer
import com.unciv.view.CityView
import com.unciv.view.CivView
import com.unciv.view.TileView
import kotlin.math.roundToInt

/**
 *  A rectangular pane showing Tile information:
 *  - Has a border and is skinnable ([borderWidth], [backgroundTexture], [borderTexture], [borderTint])
 *  - Shows tile yields (clickable for a [breakdown][com.unciv.logic.map.tile.TileStatFunctions.getTerrainStatsBreakdown])
 *  - Shows basic info such as Terrain, Resource, Improvements (Civilopedia linked, see [TileDescription])
 *  - Subclasses can add further content ([addAdditionalContent])
 *  - Needs an external call to [update] to show content
 *  - Hides if a `null` is passed to [update] or when [shouldBeVisible] is overridden and returns `false`
 *  - [isSpying] is passed to [TileDescription.toMarkup] and controls display of units or city production
 *
 *  For use in [WorldScreen][com.unciv.ui.screens.worldscreen.bottombar.TileInfoTable] and [com.unciv.ui.screens.cityscreen.CityScreenTileTable]
 */
open class BasicTileInfo(
    private val parentScreen: BaseScreen,
    protected open var civView: CivView,
    protected open val cityView: CityView? = null,
    protected val isSpying: Boolean = false,
    backgroundTexture: String,
    borderWidth: Float = 2f,
    borderTexture: String,
    borderTint: Color = Color.WHITE
) : Table(BaseScreen.skin) {
    private val innerTable = Table()

    init {
        name = this::class.simpleName
        innerTable.background = BaseScreen.skinStrings.getUiBackground(
            backgroundTexture,
            tintColor = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.5f)
        )
        add(innerTable).pad(borderWidth).fill()
        background = BaseScreen.skinStrings.getUiBackground(borderTexture, tintColor = borderTint)
        innerTable.pad(5f)
        innerTable.defaults().space(5f)
    }

    fun update(civView: CivView, tileView: TileView?) {
        this.civView = civView
        update(tileView)
    }

    fun update(tileView: TileView?) {
        innerTable.clear()
        if (tileView == null || !shouldBeVisible(tileView)) {
            isVisible = false
            return
        }
        isVisible = true
        innerTable.add(getStatsTable(tileView)).fillX().row()
        innerTable.add(getDescription(tileView)).row()
        innerTable.addAdditionalContent(tileView)
        pack()
    }

    open fun shouldBeVisible(tileView: TileView) = true

    open fun Table.addAdditionalContent(tileView: TileView) {}

    private fun getDescription(tileView: TileView) =
        MarkupRenderer.render(
            TileDescription.toMarkup(tileView, isSpying, cityView.takeIf { isSpying }),
            padding = 0f, iconDisplay = IconDisplay.None
        ) {
            parentScreen.openCivilopedia(it)
        }

    private fun getStatsTable(tileView: TileView) = Table().apply {
        defaults().pad(2f)
        for ((key, value) in tileView.getTileStats(civView, cityView)) {
            add((key.character + value.roundToInt().toString()).toLabel())
                .align(Align.left).padRight(5f)
        }
        touchable = Touchable.enabled
        onClick { StatBreakdownPopup(tileView) }
    }

    private inner class StatBreakdownPopup(tileView: TileView) : Popup(parentScreen) {
        init {
            for ((name, stats) in tileView.getTileStatsBreakdown(civView, cityView))
                add("{$name}: {${stats.clone()}}".toLabel()).row()
            addCloseButton()
            open()
        }
    }
}

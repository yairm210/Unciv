package com.unciv.ui.worldscreen.bottombar

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen

class TileInfoTable(private val worldScreen: WorldScreen) : Table() {
    init{
        skin = CameraStageBaseScreen.skin
    }

    internal fun updateTileTable(tile: TileInfo) {
        clearChildren()
        val civInfo = worldScreen.civInfo
        columnDefaults(0).padRight(10f)

        add(getCheckboxTable())
        if (civInfo.exploredTiles.contains(tile.position) || UnCivGame.Current.viewEntireMapForDebug) {
            add(getStatsTable(tile)).pad(10f)
            add(Label(tile.toString(), skin)).colspan(2)
        }

        pack()

        setPosition(worldScreen.stage.width - 10f - width, 10f)
    }

    fun getCheckboxTable(): Table {
        val settings = UnCivGame.Current.settings
        val table=Table()

        val populationCheckbox = CheckBox("",CameraStageBaseScreen.skin)
        populationCheckbox.add(ImageGetter.getStatIcon("Population")).size(20f)
        populationCheckbox.isChecked = settings.showWorkedTiles
        populationCheckbox.addListener(object : ChangeListener(){
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                settings.showWorkedTiles = populationCheckbox.isChecked
                worldScreen.update()
            }
        })
        table.add(populationCheckbox).row()

        val resourceCheckbox = CheckBox("",CameraStageBaseScreen.skin)
        resourceCheckbox.add(ImageGetter.getResourceImage("Cattle",20f))
        resourceCheckbox.isChecked = settings.showResourcesAndImprovements
        resourceCheckbox.addListener(object : ChangeListener(){
            override fun changed(event: ChangeEvent?, actor: Actor?) {
                settings.showResourcesAndImprovements = resourceCheckbox.isChecked
                worldScreen.update()
            }
        })
        table.add(resourceCheckbox).row()

        return table
    }

    fun getStatsTable(tile: TileInfo):Table{
        val table=Table()
        table.pad(10f)

        for (entry in tile.getTileStats(worldScreen.civInfo).toHashMap().filterNot { it.value == 0f }) {
            table.add(ImageGetter.getStatIcon(entry.key.toString())).size(20f).align(Align.right)
            table.add(Label(entry.value.toInt().toString(), skin)).align(Align.left)
            table.row()
        }
        return table
    }
}


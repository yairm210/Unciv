package com.unciv.ui.mapeditor

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.MarkupRenderer
import com.unciv.ui.utils.CameraStageBaseScreen

class MapEditorEditTerrainTab(
    private val parent: MapEditorEditTab,
    ruleset: Ruleset
): Table(CameraStageBaseScreen.skin) {
    init {
        add(MarkupRenderer.render(ruleset.terrains.values
            .filter { it.type.isBaseTerrain }
            .map { FormattedLine(it.name, it.name, "Terrain/${it.name}", imageSize = 40f) },
            iconDisplay = FormattedLine.IconDisplay.NoLink
        ) {
            println("Terrain selected: $it")
        }).row()
    }
}
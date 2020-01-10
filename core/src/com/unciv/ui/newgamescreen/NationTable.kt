package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.models.ruleset.Nation
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.getUniqueString
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.toLabel

class NationTable(val nation: Nation, width:Float, ruleset: Ruleset)
    : Table(CameraStageBaseScreen.skin) {
    private val innerTable = Table()

    init {
        background = ImageGetter.getBackground(nation.innerColor)
        innerTable.pad(10f)
        innerTable.background = ImageGetter.getBackground(nation.outerColor)

        val titleTable = Table()
        titleTable.add(ImageGetter.getNationIndicator(nation, 50f)).pad(10f)
        val leaderDisplayLabel = nation.getLeaderDisplayName().toLabel(nation.innerColor,24)
        val leaderDisplayNameMaxWidth = width - 70 // for the nation indicator
        if(leaderDisplayLabel.width > leaderDisplayNameMaxWidth){ // for instance Polish has really long [x] of [y] translations
            leaderDisplayLabel.setWrap(true)
            titleTable.add(leaderDisplayLabel).width(leaderDisplayNameMaxWidth)
        }
        else titleTable.add(leaderDisplayLabel)
        innerTable.add(titleTable).row()
        val nationUniqueLabel = nation.getUniqueString(ruleset).toLabel(nation.innerColor)
        nationUniqueLabel.setWrap(true)
        innerTable.add(nationUniqueLabel).width(width)
        touchable = Touchable.enabled
        add(innerTable)
    }


}
package com.unciv.ui.screens.diplomacyscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * This is meant to be used for any kind of civ introduction - [DiplomacyScreen],
 * [AlertPopup][com.unciv.ui.worldscreen.AlertPopup] [types][com.unciv.logic.civilization.AlertType] WarDeclaration, FirstContact etc.
 *
 * @param civInfo The civilization to display
 * @param hello Optional additional message
 */
class LeaderIntroTable (
    civInfo: Civilization,
    hello: String = ""
): Table(BaseScreen.skin) {
    /**
     * Build either a Table(icon, leaderName <br> hello) or
     * a Table(Portrait, Table(leaderName, icon <br> hello))
     *
     * City states in vanilla have leaderName=="" - but don't test CS, test leaderName to allow modding CS to have portraits
     */
    init {
        defaults().align(Align.center)
        val nation = civInfo.nation
        val leaderPortraitFile = "LeaderIcons/" + nation.leaderName
        val formattedName = civInfo.getLeaderDisplayName()
        val leaderLabel = formattedName.toLabel(fontSize = Constants.headingFontSize, hideIcons = true)
        val nationIndicator = ImageGetter.getNationPortrait(nation, 24f)
        if (nation.leaderName.isNotEmpty() && ImageGetter.imageExists(leaderPortraitFile)) {
            val nameTable = Table()
            nameTable.add(leaderLabel)
            nameTable.add(nationIndicator).pad(0f, 10f, 5f, 0f).row()
            if (hello.isNotEmpty())
                nameTable.add(hello.toLabel()).colspan(2)
            add(ImageGetter.getImage(leaderPortraitFile)).size(100f)
                .padRight(10f)
            add(nameTable)
        } else {
            add(nationIndicator).pad(0f, 0f, 5f, 10f)
            add(leaderLabel).row()
            if (hello.isNotEmpty())
                add(hello.toLabel()).colspan(2)
        }
    }
}

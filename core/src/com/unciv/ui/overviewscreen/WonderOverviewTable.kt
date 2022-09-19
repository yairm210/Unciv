package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.WonderInfo
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel

class WonderOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    val ruleSet = gameInfo.ruleSet

    val wonderInfo = WonderInfo()
    private val wonders: Array<WonderInfo.WonderInfo> = wonderInfo.collectInfo()

    private val fixedContent = Table()
    override fun getFixedContent() = fixedContent

    init {
        fixedContent.apply {
            defaults().pad(10f).align(Align.center)
            add()
            add("Name".toLabel())
            add("Status".toLabel())
            add("Location".toLabel())
            add().minWidth(30f)
            row()
        }

        top()
        defaults().pad(10f).align(Align.center)
        (1..5).forEach { _ -> add() }  // dummies so equalizeColumns can work because the first grid cell is colspan(5)
        row()

        createGrid()

        equalizeColumns(fixedContent, this)
    }

    fun createGrid() {
        var lastGroup = ""

        for (wonder in wonders) {
            if (wonder.status == WonderInfo.WonderStatus.Hidden) continue
            if (wonder.groupName != lastGroup) {
                lastGroup = wonder.groupName
                val groupRow = Table().apply {
                    add(ImageGetter.getDot(wonder.groupColor)).minHeight(2f).growX()
                    add(lastGroup.toLabel(wonder.groupColor).apply { setAlignment(Align.right) }).padLeft(1f).right()
                }
                add(groupRow).fillX().colspan(5).padBottom(0f).row()
            }

            val image = wonder.getImage()
            image?.onClick {
                UncivGame.Current.pushScreen(CivilopediaScreen(ruleSet, wonder.category, wonder.name))
            }
            // Terrain image padding is a bit unpredictable, they need ~5f more. Ensure equal line spacing on name, not image:
            add(image).pad(0f, 10f, 0f, 10f)

            add(wonder.getNameColumn().toLabel()).pad(15f, 10f, 15f, 10f)
            add(wonder.getStatusColumn().toLabel())
            val locationText = wonder.getLocationColumn()
            if (locationText.isNotEmpty()) {
                val locationLabel = locationText.toLabel()
                if (wonder.location != null)
                    locationLabel.onClick{
                        val worldScreen = UncivGame.Current.resetToWorldScreen()
                        worldScreen.mapHolder.setCenterPosition(wonder.location.position)
                    }
                add(locationLabel).fillY()
            }
            row()
        }
    }
}

package com.unciv.ui.screens.worldscreen.topbar

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.unciv.logic.civilization.Civilization
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class WorldScreenTopBarStats(topbar: WorldScreenTopBar) : WidgetGroup() {
    private val innerTable = Table()
    private val goldLabel = "0".toLabel(colorFromRGB(225, 217, 71))
    private val scienceLabel = "0".toLabel(colorFromRGB(78, 140, 151))
    private val happinessLabel = "0".toLabel()
    private val cultureLabel = "0".toLabel(colorFromRGB(210, 94, 210))
    private val faithLabel = "0".toLabel(colorFromRGB(168, 196, 241))

    private val happinessContainer = Group()

    // These are all to improve performance IE reduce update time (was 150 ms on my phone, which is a lot!)
    private val malcontentColor = colorFromRGB(239,83,80) // Color.valueOf("ef5350")
    private val happinessColor = colorFromRGB(92, 194, 77) // Color.valueOf("8cc24d")
    private val malcontentImage = ImageGetter.getStatIcon("Malcontent")
    private val happinessImage = ImageGetter.getStatIcon("Happiness")

    private val worldScreen = topbar.worldScreen


    companion object {
        const val defaultImageSize = 20f
        const val defaultHorizontalPad = 3f
        const val defaultTopPad = 8f
        const val defaultBottomPad = 3f
        const val defaultImageBottomPad = 6f
        const val padRightBetweenStats = 20f
        const val minScale = 0.5f
    }

    init {
        isTransform = false

        fun addStat(label: Label, icon: String, isLast: Boolean = false, screenFactory: ()-> BaseScreen) {
            val image = ImageGetter.getStatIcon(icon)
            val action = {
                worldScreen.game.pushScreen(screenFactory())
            }
            label.onClick(action)
            image.onClick(action)
            innerTable.add(label)
            innerTable.add(image).padBottom(defaultImageBottomPad).size(defaultImageSize).apply {
                if (!isLast) padRight(padRightBetweenStats)
            }
        }

        fun addStat(label: Label, icon: String, overviewPage: EmpireOverviewCategories, isLast: Boolean = false) =
            addStat(label, icon, isLast) { EmpireOverviewScreen(worldScreen.selectedCiv, overviewPage) }

        innerTable.defaults().pad(defaultTopPad, defaultHorizontalPad, defaultBottomPad, defaultHorizontalPad)
        addStat(goldLabel, "Gold", EmpireOverviewCategories.Stats)
        addStat(scienceLabel, "Science") { TechPickerScreen(worldScreen.selectedCiv) }

        innerTable.add(happinessContainer).padBottom(defaultImageBottomPad).size(defaultImageSize)
        innerTable.add(happinessLabel).padRight(padRightBetweenStats)
        val invokeResourcesPage = {
            worldScreen.openEmpireOverview(EmpireOverviewCategories.Resources)
        }
        happinessContainer.onClick(invokeResourcesPage)
        happinessLabel.onClick(invokeResourcesPage)

        addStat(cultureLabel, "Culture") { PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState) }
        if (worldScreen.gameInfo.isReligionEnabled()) {
            addStat(faithLabel, "Faith", EmpireOverviewCategories.Religion, isLast = true)
        } else {
            innerTable.add("Religion: Off".toLabel())
        }

        addActor(innerTable)
    }

    // I'd like to report "we could, if needed, shrink to innerTable.minWidth * minScale"
    // - but then we get overall a glitch during resizes where the outer Table sets our height
    // to minHeight despite there being room (as far as WorldScreenTopBar is concened) and scale > minScale...
    override fun getMinWidth() = innerTable.minWidth * innerTable.scaleX

    override fun getPrefWidth() = innerTable.prefWidth * innerTable.scaleX
    override fun getMaxWidth() = innerTable.prefWidth

    override fun getMinHeight() = innerTable.minHeight * innerTable.scaleY
    override fun getPrefHeight() = innerTable.prefHeight * innerTable.scaleY
    override fun getMaxHeight() = innerTable.prefHeight

    override fun layout() {
        innerTable.setBounds(0f, 0f, width / innerTable.scaleX, height / innerTable.scaleY)
    }

    var background: Drawable?
        get() = innerTable.background
        set(value) { innerTable.background = value }

    private fun rateLabel(value: Float) = value.roundToInt().toStringSigned()

    fun update(civInfo: Civilization) {
        innerTable.isTransform = false
        innerTable.setScale(1f)

        val nextTurnStats = civInfo.stats.statsForNextTurn
        val goldPerTurn = " (" + rateLabel(nextTurnStats.gold) + ")"
        goldLabel.setText(civInfo.gold.toString() + goldPerTurn)

        scienceLabel.setText(rateLabel(nextTurnStats.science))

        happinessLabel.setText(getHappinessText(civInfo))

        if (civInfo.getHappiness() < 0) {
            happinessLabel.setFontColor(malcontentColor)
            happinessContainer.clearChildren()
            happinessContainer.addActor(malcontentImage)
        } else {
            happinessLabel.setFontColor(happinessColor)
            happinessContainer.clearChildren()
            happinessContainer.addActor(happinessImage)
        }

        cultureLabel.setText(getCultureText(civInfo, nextTurnStats))
        faithLabel.setText(civInfo.religionManager.storedFaith.toString() +
            " (" + rateLabel(nextTurnStats.faith) + ")")

        innerTable.pack()
        scaleToMaxWidth()
    }

    private fun scaleToMaxWidth() {
        val scale = worldScreen.stage.width / innerTable.prefWidth
        if (scale >= 1f) return
        innerTable.isTransform = true
        innerTable.setScale(scale)
        if (!innerTable.needsLayout()) {
            innerTable.invalidate()
            invalidate()
        }
    }

    private fun getCultureText(civInfo: Civilization, nextTurnStats: Stats): String {
        var cultureString = rateLabel(nextTurnStats.culture)
        //if (nextTurnStats.culture == 0f) return cultureString // when you start the game, you're not producing any culture

        val turnsToNextPolicy = (civInfo.policies.getCultureNeededForNextPolicy() - civInfo.policies.storedCulture) / nextTurnStats.culture
        cultureString +=  if  (turnsToNextPolicy <= 0f) " (!)"
        else if (nextTurnStats.culture <= 0) " (∞)"
        else " (" + ceil(turnsToNextPolicy).toInt() + ")"
        return cultureString
    }

    private fun getHappinessText(civInfo: Civilization): String {
        var happinessText = civInfo.getHappiness().toString()
        val goldenAges = civInfo.goldenAges
        happinessText +=
            if (goldenAges.isGoldenAge())
                "    {GOLDEN AGE}(${goldenAges.turnsLeftForCurrentGoldenAge})".tr()
            else
                " (${goldenAges.storedHappiness}/${goldenAges.happinessRequiredForNextGoldenAge()})"
        return happinessText
    }
}

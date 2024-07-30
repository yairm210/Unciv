package com.unciv.ui.screens.worldscreen.topbar

import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.unciv.logic.civilization.Civilization
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.setFontColor
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ScalingTableWrapper
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class WorldScreenTopBarStats(topbar: WorldScreenTopBar) : ScalingTableWrapper() {
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
    }

    init {
        isTransform = false


        fun addStat(label: Label, icon: String, isLast: Boolean = false, screenFactory: ()-> BaseScreen?) {
            val image = ImageGetter.getStatIcon(icon)
            val action = {
                val screen = screenFactory()
                if (screen != null) worldScreen.game.pushScreen(screen)
            }
            label.onClick(action)
            image.onClick(action)
            add(label)
            add(image).padBottom(defaultImageBottomPad).size(defaultImageSize).apply {
                if (!isLast) padRight(padRightBetweenStats)
            }
        }

        fun addStat(label: Label, icon: String, overviewPage: EmpireOverviewCategories, isLast: Boolean = false) =
            addStat(label, icon, isLast) { EmpireOverviewScreen(worldScreen.selectedCiv, overviewPage) }

        defaults().pad(defaultTopPad, defaultHorizontalPad, defaultBottomPad, defaultHorizontalPad)
        addStat(goldLabel, "Gold", EmpireOverviewCategories.Stats)
        addStat(scienceLabel, "Science") { TechPickerScreen(worldScreen.selectedCiv) }

        add(happinessContainer).padBottom(defaultImageBottomPad).size(defaultImageSize)
        add(happinessLabel).padRight(padRightBetweenStats)
        val invokeResourcesPage = {
            worldScreen.openEmpireOverview(EmpireOverviewCategories.Resources)
        }
        happinessContainer.onClick(invokeResourcesPage)
        happinessLabel.onClick(invokeResourcesPage)

        addStat(cultureLabel, "Culture") {
            if (worldScreen.gameInfo.ruleset.policyBranches.isEmpty()) null
            else PolicyPickerScreen(worldScreen.selectedCiv, worldScreen.canChangeState)
        }
        if (worldScreen.gameInfo.isReligionEnabled()) {
            addStat(faithLabel, "Faith", EmpireOverviewCategories.Religion, isLast = true)
        } else {
            add("Religion: Off".toLabel())
        }
    }


    private fun rateLabel(value: Float) = value.roundToInt().toStringSigned()

    fun update(civInfo: Civilization) {
        resetScale()

        val nextTurnStats = civInfo.stats.statsForNextTurn
        val goldPerTurn = " (" + rateLabel(nextTurnStats.gold) + ")"
        goldLabel.setText(civInfo.gold.tr() + goldPerTurn)

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
        faithLabel.setText(
            civInfo.religionManager.storedFaith.tr() + " (" + rateLabel(nextTurnStats.faith) + ")"
        )

        scaleTo(worldScreen.stage.width)
    }

    private fun getCultureText(civInfo: Civilization, nextTurnStats: Stats): String {
        var cultureString = rateLabel(nextTurnStats.culture)
        // kotlin Float division by Zero produces `Float.POSITIVE_INFINITY`, not an exception
        val turnsToNextPolicy = (civInfo.policies.getCultureNeededForNextPolicy() - civInfo.policies.storedCulture) / nextTurnStats.culture
        cultureString += when {
            turnsToNextPolicy <= 0f -> " (!)" // Can choose policy right now
            nextTurnStats.culture <= 0 -> " (${Fonts.infinity})" // when you start the game, you're not producing any culture
            else -> " (" + ceil(turnsToNextPolicy).toInt().tr() + ")"
        }
        return cultureString
    }

    private fun getHappinessText(civInfo: Civilization): String {
        var happinessText = civInfo.getHappiness().tr()
        val goldenAges = civInfo.goldenAges
        happinessText +=
            if (goldenAges.isGoldenAge())
                "    {GOLDEN AGE}(${goldenAges.turnsLeftForCurrentGoldenAge})".tr()
            else
                " (${goldenAges.storedHappiness.tr()}/${goldenAges.happinessRequiredForNextGoldenAge().tr()})"
        return happinessText
    }
}

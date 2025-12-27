package com.unciv.ui.components.tilegroups.citybutton

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.widgets.BorderedTable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.padTopDescent
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.DebugUtils

/**
 *  This is the main "button" inside [CityButton], the one with a rounded edge look,
 *  excluding the air units / defence badges above and influence / status indicators below.
 *
 *  It's the only touchable component of `CityButton`.
 *
 *  Components (* only if you're allowed to see - you're owner, spectator, debug flag):
 *  - population number
 *  - pop growth bar (vertical)*
 *  - capital indicator icon
 *  - name
 *  - religion icon (unless [forPopup] is `true`)
 *  - construction turns to completion* ('∞' for perpetual constructions, "-" for idle)
 *  - construction progress bar* (showing 0% for perpetual constructions)
 *  - construction icon* (perpetual ones show empty space instead)
 *  - nation or city-state icon (unless yours)
 */
internal class CityTable(
    city: City,
    viewingCiv: Civilization,
    forPopup: Boolean = false
) : BorderedTable(
    path = "WorldScreen/CityButton/IconTable",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleMidBorderShape
) {
    init {
        isTransform = false
        touchable = Touchable.enabled
        pad(0f, 4f, 0f, 4f) // outer pad left and right

        val selectedCiv = GUI.getSelectedPlayer()
        when {
            city.civ == selectedCiv -> {
                borderOnTop = true
                borderSize = 4f
                bgBorderColor = Color.valueOf("#E9E9AC")
            }
            city.civ.isAtWarWith(selectedCiv) -> {
                borderSize = 4f
                bgBorderColor = Color.valueOf("#E63200")
            }
            else -> {
                borderSize = 2f
                bgBorderColor = ImageGetter.CHARCOAL
            }
        }
        bgColor = city.civ.nation.getOuterColor().cpy().apply { a = 0.9f }

        val isShowDetailedInfo = DebugUtils.VISIBLE_MAP
                || city.civ == selectedCiv
                || viewingCiv.isSpectator()

        addCityPopNumber(city)

        if (isShowDetailedInfo)
            addCityGrowthBar(city)

        addCityText(city, forPopup)

        if (isShowDetailedInfo)
            addCityConstruction(city)

        if (city.civ != viewingCiv)
            addCivIcon(city)
    }

    private fun addCityPopNumber(city: City) {
        val textColor = city.civ.nation.getInnerColor()
        val popLabel = city.population.population.tr()
            .toLabel(fontColor = textColor, fontSize = 18, alignment = Align.center)
        add(popLabel).minWidth(26f)
    }

    private fun addCityGrowthBar(city: City) {
        val table = Table()
        fun calcGrowthPercentage(food: Int) = (food.toFloat() / city.population.getFoodToNextPopulation()).coerceIn(0f, 1f)
        val isGrowing = city.isGrowing()
        val isStarving = city.isStarving()

        val growthPercentage = calcGrowthPercentage(city.population.foodStored)
        val growthBar = ImageGetter.getProgressBarVertical(
            4f, 30f,
            if (isStarving) 1.0f else growthPercentage,
            if (isStarving) Color.RED else CityButton.ColorGrowth,
            ImageGetter.CHARCOAL, 1f
        )
        growthBar.color.a = 0.8f
        if (isGrowing) {
            val nextTurnPercentage = calcGrowthPercentage(city.foodForNextTurn() + city.population.foodStored)
            growthBar.setSemiProgress(CityButton.ColorGrowth.cpy().darken(0.4f), nextTurnPercentage, 1f)
        }

        val turnLabelText = when {
            isGrowing -> {
                val turnsToGrowth = city.population.getNumTurnsToNewPopulation()
                if (turnsToGrowth != null && turnsToGrowth < 100) turnsToGrowth.tr() else Fonts.infinity.toString()
            }
            isStarving -> {
                val turnsToStarvation = city.population.getNumTurnsToStarvation()
                if (turnsToStarvation != null && turnsToStarvation < 100) turnsToStarvation.tr() else Fonts.infinity.toString()
            }
            else -> "-"
        }
        val textColor = city.civ.nation.getInnerColor()
        val turnLabel = turnLabelText.toLabel(fontColor = textColor, fontSize = 13)

        table.add(growthBar).padRight(2f)
        table.add(turnLabel).expandY().bottom()
        add(table).minWidth(6f).padLeft(2f)
    }

    private fun addCityText(city: City, forPopup: Boolean) {
        val textColor = city.civ.nation.getInnerColor()
        val table = Table().apply { isTransform = false }

        if (city.isCapital()) {
            val capitalIcon = when {
                city.civ.isCityState -> ImageGetter.getNationIcon("CityState")
                    .apply { color = textColor }
                else -> ImageGetter.getImage("OtherIcons/Capital")
            }
            table.add(capitalIcon).size(20f).padRight(5f)
        }

        val cityName = city.name.toLabel(fontColor = textColor, alignment = Align.center, hideIcons = true)
        table.add(cityName).growY().center().padTopDescent()

        if (!forPopup) {
            val cityReligion = city.religion.getMajorityReligion()
            if (cityReligion != null) {
                val religionImage = ImageGetter.getReligionIcon(cityReligion.getIconName()).apply {
                    color = textColor }.toGroup(20f)
                table.add(religionImage).size(20f).padLeft(5f)
            }
        }

        table.pack()
        add(table)
            .minHeight(34f)
            .padLeft(10f)
            .padRight(10f)
            .expandY().center()
    }

    private fun addCityConstruction(city: City) {
        val textColor = city.civ.nation.getInnerColor()

        val cityConstructions = city.cityConstructions
        val cityCurrentConstruction = cityConstructions.getCurrentConstruction()

        val progressTable = Table()

        // There's two different "idle" states: No entry in the queue and PerpetualConstruction.idle queued.
        // getCurrentConstruction does not distinuish these, only currentConstructionName does. And we want the icon to only show in the second case.
        var nextTurnPercentage = 0f
        var percentage = 0f
        val icon = if (cityConstructions.currentConstructionName().isEmpty()) null
            else ImageGetter.getConstructionPortrait(cityCurrentConstruction.name, 24f)
        val turns = when (cityCurrentConstruction) {
            PerpetualConstruction.idle -> {
                "-"
            }
            is PerpetualConstruction -> {
                Fonts.infinity.toString()
            }
            else -> {
                cityCurrentConstruction as INonPerpetualConstruction
                val turnsToConstruction = cityConstructions.turnsToConstruction(cityCurrentConstruction.name)
                val workDone = cityConstructions.getWorkDone(cityCurrentConstruction.name).toFloat()
                val cost = cityCurrentConstruction.getProductionCost(cityConstructions.city.civ, cityConstructions.city)
                fun getPercentage(done: Float) = (done / cost).coerceIn(0f, 1f)
                nextTurnPercentage = getPercentage(workDone + city.cityStats.currentCityStats.production)
                percentage = getPercentage(workDone)
                if (turnsToConstruction < 100) turnsToConstruction.tr() else "-"
            }
        }

        val productionBar = ImageGetter.getProgressBarVertical(4f, 30f, percentage,
            CityButton.ColorConstruction, ImageGetter.CHARCOAL, 1f)
        productionBar.setSemiProgress(CityButton.ColorConstruction.cpy().darken(0.4f), nextTurnPercentage, 1f)
        productionBar.color.a = 0.8f

        progressTable.add(turns.toLabel(textColor, 13)).expandY().bottom()
        progressTable.add(productionBar).padLeft(2f)

        add(progressTable).minWidth(6f).padRight(2f)
        add(icon).minWidth(26f)
    }

    private fun addCivIcon(city: City) {
        val icon = when {
            city.civ.isMajorCiv() -> ImageGetter.getNationIcon(city.civ.nation.name)
            else -> ImageGetter.getImage("CityStateIcons/" + city.civ.cityStateType.name)
        }
        icon.color = city.civ.nation.getInnerColor()

        add(icon.toGroup(20f)).minWidth(26f)
    }
}

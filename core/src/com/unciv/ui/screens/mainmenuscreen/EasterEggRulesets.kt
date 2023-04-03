package com.unciv.ui.screens.mainmenuscreen

import com.unciv.logic.HolidayDates
import com.unciv.logic.HolidayDates.Holidays
import com.unciv.logic.map.MapParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import java.time.Month

object EasterEggRulesets {
    fun MapParameters.modifyForEasterEgg() {
        temperatureShift = when(HolidayDates.getMonth()) {
            Month.JANUARY -> -1.4f
            Month.FEBRUARY -> -1.3f
            Month.MARCH -> 0.4f // actually generates a lot of grassland
            Month.AUGUST -> -0.4f // actually generates a lot more desert
            Month.NOVEMBER -> -0.7f
            Month.DECEMBER -> -1.3f
            else -> 0f
        }
        rareFeaturesRichness = 0.15f
    }

    fun getTodayEasterEggRuleset(): Ruleset? {
        return when (HolidayDates.getHolidayByDate()) {
            Holidays.Easter -> easterRuleset()
            Holidays.Samhain -> samhainRuleset()
            Holidays.Xmas -> xmasRuleset()
            else -> null
        }
    }

    private fun easterRuleset() = Ruleset().apply {
        name = "Easter Eggs"
        terrains.add(getWonderEgg())
        for (index in 1..8)
            terrains.add(getNormalEgg(index))
    }

    private fun samhainRuleset() = Ruleset().apply {
        name = "Samhain"
        terrains.add(getWonderPumpkin())
        for (index in 1..5)
            terrains.add(getCandy(index))
        terrains.add(Terrain().apply {
            // disable for autumnal effect
            name = "Grassland"
            type = TerrainType.Land
            uniques.add("Occurs at temperature between [1.0] and [1.0] and humidity between [0.0] and [0.0]")
        })
    }

    private fun xmasRuleset() = Ruleset().apply {
        name = "X-Mas"
        terrains.add(getWonderTree())
        for (index in 1..7)
            terrains.add(getXmas(index))
    }

    private fun LinkedHashMap<String, Terrain>.add(terrain: Terrain) {
        set(terrain.name, terrain)
    }

    private fun getWonder() = Terrain().apply {
        type = TerrainType.NaturalWonder
        happiness = 42f
        food = 9f
        faith = 9f
        occursOn.addAll(listOf("Grassland", "Plains", "Desert"))
        uniques.add("Must be adjacent to [0] [Coast] tiles")
        turnsInto = "Mountain"
        impassable = true
        unbuildable = true
        weight = 999999
    }

    private fun getRareFeature() = Terrain().apply {
        type = TerrainType.TerrainFeature
        happiness = 2f
        food = 1f
        faith = 1f
        occursOn.addAll(listOf("Grassland", "Plains", "Desert", "Tundra", "Snow"))
        uniques.add("Rare feature")
    }

    private fun getWonderEgg() = getWonder().apply {
        name = "Giant Easter Egg"
        civilopediaText = listOf(
            FormattedLine("This monstrous Easter Egg could feed a whole country for a year!"),
            FormattedLine("...Or certain first-world citizens for a week...", color = "#444", indent = 2, size = 15),
            FormattedLine("[See also]: [Easter Egg]", link="terrain/Easter Egg 1")
        )
    }

    private fun getNormalEgg(index: Int) = getRareFeature().apply {
        name = "Easter Egg $index"
        civilopediaText = listOf(
            FormattedLine("This is an Easter Egg, just like those some families hide once a year to have other family members seek them, eat them and get caries."),
            FormattedLine("[See also]: [Giant Easter Egg]", link="terrain/Giant Easter Egg")
        )
    }

    private fun getWonderPumpkin() = getWonder().apply {
        name = "Giant Pumpkin"
        civilopediaText = listOf(
            FormattedLine("Oh, a Halloween Pumpkin!"),
            FormattedLine("Actually, Halloween comes from Samhain, a Gaelic festival marking the beginning of winter.", color = "#444", indent = 2, size = 15),
            FormattedLine("{See also}: {Candies}", link="terrain/Halloween candy 1")
        )
    }

    private fun getCandy(index: Int) = getRareFeature().apply {
        name = "Halloween candy $index"
        civilopediaText = listOf(
            FormattedLine("This is some candy, ritually extorted from innocent seniors by Trick-or-Treaters."),
            FormattedLine("{See also}: {Giant Pumpkin}", link="terrain/Giant Pumpkin")
        )
    }

    private fun getWonderTree() = getWonder().apply {
        name = "Xmas Tree"
        occursOn.addAll(listOf("Tundra", "Snow"))
        uniques.add("Occurs on latitudes from [0] to [70] percent of distance equator to pole")
        uniques.add("Neighboring tiles will convert to [Snow]")
        civilopediaText = listOf(
            FormattedLine("The traditions demand cutting down trees like this to mount them in a home."),
            FormattedLine("For for the whole family! And the cat! And the fire brigade!.", color = "#444", indent = 2, size = 15),
            FormattedLine("{See also}: {Xmas decorations}", link="terrain/Xmas decoration 1")
        )
    }

    private fun getXmas(index: Int) = getRareFeature().apply {
        name = "Xmas decoration $index"
        civilopediaText = listOf(
            FormattedLine("On a certain holiday of culturally varying names, capitalism runs rampant. Some of the more harmless symptoms look like this."),
            FormattedLine("{See also}: {Xmas Tree}", link="terrain/Xmas Tree")
        )
    }
}

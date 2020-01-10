package com.unciv.models.ruleset

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.SerializationException
import com.unciv.colorOrDefault
import com.unciv.models.NationUnique
import com.unciv.models.VictoryType
import com.unciv.models.stats.INamed
import com.unciv.models.translations.Translations
import com.unciv.models.translations.tr
import com.unciv.stringListOrDefault

data class Nation(
        override var name: String,
        val translatedName: String?,
        val cityStateType: CityStateType?,
        val preferredVictoryType: VictoryType,
        val leaderName: String?,
        val declaringWar: String,
        val attacked: String,
        val defeated: String,
        val introduction: String,
        val tradeRequest: String,
        val neutralHello: String,
        val hateHello: String,
        val afterPeace: String,
        val cities: List<String>,
        val neutralLetsHearIt: List<String>,
        val neutralYes: List<String>,
        val neutralNo: List<String>,
        val hateLetsHearIt: List<String>,
        val hateYes: List<String>,
        val hateNo: List<String>,
        val unique: NationUnique?,
        val uniqueDescription: String?,
        val outerColor: Color,
        val innerColor: Color,
        val startBias: List<String>
) : INamed {

    fun getNameTranslation(): String = if (translatedName != null) translatedName else name

    fun getLeaderDisplayName() = if (isCityState()) getNameTranslation() else "[$leaderName] of [${getNameTranslation()}]"

    fun isCityState() = cityStateType != null
    fun isMajorCiv() = !isBarbarian() && !isCityState()
    fun isBarbarian() = name == "Barbarians"

    fun hasUnique(unique: NationUnique): Boolean = this.unique == unique

    companion object {
        fun serializer() = object : Json.ReadOnlySerializer<Nation>() {

            override fun read(json: Json?, jsonData: JsonValue?, type: Class<*>?): Nation {
                if (jsonData == null) throw SerializationException("Can not deserialize Nation.kt")

                return with(jsonData) {
                    Nation(
                            name = getString("name", ""),
                            translatedName = getString("translatedName", null),
                            leaderName = getString("leaderName", null),
                            cityStateType = getString("cityStateType", null)?.let(CityStateType::valueOf),
                            preferredVictoryType = VictoryType.valueOf(getString("preferredVictoryType", VictoryType.Neutral.name)),
                            declaringWar = getString("declaringWar", ""),
                            attacked = getString("attacked", ""),
                            defeated = getString("defeated", ""),
                            introduction = getString("introduction", ""),
                            tradeRequest = getString("tradeRequest", ""),
                            neutralHello = getString("neutralHello", ""),
                            hateHello = getString("hateHello", ""),
                            afterPeace = getString("afterPeace", ""),
                            unique = NationUnique.findByName(jsonData.getString("uniqueName", "")),
                            uniqueDescription = getString("unique", null),
                            cities = stringListOrDefault("cities"),
                            neutralLetsHearIt = stringListOrDefault("neutralLetsHearIt"),
                            neutralYes = stringListOrDefault("neutralYes"),
                            neutralNo = stringListOrDefault("neutralNo"),
                            hateLetsHearIt = stringListOrDefault("hateLetsHearIt"),
                            hateYes = stringListOrDefault("hateYes"),
                            hateNo = stringListOrDefault("hateNo"),
                            outerColor = colorOrDefault("outerColor"),
                            innerColor = colorOrDefault("innerColor"),
                            startBias = stringListOrDefault("startBias")
                    )
                }
            }
        }
    }
}

fun Nation.getUniqueString(ruleset: Ruleset): String {
    val textList = mutableListOf<String>()

    if (uniqueDescription != null) {
        textList += uniqueDescription.tr()
        textList += ""
    }

    for (building in ruleset.buildings.values.filter { it.uniqueTo == name }) {
        val originalBuilding = ruleset.buildings[building.replaces!!]!!

        textList += building.name.tr() + " - {replaces} " + originalBuilding.name.tr()
        val originalBuildingStatMap = originalBuilding.toHashMap()
        for (stat in building.toHashMap())
            if (stat.value != originalBuildingStatMap[stat.key])
                textList += "  " + stat.key.toString().tr() + " " + stat.value.toInt() + " vs " + originalBuildingStatMap[stat.key]!!.toInt()

        for (unique in building.uniques.filter { it !in originalBuilding.uniques })
            textList += "  " + unique.tr()
        if (building.maintenance != originalBuilding.maintenance)
            textList += "  {Maintenance} " + building.maintenance + " vs " + originalBuilding.maintenance
        if (building.cost != originalBuilding.cost)
            textList += "  {Cost} " + building.cost + " vs " + originalBuilding.cost
        if (building.cityStrength != originalBuilding.cityStrength)
            textList += "  {City strength} " + building.cityStrength + " vs " + originalBuilding.cityStrength
        if (building.cityHealth != originalBuilding.cityHealth)
            textList += "  {City health} " + building.cityHealth + " vs " + originalBuilding.cityHealth
        textList += ""
    }

    for (unit in ruleset.units.values.filter { it.uniqueTo == name }) {
        val originalUnit = ruleset.units[unit.replaces!!]!!
        textList += unit.name.tr() + " - {replaces} " + originalUnit.name.tr()
        if (unit.cost != originalUnit.cost)
            textList += "  {Cost} " + unit.cost + " vs " + originalUnit.cost
        if (unit.strength != originalUnit.strength)
            textList += "  {Strength} " + unit.strength + " vs " + originalUnit.strength
        if (unit.rangedStrength != originalUnit.rangedStrength)
            textList += "  {Ranged strength} " + unit.rangedStrength + " vs " + originalUnit.rangedStrength
        if (unit.range != originalUnit.range)
            textList += "  {Range} " + unit.range + " vs " + originalUnit.range
        if (unit.movement != originalUnit.movement)
            textList += "  {Movement} " + unit.movement + " vs " + originalUnit.movement
        if (originalUnit.requiredResource != null && unit.requiredResource == null)
            textList += "  " + "[${originalUnit.requiredResource}] not required".tr()
        for (unique in unit.uniques.filterNot { it in originalUnit.uniques })
            textList += "  " + Translations.translateBonusOrPenalty(unique)
        for (unique in originalUnit.uniques.filterNot { it in unit.uniques })
            textList += "  " + "Lost ability".tr() + "(vs " + originalUnit.name.tr() + "): " + Translations.translateBonusOrPenalty(unique)
        for (promotion in unit.promotions.filter { it !in originalUnit.promotions })
            textList += "  " + promotion.tr() + " (" + Translations.translateBonusOrPenalty(ruleset.unitPromotions[promotion]!!.effect) + ")"

        textList += ""
    }

    for (improvement in ruleset.tileImprovements.values.filter { it.uniqueTo == name }) {
        textList += improvement.name.tr()
        textList += "  " + improvement.clone().toString()
        for (unique in improvement.uniques)
            textList += "  " + unique.tr()
    }

    return textList.joinToString("\n").tr().trim()
}
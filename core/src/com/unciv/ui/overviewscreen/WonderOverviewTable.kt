package com.unciv.ui.overviewscreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.QuestName
import com.unciv.models.ruleset.VictoryType
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaCategories
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel

class WonderOverviewTable(
    private val viewingPlayer: CivilizationInfo,
    @Suppress("unused") private val overviewScreen: EmpireOverviewScreen
): Table() {
    val gameInfo = viewingPlayer.gameInfo
    private val hideReligionItems = !gameInfo.isReligionEnabled()

    private enum class WonderStatus(val label: String) {
        Hidden(""),
        Unknown("Unknown"),
        Unbuilt("Not built"),
        NotFound("Not found"),
        Known("Known"),
        Owned("Owned")
    }

    private class WonderInfo (
        val name: String,
        val category: CivilopediaCategories,
        val status: WonderStatus,
        val civ: CivilizationInfo?,
        val city: CityInfo?,
        val location: TileInfo?
    ) {
        val viewEntireMapForDebug = UncivGame.Current.viewEntireMapForDebug

        fun getImage() = if (status == WonderStatus.Unknown && !viewEntireMapForDebug) null
            else category.getImage?.invoke(name, if (category == CivilopediaCategories.Terrain) 50f else 45f)

        fun getNameColumn() = when {
            viewEntireMapForDebug -> name
            status == WonderStatus.Unknown -> status.label
            else -> name
        }

        fun getStatusColumn() = when {
            status != WonderStatus.Known -> status.label
            civ == null -> status.label
            else -> civ.civName
        }

        fun getLocationColumn() = when {
            status <= WonderStatus.NotFound -> ""
            location == null -> ""
            location.isCityCenter() -> location.getCity()!!.name
            location.getCity() != null -> "Near [${location.getCity()!!}]"
            city != null -> "Somewhere around [$city]"
            viewEntireMapForDebug -> location.position.toString()
            else -> "Far away"
        }
    }

    private val wonders: Array<WonderInfo>

    init {
        wonders = collectInfo()
        createGrid()
    }

    private fun shouldBeDisplayed(wonder: Building) = when {
        Constants.hideFromCivilopediaUnique in wonder.uniques -> false
        Constants.hiddenWithoutReligionUnique in wonder.uniques && hideReligionItems -> false
        else -> wonder.uniqueObjects.filter { unique ->
                unique.placeholderText == "Hidden when [] Victory is disabled"
            }.none { unique ->
                !gameInfo.gameParameters.victoryTypes.contains(VictoryType.valueOf(unique.params[0]))
            }
    }

    private fun collectInfo(): Array<WonderInfo> {
        val collator = UncivGame.Current.settings.getCollatorFromLocale()
        // Maps all World Wonders by their position in sort order to their name
        val allWonderMap: Map<Int, String> =
            gameInfo.ruleSet.buildings.values.asSequence()
            .filter { it.isWonder }
            .sortedWith(compareBy(collator, { it.name.tr() }))
            .withIndex()
            .map { it.index to it.value.name }
            .toMap()
        val wonderCount = allWonderMap.size

        // Inverse of the above
        val wonderIndexMap: Map<String, Int> = allWonderMap.map { it.value to it.key }.toMap()

        // Maps all Natural Wonders on the map by name to their tile
        val allNaturalsMap: Map<String, TileInfo> =
            gameInfo.tileMap.values.asSequence()
            .filter { it.isNaturalWonder() }
            .map { it.naturalWonder!! to it }
            .toMap()
        val naturalsCount = allNaturalsMap.size

        // Natural Wonders sort order index to name
        val naturalsIndexMap: Map<Int, String> = allNaturalsMap.keys
            .sortedWith(compareBy(collator, { it.tr() }))
            .withIndex()
            .map { it.index to it.value }
            .toMap()

        // Pre-populate result with "Unknown" entries
        val wonders = Array(wonderCount + naturalsCount) { index ->
            if (index < wonderCount) {
                val wonder = gameInfo.ruleSet.buildings[allWonderMap[index]!!]!!
                val status = if (shouldBeDisplayed(wonder)) WonderStatus.Unbuilt else WonderStatus.Hidden
                WonderInfo(allWonderMap[index]!!, CivilopediaCategories.Building, status, null, null, null)
            } else {
                WonderInfo(naturalsIndexMap[index - wonderCount]!!, CivilopediaCategories.Terrain, WonderStatus.Unknown, null, null, null)
            }
        }

        for (city in gameInfo.getCities()) {
            for (wonderName in city.cityConstructions.builtBuildings.intersect(wonderIndexMap.keys)) {
                val index = wonderIndexMap[wonderName]!!
                val status = when {
                    viewingPlayer == city.civInfo -> WonderStatus.Owned
                    viewingPlayer.knows(city.civInfo) -> WonderStatus.Known
                    else -> WonderStatus.Unknown
                }
                wonders[index] = WonderInfo(wonderName, CivilopediaCategories.Building, status, city.civInfo, city, city.getCenterTile())
            }
        }

        for ((index, name) in naturalsIndexMap) {
            val tile = allNaturalsMap[name]!!
            val civ = tile.getOwner()
            val status = when {
                civ == viewingPlayer -> WonderStatus.Owned
                name in viewingPlayer.naturalWonders -> WonderStatus.Known
                else -> WonderStatus.NotFound
            }
            if (status == WonderStatus.NotFound && viewingPlayer.questManager.assignedQuests.none {
                    it.questName == QuestName.FindNaturalWonder.value && it.data1 == name
                }) continue
            val city = if (status == WonderStatus.NotFound) null
            else tile.getTilesInDistance(5)
                .filter { it.isCityCenter() }
                .filter { viewingPlayer.knows(it.getOwner()!!) }
                .filter { it in viewingPlayer.viewableTiles }
                .sortedBy { it.aerialDistanceTo(tile) }
                .firstOrNull()?.getCity()
            wonders[index + wonderCount] = WonderInfo(name, CivilopediaCategories.Terrain, status, civ, city, tile)
        }

        return wonders
    }

    fun createGrid() {
        defaults().pad(10f).align(Align.center)
        add()
        add("Name".toLabel())
        add("Status".toLabel())
        add("Location".toLabel())
        row()
        addSeparator()
        for (wonder in wonders) {
            if (wonder.status == WonderStatus.Hidden) continue
            val clickAction: ()->Unit = {
                UncivGame.Current.setWorldScreen()
                UncivGame.Current.worldScreen.mapHolder.setCenterPosition(wonder.location!!.position)
            }
            val image = wonder.getImage()
            if (wonder.location != null && image != null)
                image.onClick(clickAction)
            // Terrain image padding is a bit unpredictable, they need ~5f more. Ensure equal line spacing on name, not image:
            add(image).pad(0f, 10f, 0f, 10f)
            add(wonder.getNameColumn().toLabel()).pad(15f, 10f, 15f, 10f)
            add(wonder.getStatusColumn().toLabel())
            val locationText = wonder.getLocationColumn()
            if (locationText.isNotEmpty()) {
                val locationLabel = locationText.toLabel()
                if (wonder.location != null)
                    locationLabel.onClick(clickAction)
                add(locationLabel).fillY()
            }
            row()
        }
    }
}

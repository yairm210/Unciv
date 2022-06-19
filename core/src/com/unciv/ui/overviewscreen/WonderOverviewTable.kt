package com.unciv.ui.overviewscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.Era
import com.unciv.models.ruleset.QuestName
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.CivilopediaCategories
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel

class WonderOverviewTab(
    viewingPlayer: CivilizationInfo,
    overviewScreen: EmpireOverviewScreen
) : EmpireOverviewTab(viewingPlayer, overviewScreen) {
    val ruleSet = gameInfo.ruleSet

    private val hideReligionItems = !gameInfo.isReligionEnabled()
    private val viewerEra = viewingPlayer.getEraNumber()
    private val startingObsolete = ruleSet.eras[gameInfo.gameParameters.startingEra]!!.startingObsoleteWonders

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
        val groupName: String,
        val groupColor: Color,
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

    private val wonders: Array<WonderInfo> = collectInfo()

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

    private fun shouldBeDisplayed(wonder: Building, wonderEra: Int) = when {
        wonder.hasUnique(UniqueType.HiddenFromCivilopedia) -> false
        wonder.hasUnique(UniqueType.HiddenWithoutReligion) && hideReligionItems -> false
        wonder.name in startingObsolete -> false
        wonder.getMatchingUniques(UniqueType.HiddenWithoutVictoryType)
            .any { unique ->
                !gameInfo.gameParameters.victoryTypes.contains(unique.params[0])
            } -> false
        else -> wonderEra <= viewerEra
    }

    /** Do we know about a natural wonder despite not having found it yet? */
    private fun knownFromQuest(name: String): Boolean {
        // No, *your* civInfo's QuestManager has no idea about your quests
        for (civ in gameInfo.civilizations) {
            for (quest in civ.questManager.assignedQuests) {
                if (quest.assignee != viewingPlayer.civName) continue
                if (quest.questName == QuestName.FindNaturalWonder.value && quest.data1 == name)
                    return true
            }
        }
        return false
    }

    private fun collectInfo(): Array<WonderInfo> {
        val collator = UncivGame.Current.settings.getCollatorFromLocale()

        // Maps all World Wonders by name to their era for grouping
        val wonderEraMap: Map<String, Era> =
            ruleSet.buildings.values.asSequence()
            .filter { it.isWonder }
            .associate { it.name to (ruleSet.eras[ruleSet.technologies[it.requiredTech]?.era()] ?: viewingPlayer.getEra()) }

        // Maps all World Wonders by their position in sort order to their name
        val allWonderMap: Map<Int, String> =
            ruleSet.buildings.values.asSequence()
            .filter { it.isWonder }
            .sortedWith(compareBy<Building> { wonderEraMap[it.name]!!.eraNumber }.thenBy(collator) { it.name.tr() })
            .withIndex()
            .associate { it.index to it.value.name }
        val wonderCount = allWonderMap.size

        // Inverse of the above
        val wonderIndexMap: Map<String, Int> = allWonderMap.map { it.value to it.key }.toMap()

        // Maps all Natural Wonders on the map by name to their tile
        val allNaturalsMap: Map<String, TileInfo> =
            gameInfo.tileMap.values.asSequence()
            .filter { it.isNaturalWonder() }
            .associateBy { it.naturalWonder!! }
        val naturalsCount = allNaturalsMap.size

        // Natural Wonders sort order index to name
        val naturalsIndexMap: Map<Int, String> = allNaturalsMap.keys
            .sortedWith(compareBy(collator) { it.tr() })
            .withIndex()
            .associate { it.index to it.value }

        // Pre-populate result with "Unknown" entries
        val wonders = Array(wonderCount + naturalsCount) { index ->
            if (index < wonderCount) {
                val wonder = ruleSet.buildings[allWonderMap[index]!!]!!
                val era = wonderEraMap[wonder.name]!!
                val status = if (shouldBeDisplayed(wonder, era.eraNumber)) WonderStatus.Unbuilt else WonderStatus.Hidden
                WonderInfo(allWonderMap[index]!!, CivilopediaCategories.Wonder,
                    era.name, era.getColor(), status, null, null, null)
            } else {
                WonderInfo(naturalsIndexMap[index - wonderCount]!!, CivilopediaCategories.Terrain,
                    "Natural Wonders", Color.FOREST, WonderStatus.Unknown, null, null, null)
            }
        }

        for (city in gameInfo.getCities()) {
            for (wonderName in city.cityConstructions.builtBuildings.intersect(wonderIndexMap.keys)) {
                val index = wonderIndexMap[wonderName]!!
                val status = when {
                    viewingPlayer == city.civInfo -> WonderStatus.Owned
                    viewingPlayer.exploredTiles.contains(city.location) -> WonderStatus.Known
                    else -> WonderStatus.NotFound
                }
                wonders[index] = WonderInfo(wonderName, CivilopediaCategories.Wonder,
                    wonders[index].groupName, wonders[index].groupColor,
                    status, city.civInfo, city, city.getCenterTile())
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
            if (status == WonderStatus.NotFound && !knownFromQuest(name)) continue
            val city = if (status == WonderStatus.NotFound) null
            else tile.getTilesInDistance(5)
                .filter { it.isCityCenter() }
                .filter { viewingPlayer.knows(it.getOwner()!!) }
                .filter { it.position in viewingPlayer.exploredTiles }
                .sortedBy { it.aerialDistanceTo(tile) }
                .firstOrNull()?.getCity()
            wonders[index + wonderCount] = WonderInfo(name, CivilopediaCategories.Terrain,
                "Natural Wonders", Color.FOREST, status, civ, city, tile)
        }

        return wonders
    }

    fun createGrid() {
        var lastGroup = ""

        for (wonder in wonders) {
            if (wonder.status == WonderStatus.Hidden) continue
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

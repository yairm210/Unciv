package com.unciv.models.gamebasics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.unciv.models.gamebasics.tech.TechColumn
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TileImprovement
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.models.gamebasics.unit.Promotion
import com.unciv.models.stats.INamed
import kotlin.collections.set

class RuleSet {
    val Buildings = LinkedHashMap<String, Building>()
    val Terrains = LinkedHashMap<String, Terrain>()
    val TileResources = LinkedHashMap<String, TileResource>()
    val TileImprovements = LinkedHashMap<String, TileImprovement>()
    val Technologies = LinkedHashMap<String, Technology>()
    val Units = LinkedHashMap<String, BaseUnit>()
    val UnitPromotions = LinkedHashMap<String, Promotion>()
    val Nations = LinkedHashMap<String, Nation>()
    val PolicyBranches = LinkedHashMap<String, PolicyBranch>()
    val Difficulties = LinkedHashMap<String, Difficulty>()
    val Translations = Translations()

    fun <T> getFromJson(tClass: Class<T>, name: String): T {
        val jsonText = Gdx.files.internal("jsons/$name.json").readString(Charsets.UTF_8.name())
        return Json().apply { ignoreUnknownFields = true }.fromJson(tClass, jsonText)
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    init {

        val gameBasicsStartTime = System.currentTimeMillis()
        val techColumns = getFromJson(Array<TechColumn>::class.java, "Techs")
        for (techColumn in techColumns) {
            for (tech in techColumn.techs) {
                if (tech.cost==0) tech.cost = techColumn.techCost
                tech.column = techColumn
                Technologies[tech.name] = tech
            }
        }

        Buildings += createHashmap(getFromJson(Array<Building>::class.java, "Buildings"))
        for (building in Buildings.values) {
            if (building.requiredTech == null) continue
            val column = Technologies[building.requiredTech!!]!!.column
            if (building.cost == 0)
                building.cost = if (building.isWonder || building.isNationalWonder) column!!.wonderCost else column!!.buildingCost
        }

        Terrains += createHashmap(getFromJson(Array<Terrain>::class.java, "Terrains"))
        TileResources += createHashmap(getFromJson(Array<TileResource>::class.java, "TileResources"))
        TileImprovements += createHashmap(getFromJson(Array<TileImprovement>::class.java, "TileImprovements"))
        Units += createHashmap(getFromJson(Array<BaseUnit>::class.java, "Units"))
        UnitPromotions += createHashmap(getFromJson(Array<Promotion>::class.java, "UnitPromotions"))

        PolicyBranches += createHashmap(getFromJson(Array<PolicyBranch>::class.java, "Policies"))
        for (branch in PolicyBranches.values) {
            branch.requires = ArrayList()
            branch.branch = branch
            for (policy in branch.policies) {
                policy.branch = branch
                if (policy.requires == null) policy.requires = arrayListOf(branch.name)
            }
            branch.policies.last().name = branch.name + " Complete"
        }

        Nations += createHashmap(getFromJson(Array<Nation>::class.java, "Nations/Nations"))
        for(nation in Nations.values) nation.setTransients()

        Difficulties += createHashmap(getFromJson(Array<Difficulty>::class.java, "Difficulties"))

        val gameBasicsLoadTime = System.currentTimeMillis() - gameBasicsStartTime
        println("Loading game basics - "+gameBasicsLoadTime+"ms")

        // Apparently you can't iterate over the files in a directory when running out of a .jar...
        // https://www.badlogicgames.com/forum/viewtopic.php?f=11&t=27250
        // which means we need to list everything manually =/

        val translationStart = System.currentTimeMillis()
        val translationFileNames = listOf("Buildings","Diplomacy,Trade,Nations",
                "NewGame,SaveGame,LoadGame,Options", "Notifications","Other","Policies","Techs",
                "Terrains,Resources,Improvements","Units,Promotions")

        for (fileName in translationFileNames){
            val file = Gdx.files.internal("jsons/Translations/$fileName.json")
            if(file.exists()) {
                Translations.add(file.readString(Charsets.UTF_8.name()))
            }
        }
        val translationFilesTime = System.currentTimeMillis() - translationStart
        println("Loading translation files - "+translationFilesTime+"ms")
    }
}


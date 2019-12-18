package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.Json
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.stats.INamed
import kotlin.collections.set

class Ruleset {
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

    fun <T> getFromJson(tClass: Class<T>, filePath:String): T {
        val jsonText = Gdx.files.internal(filePath).readString(Charsets.UTF_8.name())
        return Json().apply { ignoreUnknownFields = true }.fromJson(tClass, jsonText)
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    fun clone(): Ruleset{
        val newRuleset = Ruleset(false)
        newRuleset.Buildings.putAll(Buildings)
        newRuleset.Difficulties.putAll(Difficulties)
        newRuleset.Nations .putAll(Nations)
        newRuleset.PolicyBranches.putAll(PolicyBranches)
        newRuleset.Technologies.putAll(Technologies)
        newRuleset.Buildings.putAll(Buildings)
        newRuleset.Terrains.putAll(Terrains)
        newRuleset.TileImprovements.putAll(TileImprovements)
        newRuleset.TileResources.putAll(TileResources)
        newRuleset.UnitPromotions.putAll(UnitPromotions)
        newRuleset.Units.putAll(Units)
        return newRuleset
    }

    constructor(load:Boolean=true){
        if(load) load()
    }

    fun load(folderPath: String="jsons") {

        val gameBasicsStartTime = System.currentTimeMillis()
        val techColumns = getFromJson(Array<TechColumn>::class.java, "$folderPath/Techs.json")
        for (techColumn in techColumns) {
            for (tech in techColumn.techs) {
                if (tech.cost==0) tech.cost = techColumn.techCost
                tech.column = techColumn
                Technologies[tech.name] = tech
            }
        }

        Buildings += createHashmap(getFromJson(Array<Building>::class.java, "$folderPath/Buildings.json"))
        for (building in Buildings.values) {
            if (building.requiredTech == null) continue
            val column = Technologies[building.requiredTech!!]!!.column
            if (building.cost == 0)
                building.cost = if (building.isWonder || building.isNationalWonder) column!!.wonderCost else column!!.buildingCost
        }

        Terrains += createHashmap(getFromJson(Array<Terrain>::class.java, "$folderPath/Terrains.json"))
        TileResources += createHashmap(getFromJson(Array<TileResource>::class.java, "$folderPath/TileResources.json"))
        TileImprovements += createHashmap(getFromJson(Array<TileImprovement>::class.java, "$folderPath/TileImprovements.json"))
        Units += createHashmap(getFromJson(Array<BaseUnit>::class.java, "$folderPath/Units.json"))
        UnitPromotions += createHashmap(getFromJson(Array<Promotion>::class.java, "$folderPath/UnitPromotions.json"))

        PolicyBranches += createHashmap(getFromJson(Array<PolicyBranch>::class.java, "$folderPath/Policies.json"))
        for (branch in PolicyBranches.values) {
            branch.requires = ArrayList()
            branch.branch = branch
            for (policy in branch.policies) {
                policy.branch = branch
                if (policy.requires == null) policy.requires = arrayListOf(branch.name)
            }
            branch.policies.last().name = branch.name + " Complete"
        }

        Nations += createHashmap(getFromJson(Array<Nation>::class.java, "$folderPath/Nations/Nations.json"))
        for(nation in Nations.values) nation.setTransients()

        Difficulties += createHashmap(getFromJson(Array<Difficulty>::class.java, "$folderPath/Difficulties.json"))

        val gameBasicsLoadTime = System.currentTimeMillis() - gameBasicsStartTime
        println("Loading game basics - "+gameBasicsLoadTime+"ms")

    }

}


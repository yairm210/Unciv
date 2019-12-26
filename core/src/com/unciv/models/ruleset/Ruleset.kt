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
    var name=""
    val mods = LinkedHashSet<String>()
    val buildings = LinkedHashMap<String, Building>()
    val terrains = LinkedHashMap<String, Terrain>()
    val tileResources = LinkedHashMap<String, TileResource>()
    val tileImprovements = LinkedHashMap<String, TileImprovement>()
    val technologies = LinkedHashMap<String, Technology>()
    val units = LinkedHashMap<String, BaseUnit>()
    val unitPromotions = LinkedHashMap<String, Promotion>()
    val nations = LinkedHashMap<String, Nation>()
    val policyBranches = LinkedHashMap<String, PolicyBranch>()
    val difficulties = LinkedHashMap<String, Difficulty>()

    fun clone(): Ruleset{
        val newRuleset = Ruleset(false)
        newRuleset.add(this)
        return newRuleset
    }

    constructor(load:Boolean=true){
        if(load) load()
    }


    fun <T> getFromJson(tClass: Class<T>, filePath:String): T {
        val file = Gdx.files.internal(filePath)
        if(!file.exists()) return tClass Array<>().first()
        val jsonText = Gdx.files.internal(filePath).readString(Charsets.UTF_8.name())
        return Json().apply { ignoreUnknownFields = true }.fromJson(tClass, jsonText)
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    fun add(ruleset: Ruleset){
        buildings.putAll(ruleset.buildings)
        difficulties.putAll(ruleset.difficulties)
        nations .putAll(ruleset.nations)
        policyBranches.putAll(ruleset.policyBranches)
        technologies.putAll(ruleset.technologies)
        buildings.putAll(ruleset.buildings)
        terrains.putAll(ruleset.terrains)
        tileImprovements.putAll(ruleset.tileImprovements)
        tileResources.putAll(ruleset.tileResources)
        unitPromotions.putAll(ruleset.unitPromotions)
        units.putAll(ruleset.units)
    }

    fun clearExceptModNames(){
        buildings.clear()
        difficulties.clear()
        nations.clear()
        policyBranches.clear()
        technologies.clear()
        buildings.clear()
        terrains.clear()
        tileImprovements.clear()
        tileResources.clear()
        unitPromotions.clear()
        units.clear()
    }

    fun load(folderPath: String="jsons") {

        val gameBasicsStartTime = System.currentTimeMillis()
        val techColumns = getFromJson(Array<TechColumn>::class.java, "$folderPath/Techs.json")
        for (techColumn in techColumns) {
            for (tech in techColumn.techs) {
                if (tech.cost==0) tech.cost = techColumn.techCost
                tech.column = techColumn
                technologies[tech.name] = tech
            }
        }

        buildings += createHashmap(getFromJson(Array<Building>::class.java, "$folderPath/Buildings.json"))
        for (building in buildings.values) {
            if (building.requiredTech == null) continue
            val column = technologies[building.requiredTech!!]!!.column
            if (building.cost == 0)
                building.cost = if (building.isWonder || building.isNationalWonder) column!!.wonderCost else column!!.buildingCost
        }

        terrains += createHashmap(getFromJson(Array<Terrain>::class.java, "$folderPath/Terrains.json"))
        tileResources += createHashmap(getFromJson(Array<TileResource>::class.java, "$folderPath/TileResources.json"))
        tileImprovements += createHashmap(getFromJson(Array<TileImprovement>::class.java, "$folderPath/TileImprovements.json"))
        units += createHashmap(getFromJson(Array<BaseUnit>::class.java, "$folderPath/Units.json"))
        unitPromotions += createHashmap(getFromJson(Array<Promotion>::class.java, "$folderPath/UnitPromotions.json"))

        policyBranches += createHashmap(getFromJson(Array<PolicyBranch>::class.java, "$folderPath/Policies.json"))
        for (branch in policyBranches.values) {
            branch.requires = ArrayList()
            branch.branch = branch
            for (policy in branch.policies) {
                policy.branch = branch
                if (policy.requires == null) policy.requires = arrayListOf(branch.name)
            }
            branch.policies.last().name = branch.name + " Complete"
        }

        nations += createHashmap(getFromJson(Array<Nation>::class.java, "$folderPath/Nations/Nations.json"))
        for(nation in nations.values) nation.setTransients()

        difficulties += createHashmap(getFromJson(Array<Difficulty>::class.java, "$folderPath/Difficulties.json"))

        val gameBasicsLoadTime = System.currentTimeMillis() - gameBasicsStartTime
        println("Loading game basics - "+gameBasicsLoadTime+"ms")

    }

}


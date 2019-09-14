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

object GameBasics {
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
        val jsonText = Gdx.files.internal("jsons/$name.json").readString()
        return Json().apply { ignoreUnknownFields=true }.fromJson(tClass, jsonText)
    }

    fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    init {
            Buildings += createHashmap(getFromJson(Array<Building>::class.java, "Buildings"))
            Terrains += createHashmap(getFromJson(Array<Terrain>::class.java, "Terrains"))
            TileResources += createHashmap(getFromJson(Array<TileResource>::class.java, "TileResources"))
            TileImprovements += createHashmap(getFromJson(Array<TileImprovement>::class.java, "TileImprovements"))
            Units += createHashmap(getFromJson(Array<BaseUnit>::class.java, "Units"))
            UnitPromotions += createHashmap(getFromJson(Array<Promotion>::class.java, "UnitPromotions"))
            PolicyBranches += createHashmap(getFromJson(Array<PolicyBranch>::class.java, "Policies"))
            Nations += createHashmap(getFromJson(Array<Nation>::class.java, "Nations/Nations"))
            Difficulties += createHashmap(getFromJson(Array<Difficulty>::class.java, "Difficulties"))

            val techColumns = getFromJson(Array<TechColumn>::class.java, "Techs")
            for (techColumn in techColumns) {
                for (tech in techColumn.techs) {
                    tech.cost = techColumn.techCost
                    tech.column = techColumn
                    Technologies[tech.name] = tech
                }
            }
            for (building in Buildings.values) {
                if (building.requiredTech == null) continue
                val column = building.getRequiredTech().column
                if (building.cost == 0)
                    building.cost = if (building.isWonder || building.isNationalWonder) column!!.wonderCost else column!!.buildingCost
            }

            for (branch in PolicyBranches.values) {
                branch.requires = ArrayList()
                branch.branch = branch.name
                for (policy in branch.policies) {
                    policy.branch = branch.name
                    if (policy.requires == null) policy.requires = arrayListOf(branch.name)
                }
                branch.policies.last().name = branch.name + " Complete"
            }

        for(file in Gdx.files.internal("jsons/Translations").list())
            Translations.add(file.readString())
        }
}


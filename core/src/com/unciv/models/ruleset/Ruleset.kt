package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.JsonParser
import com.unciv.logic.UncivShowableException
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.tile.TileResource
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.ruleset.unit.Promotion
import com.unciv.models.stats.INamed
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap
import kotlin.collections.set

class Ruleset() {

    private val jsonParser = JsonParser()

    var name = ""
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
    val mods = HashSet<String>()

    fun clone(): Ruleset {
        val newRuleset = Ruleset()
        newRuleset.add(this)
        return newRuleset
    }

    private fun <T : INamed> createHashmap(items: Array<T>): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    private fun <T : INamed, C: Collection<T>> createHashmap(items: C): LinkedHashMap<String, T> {
        val hashMap = LinkedHashMap<String, T>()
        for (item in items)
            hashMap[item.name] = item
        return hashMap
    }

    private fun <T> mergeByReplaces (base: LinkedHashMap<String, T>, new: LinkedHashMap<String, T>)
                where T: IHasReplaces, T: INamed {
        // Special ordering: Uniques directly after their normal counterpart, otherwise by load order
        if (base.isEmpty()) {
            base.putAll(new)
            return
        }
        val newArray = ArrayList<T>(base.size + new.size)
        newArray.addAll(base.values)
        new.values.forEach { item ->
            var index = newArray.size
            if (item.replaces != null) {
                index = 1 + newArray.indexOfFirst { it.name == item.replaces }
                if (index == 0 ) index = newArray.size
            }
            newArray.add(index, item)
        }
        base.clear()
        base.putAll(createHashmap(newArray))
    }

    private fun <T> mergeBySortHint (base: LinkedHashMap<String, T>, new: LinkedHashMap<String, T>)
                where T: IHasSortHint, T: INamed {
        // Special ordering: sortHint then load order - so Mods can insert in a controlled manner
        if (base.isEmpty()) {
            base.putAll(new)
            return
        }
        val list = listOf(base, new)
                .flatMap { it.values }
                .sortedBy { it.sortHint }
        base.clear()
        base.putAll(createHashmap(list))
    }

    private fun <T: INamed> mergeNewToTop (base: LinkedHashMap<String, T>, new: LinkedHashMap<String, T>) {
        // Special ordering: mods-added first
        if (base.isEmpty()) {
            base.putAll(new)
            return
        }
        val baseClone = LinkedHashMap<String, T>()
        baseClone.putAll(base)
        base.clear()
        base.putAll(new.filter { it.key !in baseClone.keys })
        base.putAll(baseClone)
        base.putAll(new.filter { it.key in baseClone.keys })
    }

    fun add(ruleset: Ruleset) {
        // The ruleset member collections maintain inherent load order. Some are displayed without sorting.
        // The effect may not be desirable for mods - their items would come last.
        // Especially in the case of Improvements - they'd get listed after e.g. Removals.
        // Thus the merge functions - they try to preserve load order while still placing items
        // defined by mods in a place not too surprising to the player, and preserving the
        // ability of mods to overwrite an existing definition.
        mergeByReplaces (buildings, ruleset.buildings)
        mergeBySortHint (difficulties, ruleset.difficulties)
        mergeNewToTop (nations, ruleset.nations)
        policyBranches.putAll(ruleset.policyBranches)   // special display
        technologies.putAll(ruleset.technologies)       // special display
        terrains.putAll(ruleset.terrains)               // mod-terrain goes to the end
        mergeBySortHint (tileImprovements, ruleset.tileImprovements)
        tileResources.putAll(ruleset.tileResources)     // mod-resources go to the end
        unitPromotions.putAll(ruleset.unitPromotions)   // mod-promotions to the end
        mergeByReplaces(units, ruleset.units)
    }

    fun clear() {
        buildings.clear()
        difficulties.clear()
        nations.clear()
        policyBranches.clear()
        technologies.clear()
        terrains.clear()
        tileImprovements.clear()
        tileResources.clear()
        unitPromotions.clear()
        units.clear()
        mods.clear()
    }

    fun load(folderHandle :FileHandle ) {
        val gameBasicsStartTime = System.currentTimeMillis()

        val techFile =folderHandle.child("Techs.json")
        if(techFile.exists()) {
            val techColumns = jsonParser.getFromJson(Array<TechColumn>::class.java, techFile)
            for (techColumn in techColumns) {
                for (tech in techColumn.techs) {
                    if (tech.cost == 0) tech.cost = techColumn.techCost
                    tech.column = techColumn
                    technologies[tech.name] = tech
                }
            }
        }

        val buildingsFile = folderHandle.child("Buildings.json")
        if(buildingsFile.exists()) {
            buildings += createHashmap(jsonParser.getFromJson(Array<Building>::class.java, buildingsFile))
            for (building in buildings.values) {
                if (building.cost == 0) {
                    val column = technologies[building.requiredTech]?.column
                            ?: throw UncivShowableException("Building (${building.name}) must either have an explicit cost or a required tech in the same mod")
                    building.cost = if (building.isWonder || building.isNationalWonder) column.wonderCost else column.buildingCost
                }
            }
        }

        val terrainsFile = folderHandle.child("Terrains.json")
        if(terrainsFile.exists()) terrains += createHashmap(jsonParser.getFromJson(Array<Terrain>::class.java, terrainsFile))

        val resourcesFile = folderHandle.child("TileResources.json")
        if(resourcesFile.exists()) tileResources += createHashmap(jsonParser.getFromJson(Array<TileResource>::class.java, resourcesFile))

        val improvementsFile = folderHandle.child("TileImprovements.json")
        if(improvementsFile.exists()) tileImprovements += createHashmap(jsonParser.getFromJson(Array<TileImprovement>::class.java, improvementsFile))

        val unitsFile = folderHandle.child("Units.json")
        if(unitsFile.exists()) units += createHashmap(jsonParser.getFromJson(Array<BaseUnit>::class.java, unitsFile))

        val promotionsFile = folderHandle.child("UnitPromotions.json")
        if(promotionsFile.exists()) unitPromotions += createHashmap(jsonParser.getFromJson(Array<Promotion>::class.java, promotionsFile))

        val policiesFile = folderHandle.child("Policies.json")
        if(policiesFile.exists()) {
            policyBranches += createHashmap(jsonParser.getFromJson(Array<PolicyBranch>::class.java, policiesFile))
            for (branch in policyBranches.values) {
                branch.requires = ArrayList()
                branch.branch = branch
                for (policy in branch.policies) {
                    policy.branch = branch
                    if (policy.requires == null) policy.requires = arrayListOf(branch.name)
                }
                branch.policies.last().name = branch.name + " Complete"
            }
        }

        val nationsFile = folderHandle.child("Nations.json")
        if(nationsFile.exists()) {
            nations += createHashmap(jsonParser.getFromJson(Array<Nation>::class.java, nationsFile))
            for (nation in nations.values) nation.setTransients()
        }

        val difficultiesFile = folderHandle.child("Difficulties.json")
        if(difficultiesFile.exists()) difficulties += createHashmap(jsonParser.getFromJson(Array<Difficulty>::class.java, difficultiesFile))

        val gameBasicsLoadTime = System.currentTimeMillis() - gameBasicsStartTime
        println("Loading game basics - " + gameBasicsLoadTime + "ms")
    }
}

/** Loading mods is expensive, so let's only do it once and
 * save all of the loaded rulesets somewhere for later use
 *  */
object RulesetCache :HashMap<String,Ruleset>(){
    fun loadRulesets(){
        this[""] = Ruleset().apply { load(Gdx.files.internal("jsons")) }

        for(modFolder in Gdx.files.local("mods").list()){
            if (modFolder.name().startsWith('.')) continue
            try{
                val modRuleset = Ruleset()
                modRuleset.load(modFolder.child("jsons"))
                modRuleset.name = modFolder.name()
                this[modRuleset.name] = modRuleset
                println ("Mod loaded successfully: " + modRuleset.name)
            }
            catch (ex:Exception){
                println ("Exception loading mod '${modFolder.name()}':")
                println ("  ${ex.localizedMessage}")
                println ("  (Source file ${ex.stackTrace[0].fileName} line ${ex.stackTrace[0].lineNumber})")
            }
        }
    }

    fun getBaseRuleset() = this[""]!!

    fun getComplexRuleset(mods:Collection<String>): Ruleset {
        val newRuleset = Ruleset()
        // Include base ruleset first so mods can overwrite existing items
        // A nice display order must be handled separately
        newRuleset.add(getBaseRuleset())
        for(mod in mods)
            if(containsKey(mod)) {
                newRuleset.add(this[mod]!!)
                newRuleset.mods += mod
            }
        return newRuleset
    }
}
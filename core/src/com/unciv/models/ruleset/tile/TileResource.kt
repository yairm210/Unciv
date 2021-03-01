package com.unciv.models.ruleset.tile

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import java.util.*

class TileResource : NamedStats() {

    var resourceType: ResourceType = ResourceType.Bonus
    var terrainsCanBeFoundOn: List<String> = listOf()
    var improvement: String? = null
    var improvementStats: Stats? = null

    /**
     * The building that improves this resource, if any. E.G.: Granary for wheat, Stable for cattle.
     *
     */
    @Deprecated("Since 3.13.3 - replaced with '[stats] from [resource] tiles in this city' unique in the building")
    var building: String? = null
    var revealedBy: String? = null
    var unique: String? = null


    fun getDescription(ruleset: Ruleset): String {
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine(resourceType.name.tr())
        stringBuilder.appendLine(this.clone().toString())
        val terrainsCanBeBuiltOnString: ArrayList<String> = arrayListOf()
        terrainsCanBeBuiltOnString.addAll(terrainsCanBeFoundOn.map { it.tr() })
        stringBuilder.appendLine("Can be found on ".tr() + terrainsCanBeBuiltOnString.joinToString(", "))
        stringBuilder.appendln()
        stringBuilder.appendLine("Improved by [$improvement]".tr())
        stringBuilder.appendLine("{Bonus stats for improvement}: ".tr() + "$improvementStats".tr())

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirements().containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty())
            stringBuilder.appendLine("{Buildings that consume this resource}: ".tr()
                    + buildingsThatConsumeThis.joinToString { it.name.tr() })

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirements().containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty())
            stringBuilder.appendLine("{Units that consume this resource}: ".tr()
                    + unitsThatConsumeThis.joinToString { it.name.tr() })

        if (unique != null) stringBuilder.appendLine(unique!!.tr())
        return stringBuilder.toString()
    }
}

data class ResourceSupply(val resource:TileResource,var amount:Int, val origin:String)

class ResourceSupplyList:ArrayList<ResourceSupply>() {
    fun add(resource: TileResource, amount: Int, origin: String) {
        val existingResourceSupply = firstOrNull { it.resource == resource && it.origin == origin }
        if (existingResourceSupply != null) {
            existingResourceSupply.amount += amount
            if (existingResourceSupply.amount == 0) remove(existingResourceSupply)
        } else add(ResourceSupply(resource, amount, origin))
    }

    fun add(resourceSupplyList: ResourceSupplyList) {
        for (resourceSupply in resourceSupplyList)
            add(resourceSupply.resource, resourceSupply.amount, resourceSupply.origin)
    }
}
package com.unciv.models.ruleset.tile

import com.unciv.models.ruleset.Belief
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetStatsObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.stats.Stats
import com.unciv.ui.civilopedia.FormattedLine

class TileResource : RulesetStatsObject() {

    var resourceType: ResourceType = ResourceType.Bonus
    var terrainsCanBeFoundOn: List<String> = listOf()
    var improvement: String? = null
    var improvementStats: Stats? = null
    var revealedBy: String? = null
    @Deprecated("As of 3.16.16 - replaced by uniques")
    var unique: String? = null
    var majorDepositAmount: DepositAmount = DepositAmount()
    var minorDepositAmount: DepositAmount = DepositAmount()
    
    override fun getUniqueTarget() = UniqueTarget.Resource


    override fun makeLink() = "Resource/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        textList += FormattedLine("${resourceType.name} resource", header = 4, color = resourceType.color)
        textList += FormattedLine()

        textList += FormattedLine(this.clone().toString())

        if (terrainsCanBeFoundOn.isNotEmpty()) {
            textList += FormattedLine()
            if (terrainsCanBeFoundOn.size == 1) {
                with (terrainsCanBeFoundOn[0]) {
                    textList += FormattedLine("{Can be found on} {$this}", link = "Terrain/$this")
                }
            } else {
                textList += FormattedLine("{Can be found on}:")
                terrainsCanBeFoundOn.forEach {
                    textList += FormattedLine(it, link = "Terrain/$it", indent = 1)
                }
            }
        }

        if (improvement != null) {
            textList += FormattedLine()
            textList += FormattedLine("Improved by [$improvement]", link = "Improvement/$improvement")
            if (improvementStats != null && !improvementStats!!.isEmpty())
                textList += FormattedLine("{Bonus stats for improvement}: " + improvementStats.toString())
        }

        val buildingsThatConsumeThis = ruleset.buildings.values.filter { it.getResourceRequirements().containsKey(name) }
        if (buildingsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that consume this resource}:")
            buildingsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val unitsThatConsumeThis = ruleset.units.values.filter { it.getResourceRequirements().containsKey(name) }
        if (unitsThatConsumeThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Units that consume this resource}: ")
            unitsThatConsumeThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        val buildingsRequiringThis =  ruleset.buildings.values.filter {
            it.requiredNearbyImprovedResources?.contains(name) == true
        }
        if (buildingsRequiringThis.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{Buildings that require this resource worked near the city}: ")
            buildingsRequiringThis.forEach {
                textList += FormattedLine(it.name, link = it.makeLink(), indent = 1)
            }
        }

        textList += Belief.getCivilopediaTextMatching(name, ruleset)

        return textList
    }
    
    class DepositAmount {
        var sparse: Int = 1
        var default: Int = 2
        var abundant: Int = 3
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

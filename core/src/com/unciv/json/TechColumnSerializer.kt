package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.Json.Serializer
import com.badlogic.gdx.utils.JsonValue
import com.unciv.models.ruleset.tech.TechColumn
import com.unciv.models.ruleset.tech.Technology

/**
 * TeaVM can drop nested generic collection element metadata for TechColumn.techs.
 * Keep tech-column ser/deser explicit so the shared upstream Ruleset loader works unchanged on web.
 */
class TechColumnSerializer : Serializer<TechColumn> {
    override fun write(json: Json, techColumn: TechColumn, knownType: Class<*>?) {
        json.writeObjectStart()
        json.writeValue("columnNumber", techColumn.columnNumber)
        json.writeValue("era", techColumn.era)
        json.writeArrayStart("techs")
        for (tech in techColumn.techs) json.writeValue(tech, Technology::class.java)
        json.writeArrayEnd()
        json.writeValue("techCost", techColumn.techCost)
        if (techColumn.buildingCost >= 0) json.writeValue("buildingCost", techColumn.buildingCost)
        if (techColumn.wonderCost >= 0) json.writeValue("wonderCost", techColumn.wonderCost)
        json.writeObjectEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): TechColumn {
        val techColumn = TechColumn()
        techColumn.columnNumber = jsonData.getInt("columnNumber", techColumn.columnNumber)
        techColumn.era = jsonData.getString("era", "Ancient era")
        techColumn.techCost = jsonData.getInt("techCost", techColumn.techCost)
        techColumn.buildingCost = jsonData.getInt("buildingCost", techColumn.buildingCost)
        techColumn.wonderCost = jsonData.getInt("wonderCost", techColumn.wonderCost)

        val technologies = ArrayList<Technology>()
        var techNode = jsonData.get("techs")?.child
        while (techNode != null) {
            val tech = Technology()
            runCatching { json.readFields(tech, techNode) }
            if (tech.name.isBlank()) tech.name = techNode.getString("name", tech.name)
            tech.cost = techNode.getInt("cost", tech.cost)
            tech.row = techNode.getInt("row", tech.row)
            tech.quote = techNode.getString("quote", tech.quote)
            if (tech.uniques.isEmpty()) {
                tech.uniques = readStringList(techNode.get("uniques"))
            }
            if (tech.prerequisites.isEmpty()) {
                tech.prerequisites = LinkedHashSet(readStringList(techNode.get("prerequisites")))
            }
            technologies += tech
            techNode = techNode.next
        }
        techColumn.techs = technologies
        return techColumn
    }

    private fun readStringList(node: JsonValue?): ArrayList<String> {
        val values = ArrayList<String>()
        var cursor = node?.child
        while (cursor != null) {
            val value = cursor.asString()
            if (value.isNotBlank()) values += value
            cursor = cursor.next
        }
        return values
    }
}

package com.unciv.models.metadata

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.SerializationException
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.overviewscreen.EmpireOverviewTab

class OverviewPersistableData(
    private val map: LinkedHashMap<EmpireOverviewCategories, EmpireOverviewTab.EmpireOverviewTabPersistableData> = linkedMapOf()
) : Json.Serializable,
    Map<EmpireOverviewCategories, EmpireOverviewTab.EmpireOverviewTabPersistableData> by map
{
    var last: EmpireOverviewCategories = EmpireOverviewCategories.Cities

    fun update(pageObjects: Map<EmpireOverviewCategories, EmpireOverviewTab>) {
        for ((category, page) in pageObjects)
            map[category] = page.persistableData
    }

    /**
     *  Gdx Serialize
     *  - Intended format: `"overview":{"Cities":{"sortedBy":"Population","direction":"Descending"},...,"Resources":{"vertical":true}}`
     *  - Outer field name and Object markers are already cared for, so we can begin directly with our fields
     */
    override fun write(json: Json) {
        if (last != EmpireOverviewCategories.Cities)
            json.writeValue("last", last.name, String::class.java)
        for ((category, data) in map) {
            val clazz = category.getPersistDataClass() ?: continue
            if (data.isEmpty()) continue
            json.writeValue(category.name, data, clazz)
        }
    }

    /**
     *  Gdx Deserialize (format see [write])
     *  - Gdx architecture means this operates on a default instance Gdx just created using the zero-args constructor
     *  - Should be tolerant against Enum values that do not exist in current code
     */
    override fun read(json: Json, jsonData: JsonValue) {
        val lastName = jsonData.get("last")?.asString() // Nullable, benign if field missing - getString() is not.
        EmpireOverviewCategories.entries.firstOrNull { it.name == lastName }?.let { last = it }
        if (jsonData.isObject && jsonData.notEmpty())
            for (element in jsonData) readEntry(json, element)
    }

    private fun readEntry(json: Json, element: JsonValue) {
        val name = element.name()
        val category = EmpireOverviewCategories.entries.firstOrNull { it.name == name }
            ?: return // Guards against downgrading to an Unciv missing a category, but also skips "last" here
        val clazz = category.getPersistDataClass()
        try {
            val data = json.readValue(clazz, element)
            map[category] = data
        } catch (ex: SerializationException) {
            // Unknown Enum values inside an EmpireOverviewTabPersistableData subclass end up here
        }
    }
}

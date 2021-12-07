package com.unciv.scripting.api

// Convert an Enum type parameter into a Map of its constants by their names.
inline fun <reified T: Enum<T>> enumToMap() = enumValues<T>().associateBy { it.name }

fun enumQualnameToMap(qualName: String) = Class.forName(qualName).enumConstants.associateBy { (it as Enum<*>).name }
// Always return a built-in Map class instance here, so its gets serialized as JSON object instead of tokenized, and scripts can refer directly to its items.
// I cast to Enum<*> fully expecting it would crash because it felt metaclass-y. But apparently it's just a base class, so it works?

// TODO (Later): Use ClassGraph to automatically find all relevant classes on build.
// https://github.com/classgraph/classgraph/wiki/Build-Time-Scanning


/**
 * For use in ScriptingScope. Allows interpreted scripts to access Unciv Enum constants.
 *
 * Currently exposes enum values as maps.
 */
object ScriptingApiEnums {
    val enumMapsByQualname = LazyMap(::enumQualnameToMap)

    // apiHelpers.Enums.enumMapsByQualname["com.unciv.logic.automation.ThreatLevel"]['VeryLow']
}

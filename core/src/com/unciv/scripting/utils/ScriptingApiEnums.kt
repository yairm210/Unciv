package com.unciv.scripting.utils

import com.unciv.models.stats.Stat


inline fun <reified T: Enum<T>> enumToMap() = enumValues<T>().associateBy{ it.name }


/**
 * For use in ScriptingScope. Allows interpreted scripts to access Unciv Enum constants.
 */
object ScriptingApiEnums {
	val Stat = enumToMap<com.unciv.models.stats.Stat>()
}

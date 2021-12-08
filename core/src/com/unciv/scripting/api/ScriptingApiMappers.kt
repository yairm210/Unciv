package com.unciv.scripting.api

import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.utils.LazyMap

// TODO: Rename this.

object ScriptingApiMappers {

    //// Some ways to access or assign the same property(s) on a lot of instances at once, using only one IPC call. Maybe use parseKotlinPath? Probably preserve order in return instead of mapping from each instance, since script must already have list of tokens anyway (ideally also from a single IPC call). Or preserve mapping, since order could be messy, and to fit with the assignment function?
    fun mapPathCodes(instances: List<Any>, pathcodes: Collection<String>): List<Map<String, Any?>> {
        val pathElementLists = pathcodes.associateWith(Reflection::parseKotlinPath)
        return instances.map {
            val instance = it
            pathElementLists.mapValues {
                Reflection.resolveInstancePath(instance, it.value)
            }
        }
    }

    fun getPathCodesFrom(instance: Any, pathcodes: Collection<String>) = pathcodes.associateWith { Reflection.resolveInstancePath(instance, Reflection.parseKotlinPath(it)) }

    fun getPathCodes(instancesAndPathcodes: Map<Any, List<String>>): Map<Any, Map<String, Any?>> {
        val pathElementLists = LazyMap<String, List<Reflection.PathElement>>(Reflection::parseKotlinPath)
        return instancesAndPathcodes.mapValues { (instance, pathcodes) ->
            pathcodes.associateWith {
                Reflection.resolveInstancePath(instance, pathElementLists[it]!!)
            }
        }
    }

    fun applyPathCodesTo(instance: Any, pathcodesAndValues: Map<String, Any?>): Any {
        for ((pathcode, value) in pathcodesAndValues) {
            Reflection.setInstancePath(instance, Reflection.parseKotlinPath(pathcode), value)
        }
        return instance
    }

    fun applyPathCodes(instancesPathcodesAndValues: Map<Any, Map<String, Any?>>) {
        val pathElementLists = LazyMap<String, List<Reflection.PathElement>>(Reflection::parseKotlinPath)
        for ((instance, assignments) in instancesPathcodesAndValues) {
            for ((pathcode, value) in assignments) {
                Reflection.setInstancePath(instance, pathElementLists[pathcode]!!, value)
            }
        }
    }
}
// st=time.time(); [real(t.baseTerrain) for t in gameInfo.tileMap.values]; print(time.time()-st)
// st=time.time(); apiHelpers.Mappers.mapPathCodes(gameInfo.tileMap.values, ['baseTerrain']); print(time.time()-st)
// FIXME: Gets slower each time run (presumably due to InstanceTokenizer.clean()'s leak).
// Actually around the same speed. Or wait: Is that the InstanceTokenizer slowdown?

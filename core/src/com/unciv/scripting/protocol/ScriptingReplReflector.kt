package com.unciv.scripting.protocol

import com.unciv.scripting.ScriptingScope
import java.lang.System


class ScriptingReplReflector(scriptingScope: ScriptingScope) {

    companion object {
    
        val kotlinObjectIdPrefix = "_unciv-kt-obj@"
    
        fun idKotlinObject(value: Any?): String {
            //Don't depend on parsing this for extracting information.
            //It's basically a readable/dumb UUID.
            //Actually, it might be better to just use a UUID. I think that was actually my original plan.
            return "${kotlinObjectIdPrefix}${System.identityHashCode(value)}:${if (value == null) "null" else value!!::class.qualifiedName}/${value.toString()}"
        }
        
        fun isKotlinObject(value: Any?): Boolean {
            return value is String && value!!.startsWith(kotlinObjectIdPrefix)
        }
    }
    
    val ScriptingObjectCache = mutableMapOf<String, Any>()

    fun resolvePath() { }
}


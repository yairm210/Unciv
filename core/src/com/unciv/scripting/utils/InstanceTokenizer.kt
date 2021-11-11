package com.unciv.scripting.utils

import com.unciv.scripting.ScriptingConstants
import kotlin.math.min
import java.lang.ref.WeakReference
import java.util.UUID


object InstanceTokenizer {

    private val instances = mutableMapOf<String, WeakReference<Any>>()

    private val tokenPrefix
        get() = ScriptingConstants.apiConstants.kotlinInstanceTokenPrefix
    private val tokenMaxLength = 100

    private fun tokenFromInstance(value: Any?): String {
        //Try to keep this human-informing, but don't parse it to extracting information.
        var stringified = value.toString()
        if (stringified.length > tokenMaxLength) {
            stringified = stringified.slice(0..tokenMaxLength-4) + "..."
            println("Slicing.")
        }
        return "${tokenPrefix}${System.identityHashCode(value)}:${if (value == null) "null" else value::class.qualifiedName}/${stringified}:${UUID.randomUUID().toString()}"
    }
    
    private fun isToken(value: Any?): Boolean {
        return value is String && value.startsWith(tokenPrefix)
    }
    
    fun clean(): Unit {
        val badtokens = mutableListOf<String>()
        for ((t, o) in instances) {
            if (o.get() == null) {
                badtokens.add(t)
            }
        }
        for (t in badtokens) {
            instances.remove(t)
        }
    }
    
    fun getToken(obj: Any?): String {
        clean()
        val token = tokenFromInstance(obj)
        instances[token] = WeakReference(obj)
        return token
    }
    
    fun getReal(token: Any?): Any? {
        clean()
        if (isToken(token)) {
            return instances[token]!!.get()
        } else {
            return token
        }
    }
    
}

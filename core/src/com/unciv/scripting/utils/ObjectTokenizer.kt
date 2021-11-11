package com.unciv.scripting.utils

import com.unciv.scripting.ScriptingConstants
import kotlin.math.min
import java.lang.ref.WeakReference
import java.util.UUID


object ObjectTokenizer {

    private val objs = mutableMapOf<String, WeakReference<Any>>()

    private val tokenPrefix
        get() = ScriptingConstants.apiConstants.kotlinObjectTokenPrefix
    private val tokenMaxLength = 100

    private fun tokenFromObject(value: Any?): String {
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
        for ((t, o) in objs) {
            if (o.get() == null) {
                badtokens.add(t)
            }
        }
        for (t in badtokens) {
            objs.remove(t)
        }
    }
    
    fun getToken(obj: Any?): String {
        clean()
        val token = tokenFromObject(obj)
        objs[token] = WeakReference(obj)
        return token
    }
    
    fun getReal(token: Any?): Any? {
        clean()
        if (isToken(token)) {
            return objs[token]!!.get()
        } else {
            return token
        }
    }
    
}

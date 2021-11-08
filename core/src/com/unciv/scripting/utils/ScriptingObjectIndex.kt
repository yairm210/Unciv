package com.unciv.scripting.utils

import kotlin.collections.MutableMap
import java.lang.ref.WeakReference
import java.util.UUID


object ScriptingObjectIndex {

    private val objs = mutableMapOf<String, WeakReference<Any>>()

    private val kotlinObjectIdPrefix = "_unciv-kt-obj@" // load from ScriptAPIConstants.json

    private fun idKotlinObject(value: Any?): String {
        //Try to keep this human-informing, but don't parse it to extracting information.
        return "${kotlinObjectIdPrefix}${System.identityHashCode(value)}:${if (value == null) "null" else value::class.qualifiedName}/${value.toString()}:${UUID.randomUUID().toString()}"
    }
    
    private fun isKotlinToken(value: Any?): Boolean {
        return value is String && value.startsWith(kotlinObjectIdPrefix)
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
        val token = idKotlinObject(obj)
        objs[token] = WeakReference(obj)
        return token
    }
    
    fun getReal(token: Any?): Any? {
        clean()
        if (isKotlinToken(token)) {
            return objs[token]!!.get()
        } else {
            return token
        }
    }
    
}

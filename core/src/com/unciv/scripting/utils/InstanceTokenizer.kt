package com.unciv.scripting.utils

import com.unciv.scripting.ScriptingConstants
import kotlin.math.min
import java.lang.ref.WeakReference
import java.util.UUID


/**
 * Object that returns unique strings for any Kotlin/JVM instances, and then allows the original instances to be accessed given the token strings.
 *
 * Uses WeakReferences, so should not cause memory leaks on its own.
 *
 * Combined with TokenizingJson in ScriptingProtocol, allows scripts to handle unserializable objects, and use them in property/key/index assignments and function calls.
 */
object InstanceTokenizer {

    /**
     * Weakmap of currently known token strings to WeakReferences of the Kotlin/JVM instances they represent.
     */
    private val instances = mutableMapOf<String, WeakReference<Any>>()

    /**
     * Prefix that all generated token strings should start with.
     *
     * A string should be identifiable as a token string by checking whether it starts with this prefix.
     * As such, it is useful for this value to be defined somewhere that scripts can access too.
     */
    private val tokenPrefix
        //I considered other structures like integer IDs, and objects with a particular structure and key. But semantically and syntactically, immutable and often-singleton/interned strings are really the best JSON representations of completely opaque Kotlin/JVM objects.
        get() = ScriptingConstants.apiConstants.kotlinInstanceTokenPrefix
    /**
     * Length to clip generated token strings to. Here in case token string generation uses the instance's .toString(), which it currently does.
     */
    private val tokenMaxLength = 100

    /**
     * Generate a distinctive token string to represent a Kotlin/JVM object.
     *
     * Should be human-informing when read. But should not be parsed, and should not encourage being parsed, to extract information.
     * Only creates string. Does not automatically register resulting token string for detokenization.
     *
     * @param value: Instance to tokenize.
     * @return Token string.
     */
    private fun tokenFromInstance(value: Any?): String {
        var stringified = value.toString()
        if (stringified.length > tokenMaxLength) {
            stringified = stringified.slice(0..tokenMaxLength-4) + "..."
        }
        return "${tokenPrefix}${System.identityHashCode(value)}:${if (value == null) "null" else value::class.qualifiedName}/${stringified}:${UUID.randomUUID().toString()}"
    }

    /**
     * @param value Any value or instance.
     * @return Whether or not it is a token string.
     */
    private fun isToken(value: Any?): Boolean {
        return value is String && value.startsWith(tokenPrefix)
    }

    /**
     * Remove all tokens and WeakReferences whose instances have already been garbage-collected.
     */
    fun clean(): Unit {
        //FIXME (if I become a problem): Because a new unique token is currently generated even if the instance is already tokenized as something else, this will eventually get slower over time if a script makes lots of requests that result in new instance tokens for objects that last a long time (E.G. uncivGame). And since any stored instances should ideally be WeakReferences to prevent garbage collection from being broken for *all* instances, fixing that may not be as simple as checking for existing tokens to reuse them.
        //TODO: Probably keep another map of instance hashes to weakrefs and their existing token?
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

    /**
     * @param obj Instance to tokenize.
     * @return Token string that can later be detokenized back into the original instance.
     */
    fun getToken(obj: Any?): String {
        clean()
        val token = tokenFromInstance(obj)
        instances[token] = WeakReference(obj)
        return token
    }

    /**
     * Detokenize a token string into the real Kotlin/JVM instance it represents.
     *
     * Accepts non-token values, and passes them through unchanged. So can be used to E.G. blindly transform a Collection/JSON Array that only maybe contains some token strings by being called on every element.
     *
     * @param token Previously generated token, or any instance or value.
     * @throws NullPointerException (I think) if given a token string but not a valid one (E.G. if its object was garbage-collected, or if it's fake).
     * @return Real instance from detokenizing input if given a token string, input value or instance unchanged if not given a token string.
     */
    fun getReal(token: Any?): Any? {
        clean()
        if (isToken(token)) {
            return instances[token]!!.get()
        } else {
            return token
        }
    }

}

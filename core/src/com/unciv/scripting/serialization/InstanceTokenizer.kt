package com.unciv.scripting.serialization

import com.unciv.scripting.ScriptingConstants
import com.unciv.scripting.utils.ScriptingDebugParameters
import com.unciv.scripting.utils.WeakIdentityMap
//import kotlin.math.min
import java.lang.ref.WeakReference // Could use SoftReferences— Would seem convenient, but probably lead to mysterious bugs in scripts.
import java.util.UUID
import kotlin.math.floor
import kotlin.math.log
import kotlin.math.pow


/**
 * Object that returns unique strings for any Kotlin/JVM instances, and then allows the original instances to be accessed given the token strings.
 *
 * Uses WeakReferences, so should not cause memory leaks on its own.
 *
 * Combined with TokenizingJson in ScriptingProtocol, allows scripts to handle unserializable objects, and use them in property/key/index assignments and function calls.
 */
object InstanceTokenizer {

    // Could even potentially get rid of `.registeredInstances` completely by automatically registering/reference counting in the JVM and freeing in scripting language destructors. But JS apparently doesn't give any way to control garbage collection, so the risk of memory leaks wouldn't be worth it.

    /**
     * Map of currently known token strings to WeakReferences of the Kotlin/JVM instances they represent.
     */
    private val instancesByTokens = mutableMapOf<String, WeakReference<Any>>()

    // Map of WeakReferences of Kotlin/JVM instances to token strings that represent them.
    private val tokensByInstances = WeakIdentityMap<Any?, String>()
    // Without this, running the Python tests twice in a row led to a token count exceeding 32768 (and over 16348 after the first run) as of this comment, and very significant slowdown.
    // With it… It's actually only slightly less, and the performance still hurts a lot.


    // Logarithm and base for the number of known tokens after the last cleaning. Used for printing debug info.
    private var lastTokenCountLog: Int = 0
    private const val tokenCountLogBase = 2f

//    private val instanceHashes = mutableMapOf<Pair<Int, arrayListOf<Pair<String, WeakReference<Any>>>()>>()
    // TODO: See note under clean().

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
    private const val tokenMaxLength = 100

//    private fun newTokenFromInstance(value: Any?): String {
//    } // Move existing code here, publicize tokenFromInstance, check against existing WeakMap of tokens, and maybe forbid nullable input… Hm. Need to use identity instead of equality comparison. Oh wow. They have "WeakIdentityHashMap", the maniacs.

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
        var token: String? = tokensByInstances[value] // Atomicity. Separating containment check would give the GC a chance to clear the key.
        if (token != null) {
            return token
        }
        var stringified: String
        stringified = try { // Because this can be overridden, it can fail. E.G. MapUnit.toString() depends on a lateinit.
            value.toString()
        } catch (e: Exception) {
            "?" // Use exception text?
        }
        if (stringified.length > tokenMaxLength) {
            stringified = stringified.slice(0..tokenMaxLength -4) + "..."
        }
        token = "$tokenPrefix${System.identityHashCode(value)}:${if (value == null) "null" else value::class.qualifiedName}/${stringified}:${UUID.randomUUID().toString()}"
        tokensByInstances[value] = token
        return token
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
    fun clean() {
        //FIXME (if I become a problem): Because a new unique token is currently generated even if the instance is already tokenized as something else, this will eventually get slower over time if a script makes lots of requests that result in new instance tokens for objects that last a long time (E.G. uncivGame). And since any stored instances should ideally be WeakReferences to prevent garbage collection from being broken for *all* instances, fixing that may not be as simple as checking for existing tokens to reuse them.
        //TODO: Probably keep another map of instance hashes to weakrefs and their existing token? The hashes will have to have counts instead of just containment, since otherwise a hash collision would cause the earlier token to become inaccessible.
        // Can I use WeakReferences as keys? Do they implement hash and equality? Huh, WeakHashMap is a thing here too. Cool. Auto cleaning. And could check against for cleaning the other map. Hm. Need to use identity instead of equality comparison. Oh wow. They have "WeakIdentityHashMap", the maniacs.
        tokensByInstances.clean()
        val badtokens = mutableListOf<String>()
        // TODO: Print messages on major size milestone changes.
        for ((t, o) in instancesByTokens) {
            if (o.get() == null) {
                badtokens.add(t)
            }
        }
        // TODO: Maybe O(n) cleaning strategy is just not great.
        for (t in badtokens) {
            instancesByTokens.remove(t)
        }
        if (ScriptingDebugParameters.printTokenizerMilestones) {
            val count = instancesByTokens.size
            val current = floor(log(count.toFloat(), tokenCountLogBase)).toInt()
            if (current != lastTokenCountLog) {
                println("${this::class.simpleName} now contains ${count} tokens.")
            }
            lastTokenCountLog = current
        }
    }

    /**
     * @param obj Instance to tokenize.
     * @return Token string that can later be detokenized back into the original instance.
     */
    fun getToken(obj: Any?): String { // TOOD: Switch to Any, since null will just be cleaned anyway?
        clean()
        val token = tokenFromInstance(obj)
        instancesByTokens[token] = WeakReference(obj)
        return token
    }

    /**
     * Detokenize a token string into the real Kotlin/JVM instance it represents.
     *
     * Accepts non-token values, and passes them through unchanged. So can be used to E.G. blindly transform a Collection/JSON Array that only maybe contains some token strings by being called on every element.
     *
     * @param token Previously generated token, or any instance or value.
     * @throws NullPointerException If given a token string but not a valid one (E.G. if its object was garbage-collected, or if it's fake).
     * @return Real instance from detokenizing input if given a token string, input value or instance unchanged if not given a token string.
     */
    fun getReal(token: Any?): Any? {
        clean()
        if (isToken(token)) {
            return instancesByTokens[token]!!.get()// TODO: Add another non-null assertion here? Unknown tokens and expired tokens are only a cleaning cycle apart, which seems race condition-y.
            // TODO: Helpful exception message for invalid tokens.
        } else {
            return token
        }
    }

}

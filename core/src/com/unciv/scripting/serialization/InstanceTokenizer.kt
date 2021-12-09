package com.unciv.scripting.serialization

import com.unciv.scripting.ScriptingConstants
import com.unciv.scripting.utils.ScriptingDebugParameters
import com.unciv.scripting.utils.WeakIdentityMap
import java.lang.ref.WeakReference // Could use SoftReferences— Would seem convenient, but probably lead to mysterious bugs in scripts.
import java.util.UUID
import kotlin.math.floor
import kotlin.math.log
import kotlin.random.Random


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
     * Used for basic functionality of tracking tokens and transforming them back into arbitrary instances.
     */
    private val instancesByTokens = mutableMapOf<String, WeakReference<Any>>()

    // Map of WeakReferences of Kotlin/JVM instances to token strings that represent them.
    // Used to reuse existing tokens for previously tokenized objects, improving performance and avoiding a memory leak.
    private val tokensByInstances = WeakIdentityMap<Any?, String>()
    // Without this, repeatedly running the Python tests led to a token count over 16835 after the first run, exceeding 25252 after the second run, and over 37887 after the third run, as of this comment.
    // With it: Over 11223 after the first run, back down to 4399 in the middle of the second run and then up again to over 11223, down to 4396 in the middle of the third and back up to over 11223 afterwards, over 85000 following ten runs in a non-stop Python loop, but back down to 7809 after running as separate commands again and hitting the next cleanup at 127834.
    // (Oh. Yeah. Duh. Because I made ScriptingProtocol save everything in the same script execution from being garbage collected, cleanup doesn't happen much in an ongoing Python loop. Oh well; That took a long time to run, and no script should be doing anywhere near that much in one go anyway (and even if it is processing that much data in one go, it should use its own data structures and only write out to Unciv at the very end).)

    // Logarithm of number of known tokens after the last cleaning, with tokenCountLogBase as base. Cleaning is triggered when changing this.
    private var lastTokenCountLog: Int = 0
    // Logarithm base for lastTokenCountLog. Acts as factor threshold for cleaning invalid WeakReferences.
    private const val tokenCountLogBase = 1.3f

    // Above this value, have a forceCleanChance to perform cleaning even if a no token count thresholds have been crossed.
    // Needed because in theory, token count, as measured from a Collection size, can apparently max out with a large enough collection.
    private const val forceCleanThreshold = Int.MAX_VALUE / 2
    // Chance of performing a cleaning when token count is above forceCleanThreshold.
    private const val forceCleanChance = 0.001

    // Compile-time flag on whether to keep track of previously tokenized objects and always reuse the same tokens for them.
    // Disabling this could cause long-lived objects to create a lot of tokens that have no way of ever being cleaned.
    // Keep it set to true to avoid scripts causing a memory leak and degrading token cleaning performance, basically.
    private const val tokenReuse = true
    // So why leave the flag in? It marks where the reuse behaviour happens, and helps keep its relationship to core functionality clear.

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
        if (tokenReuse && token != null) {
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
        if (tokenReuse) {
            tokensByInstances[value] = token
        }
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
     *
     * Runs in O(n) time relative to token count.
     *
     * Should not have to be called manually.
     */
    fun clean() {
        val badtokens = if (tokenReuse)
            tokensByInstances.clean(true)!!
        else
            instancesByTokens.entries.asSequence().filter { it.value.get() == null }.map { it.key }.toList() // Technically the .toLists()'s not needed, but this is a legacy thing anyway— TODO: Wait, can't you type as Iterable?
        for (t in badtokens) {
            instancesByTokens.remove(t)
        }
    }

    // Try to clean all invalid tokens.

    // Only does anything if detects a sufficient change in token count from the last cleanup, as defined by lastTokenCountLog and tokenCountLogBase.

    // Should not have to be called manually.
    fun tryClean() {
        val count = instancesByTokens.size
        val countLog = floor(log(count.toFloat(), tokenCountLogBase)).toInt()
        if (countLog != lastTokenCountLog || (count >= forceCleanThreshold && Random.nextDouble() <= forceCleanChance)) {
            // In theory, could cause repeated and inefficient bouncing near a trigger threshold— Unlikeliness aside, that also won't happen because ScriptingProtocol prevents new tokens from being freed per script execution.
            // forceCleanThreshold should make sure cleaning still happens even with count clipped to MAX_INT.
            if (ScriptingDebugParameters.printTokenizerMilestones) {
                println("${this::class.simpleName} now tracks ${count} tokens and ${tokensByInstances.size} instances. Cleaning.")
            }
            clean()
            lastTokenCountLog = countLog
        }
    }

    /**
     * @param obj Instance to tokenize.
     * @return Token string that can later be detokenized back into the original instance.
     */
    fun getToken(obj: Any?): String { // TOOD: Switch to Any, since null will just be cleaned anyway?
        tryClean()
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
        tryClean()
        return if (isToken(token))
            instancesByTokens[token]!!.get()// TODO: Add another non-null assertion here? Unknown tokens and expired tokens are only a cleaning cycle apart, which seems race condition-y.
            // TODO: Helpful exception message for invalid tokens?
        else
            token
    }

}

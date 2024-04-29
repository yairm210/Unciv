package com.unciv.utils

import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

/**
 *  A replacement for `lazy {}` that allows use as resettable cache.
 *
 *  Typical usage: `val foo by resettableLazy { bar() }; ::foo.resetLazy()`
 *
 *  Code mirrors [SynchronizedLazyImpl] closely, including adapted Kdoc on the accessor extensions.
 *  No support for the other [LazyThreadSafetyMode]s.
 *
 *  - Note this does ***not*** extend the default [Lazy] interface, but it has all the same elements.
 *    When doing so the compiler will redirect instantiation to its own 'publication' implementation.
 */
class ResettableLazy<out T>(private val initializer: () -> T, lock: Any?) {
    private var _value: Any? = UninitializedValue
    private val lock = lock ?: this

    val value: T
        get() {
            val v1 = _value
            if (v1 !== UninitializedValue)
                @Suppress("UNCHECKED_CAST")
                return v1 as T
            return synchronized(lock) {
                val v2 = _value
                if (v2 !== UninitializedValue)
                    @Suppress("UNCHECKED_CAST")
                    v2 as T
                else {
                    val newValue = initializer()
                    _value = newValue
                    newValue
                }
            }
        }

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    internal fun reset() {
        _value = UninitializedValue
    }

    private fun isInitialized() = _value !== UninitializedValue
    override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."
    private object UninitializedValue
}

/**
 * Creates a new instance of the [ResettableLazy] that uses the specified initialization function [initializer].
 *
 * Note: ResettableLazy only supports thread-safety mode [LazyThreadSafetyMode.SYNCHRONIZED].
 * If the initialization of a value throws an exception, it will attempt to reinitialize the value at next access.
 *
 * Note that the returned instance uses itself to synchronize on. Do not synchronize from external code on
 * the returned instance as it may cause accidental deadlock. Also this behavior can be changed in the future.
 */
fun <T> resettableLazy (initializer: () -> T) = ResettableLazy(initializer, null)

/**
 * Creates a new instance of the [ResettableLazy] that uses the specified initialization function [initializer].
 *
 * Note: ResettableLazy only supports thread-safety mode [LazyThreadSafetyMode.SYNCHRONIZED].
 * If the initialization of a value throws an exception, it will attempt to reinitialize the value at next access.
 *
 * The returned instance uses the specified [lock] object to synchronize on.
 */
@Suppress("unused")
fun <T> resettableLazy (lock: Any, initializer: () -> T) = ResettableLazy(initializer, lock)

/**
 *  Reset the [ResettableLazy] feeding this field.
 *
 *  The cached value is forgotten, and the next time the property is accessed will call the initializer again.
 *
 *  @throws ClassCastException If called on a field not using the [ResettableLazy] delegate.
 *  @throws NullPointerException I called on a non-delegated field.
 */
fun <T> KProperty0<T>.resetLazy() {
    isAccessible = true
    @Suppress("UNCHECKED_CAST")
    (getDelegate() as ResettableLazy<T>).reset()
}

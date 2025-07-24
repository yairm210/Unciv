package com.unciv.logic.event

import com.unciv.logic.event.EventBus.send
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

/**
 * The heart of the event system. Significant game events are sent/received here.
 *
 * Use [send] to send events and [EventReceiver.receive] to receive events.
 *
 * **Do not use this for every communication between modules**. Only use it for events that might be relevant for a wide variety of modules or
 * significantly affect the game state, i.e. buildings being created, units dying, new multiplayer data available, etc.
 */
@Suppress("UNCHECKED_CAST") // Through using the "map by KClass" pattern, we ensure all methods get called with correct argument type
object EventBus {

    private val listeners = mutableMapOf<KClass<*>, MutableList<EventListenerWeakReference<*>>>()

    /**
     * Only use this from the render thread. For example, in coroutines launched by [com.unciv.ui.crashhandling.launchCrashHandling]
     * always wrap the  call in [com.unciv.ui.crashhandling.postCrashHandlingRunnable].
     *
     * We could use a generic method like `sendOnRenderThread` or make the whole event system asynchronous in general,
     * but doing it like this makes debugging slightly easier.
     */
    fun <T : Event> send(event: T) {
        val eventListeners = getListeners(event::class) as Set<EventListener<T>>
        for (listener in eventListeners) {
            val filter = listener.filter
            if (filter == null || filter(event)) {
                listener.eventHandler(event)
            }
        }
    }

    private fun <T : Event> getListeners(eventClass: KClass<T>): Set<EventListener<*>> {
        val classesToListenTo = getClassesToListenTo(eventClass) // This is always a KClass
        // Set because we don't want to notify the same listener multiple times
        return buildSet {
            for (classToListenTo in classesToListenTo) {
                addAll(updateActiveListeners(classToListenTo))
            }
        }
    }

    /** To be able to listen to an event class and get notified even when child classes are sent as an event */
    private fun <T : Event> getClassesToListenTo(eventClass: KClass<T>): List<KClass<*>> {
        return getSuperClasses(eventClass) + eventClass
    }

    private fun getSuperClasses(kClass: KClass<*>): List<KClass<*>> {
        if (kClass.supertypes.size == 1 && kClass.supertypes[0] == Any::class) return emptyList()
        return kClass.supertypes
            .map { it.classifier as KClass<*> }
            .flatMap { getSuperClasses(it) + it }
            .filter { it != Any::class }
    }

    /** Removes all listeners whose WeakReference got collected and returns the ones that are still active */
    private fun updateActiveListeners(eventClass: KClass<*>): List<EventListener<*>> {
        return buildList {
            val listenersWeak = listeners[eventClass] ?: return listOf()
            val iterator = listenersWeak.listIterator()
            while (iterator.hasNext()) {
                val listener = iterator.next()
                val eventHandler = listener.eventHandler.get()
                if (eventHandler == null) {
                    // eventHandler got garbage collected, prevent WeakListener memory leak
                    iterator.remove()
                } else {
                    add(EventListener(eventHandler, listener.filter.get()))
                }
            }
        }
    }


    private fun <T: Event> receive(eventClass: KClass<T>, filter: ((T) -> Boolean)? = null, eventHandler: (T) -> Unit) {
        if (listeners[eventClass] == null) {
            listeners[eventClass] = mutableListOf()
        }
        listeners[eventClass]!!.add(EventListenerWeakReference(eventHandler, filter))
    }

    private fun cleanUp(eventHandlers: Map<KClass<*>, MutableList<Any>>) {
        for ((kClass, toRemove) in eventHandlers) {
            val registeredListeners = listeners.get(kClass)
            registeredListeners?.removeAll {
                val eventHandler = it.eventHandler.get()
                eventHandler == null || (eventHandler as Any) in toRemove
            }
        }
    }

    /**
     * Used to receive events by the [EventBus].
     *
     * Usage:
     *
     * ```
     * class SomeClass {
     *     private val events = EventReceiver()
     *
     *     init {
     *         events.receive(SomeEvent::class) {
     *             // do something when the event is received.
     *         }
     *     }
     *
     *     // Optional
     *     cleanup() {
     *         events.stopReceiving()
     *     }
     * }
     * ```
     *
     * The [stopReceiving] call is optional. Event listeners will be automatically garbage collected. However, garbage collection is non-deterministic, so it's
     * possible that the events keep being received for quite a while even after a class is unused. [stopReceiving] immediately cleans up all listeners.
     *
     * To have event listeners automatically garbage collected, we need to use [WeakReference]s in the event bus. For that to work, though, the class
     * that wants to receive events needs to hold references to its own event listeners. [EventReceiver] allows to do that while also providing the
     * interface to start receiving events.
     */
    class EventReceiver {

        val eventHandlers = mutableMapOf<KClass<*>, MutableList<Any>>()
        val filters: MutableList<Any> = mutableListOf()

        /**
         * Listen to the event with the given [eventClass] and all events that subclass it. Use [stopReceiving] to stop listening to all events.
         *
         * The listeners will always be called on the main GDX render thread.
         *
         * @param T The event class holding the data of the event, or simply [Event].
         */
        fun <T: Event> receive(eventClass: KClass<T>, filter: ((T) -> Boolean)? = null, eventHandler: (T) -> Unit) {
            if (filter != null) {
                filters.add(filter)
            }
            if (eventHandlers[eventClass] == null) {
                eventHandlers[eventClass] = mutableListOf()
            }
            eventHandlers[eventClass]!!.add(eventHandler)

            EventBus.receive(eventClass, filter, eventHandler)
        }

        /**
         * Stops receiving all events, cleaning up all event listeners.
         */
        fun stopReceiving() {
            cleanUp(eventHandlers)
            eventHandlers.clear()
            filters.clear()
        }
    }

}

/** Exists so that eventHandlers and filters do not get garbage-collected *while* we are passing them around in here,
 * otherwise we would only need [EventListenerWeakReference] */
private class EventListener<T>(
    val eventHandler: (T) -> Unit,
    val filter: ((T) -> Boolean)? = null
)

private class EventListenerWeakReference<T>(
    eventHandler: (T) -> Unit,
    filter: ((T) -> Boolean)? = null
) {
    val eventHandler = WeakReference(eventHandler)
    val filter = WeakReference(filter)
}

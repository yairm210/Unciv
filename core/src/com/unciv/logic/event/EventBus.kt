package com.unciv.logic.event

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
@Suppress("UNCHECKED_CAST") // Through using the "map by KClass", we ensure all methods get called with correct argument type
object EventBus {
    // This is one of the simplest implementations possible. If it is ever useful, this could be changed to
    private val receivers = mutableMapOf<KClass<*>, MutableList<EventListener<*>>>()

    /**
     * Only use this from the render thread. For example, in coroutines launched by [com.unciv.ui.crashhandling.launchCrashHandling]
     * always wrap the  call in [com.unciv.ui.crashhandling.postCrashHandlingRunnable].
     *
     * We could use a generic method like `sendOnRenderThread` or make the whole event system asynchronous in general,
     * but doing it like this makes debugging slightly easier.
     */
    fun <T : Event> send(event: T) {
        val listeners = receivers[event::class]
        if (listeners == null) return
        val iterator = listeners.listIterator()
        while (iterator.hasNext()) {
            val listener = iterator.next() as EventListener<T>
            val eventHandler = listener.eventHandler.get()
            if (eventHandler == null) {
                // eventHandler got garbage collected, prevent WeakListener memory leak
                iterator.remove()
                continue
            }
            val filter = listener.filter.get()
            if (filter == null || filter(event)) {
                eventHandler(event)
            }
        }
    }

    private fun <T: Event> receive(eventClass: KClass<T>, filter: ((T) -> Boolean)? = null, eventHandler: (T) -> Unit) {
        if (receivers[eventClass] == null) {
            receivers[eventClass] = mutableListOf()
        }
        receivers[eventClass]!!.add(EventListener(eventHandler, filter))
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
     * }
     * ```
     *
     * To have event listeners automatically garbage collected, we need to use [WeakReference]s in the event bus. For that to work, though, the class
     * that wants to receive events needs to hold references to its own event listeners. [EventReceiver] allows to do that while also providing the
     * interface to start receiving events.
     */
    class EventReceiver {

        val listeners: MutableList<Any> = mutableListOf()

        /**
         * The listeners will always be called on the main GDX render thread.
         *
         * @param T The event class holding the data of the event, or simply [Event].
         */
        fun <T: Event> receive(eventClass: KClass<T>, filter: ((T) -> Boolean)? = null, eventHandler: (T) -> Unit) {
            listeners.add(eventHandler)
            EventBus.receive(eventClass, filter, eventHandler)
        }
    }

}

private class EventListener<T>(
    eventHandler: (T) -> Unit,
    filter: ((T) -> Boolean)? = null
) {
    val eventHandler = WeakReference(eventHandler)
    val filter = WeakReference(filter)
}

package com.unciv.logic.event

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Test

class EventBusTest {
    open class Parent2 : Event
    open class Parent : Parent2()
    class Child : Parent()
    interface Update : Event
    class ChildUpdate : Parent(), Update

    @Test
    fun `sends an event to superclass and interface listeners`() {
        val events = EventBus.EventReceiver()
        val calls = mutableListOf<String>()
        try {
            events.receive(Parent::class) { calls.add("parent") }
            events.receive(Update::class) { calls.add("interface") }
            events.receive(ChildUpdate::class) { calls.add("child") }

            EventBus.send(ChildUpdate())

            Assert.assertEquals(listOf("parent", "interface", "child"), calls)
        } finally {
            events.stopReceiving()
        }
    }

    // All these tests are FLAKY and no good.
    // All work when debugging, but when running tests - sometimes yes sometimes no
    // EventBus was a bad idea to begin with though
//     @Test
    fun `should receive parent event once when receiving child event`() {
        val events = EventBus.EventReceiver()
        var callCount = 0
        events.receive(Parent::class) { ++callCount }

        EventBus.send(Child())

        assertThat(callCount, `is`(1))
    }

//     @Test
    fun `should receive parent event when child event with two levels of inheritance is sent`() {
        val events = EventBus.EventReceiver()
        var callCount = 0
        events.receive(Parent2::class) {
            ++callCount
        }

        EventBus.send(Child())

        Assert.assertEquals(callCount,1)
    }

//     @Test
    fun `should not receive parent event when listening to child event`() {
        val events = EventBus.EventReceiver()
        var callCount = 0
        events.receive(Child::class) { callCount++ }

        EventBus.send(Parent())

        assertThat(callCount, `is`(0))
    }

//     @Test
    fun `should stop listening to events when requested`() {
        val events = EventBus.EventReceiver()
        var callCount = 0
        events.receive(Child::class) { callCount++ }

        EventBus.send(Child())
        events.stopReceiving()
        EventBus.send(Child())

        assertThat(callCount, `is`(1))
    }
}

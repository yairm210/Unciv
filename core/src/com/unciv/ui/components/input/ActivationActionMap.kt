package com.unciv.ui.components.input

import com.unciv.models.UncivSound
import com.unciv.ui.audio.SoundPlayer
import com.unciv.utils.Concurrency
import java.util.EnumMap

typealias ActivationAction = () -> Unit

// The delegation inheritance is only done to reduce the signature and limit clients to *our* add functions
internal class ActivationActionMap : MutableMap<ActivationTypes, ActivationActionMap.ActivationActionList> by EnumMap(ActivationTypes::class.java) {
    // todo Old listener said "happens if there's a double (or more) click function but no single click" -
    //      means when we register a single-click but the listener *only* reports a double, the registered single-click action is invoked.

    class ActivationActionList(val sound: UncivSound) : MutableList<ActivationAction> by ArrayList()

    fun add(
        type: ActivationTypes,
        sound: UncivSound,
        noEquivalence: Boolean = false,
        action: ActivationAction
    ) {
        getOrPut(type) { ActivationActionList(sound) }.add(action)
        if (noEquivalence) return
        for (other in ActivationTypes.equivalentValues(type)) {
            getOrPut(other) { ActivationActionList(sound) }.add(action)
        }
    }

    fun clear(type: ActivationTypes) {
        if (containsKey(type)) remove(type)
    }

    fun clear(type: ActivationTypes, noEquivalence: Boolean) {
        clear(type)
        if (noEquivalence) return
        for (other in ActivationTypes.equivalentValues(type)) {
            clear(other)
        }
    }

    fun clearGestures() {
        for (type in ActivationTypes.gestures()) {
            clear(type)
        }
    }

    fun isNotEmpty() = any { it.value.isNotEmpty() }

    fun activate(type: ActivationTypes): Boolean {
        val actions = get(type) ?: return false
        if (actions.isEmpty()) return false
        if (actions.sound != UncivSound.Silent)
            Concurrency.runOnGLThread("Sound") { SoundPlayer.play(actions.sound) }
        // We can't know an activation handler won't redefine activations, so better iterate over a copy
        for (action in actions.toList())
            action.invoke()
        return true
    }
}

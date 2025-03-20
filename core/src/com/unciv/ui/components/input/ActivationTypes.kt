package com.unciv.ui.components.input

/** Formal encapsulation of the input interaction types */

enum class ActivationTypes(
    internal val tapCount: Int = 0,
    internal val button: Int = 0,
    internal val isGesture: Boolean = true,
    private val equivalentTo: ActivationTypes? = null
) {
    Keystroke(isGesture = false),

    Tap(1, equivalentTo = Keystroke),
    Doubletap(2),
    Tripletap(3),  // Just to clarify it ends here

    RightClick(1, 1),
    DoubleRightClick(1, 1),
    Longpress(equivalentTo = RightClick),
    ;

    /** Checks whether two [ActivationTypes] are declared equivalent, e.g. [RightClick] and [Longpress]. */
    internal fun isEquivalent(other: ActivationTypes) =
        this == other.equivalentTo || other == this.equivalentTo

    internal companion object {
        fun equivalentValues(type: ActivationTypes) = entries.asSequence()
            .filter { it.isEquivalent(type) }
        fun gestures() = entries.asSequence()
            .filter { it.isGesture }
    }
}

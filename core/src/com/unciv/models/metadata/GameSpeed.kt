package com.unciv.models.metadata

enum class GameSpeed {
    Quick,
    Standard,
    Epic,
    Marathon;

    fun getModifier(): Float {
        return when (this) {
            Quick -> 0.67f
            Standard -> 1f
            Epic -> 1.5f
            Marathon -> 3f
        }
    }

    /** Time victory turn limit */
    // https://gaming.stackexchange.com/a/9202
    fun getTurnLimit(): Int {
        return when (this) {
            Quick -> 330
            Standard -> 500
            Epic -> 750
            Marathon -> 1500
        }
    }
}
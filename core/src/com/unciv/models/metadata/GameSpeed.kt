package com.unciv.models.metadata

enum class GameSpeed{
    Quick,
    Standard,
    Epic,
    Marathon;

    fun getModifier(): Float {
        when(this) {
            Quick -> return 0.67f
            Standard -> return 1f
            Epic -> return 1.5f
            Marathon -> return 1.5f
        }
    }
}
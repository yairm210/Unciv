package com.unciv.models

data class UnitAction(
        var name: String, // TODO make it enum or sealed class
        var canAct: Boolean,
        var title: String = name,
        var isCurrentAction: Boolean = false,
        var uncivSound: UncivSound = UncivSound.Click,
        var action: (() -> Unit)? = null
)
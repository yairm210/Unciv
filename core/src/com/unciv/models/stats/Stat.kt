package com.unciv.models.stats

import com.unciv.models.UncivSound

enum class Stat (val sound: UncivSound) {
    Production(UncivSound.Click),
    Food(UncivSound.Click),
    Gold(UncivSound.Coin),
    Science(UncivSound.Chimes),
    Culture(UncivSound.Paper),
    Happiness(UncivSound.Click),
    Faith(UncivSound.Choir),
}
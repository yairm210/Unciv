package com.unciv.models

/**
 * Represents an Unciv Sound, either from a predefined set or custom with a specified filename.
 */
data class UncivSound(
    /** The base filename without extension. */
    val fileName: String
) {
    companion object {
        val Bombard = UncivSound("bombard")
        val Chimes = UncivSound("chimes")
        val Choir = UncivSound("choir")
        val Click = UncivSound("click")
        val Coin = UncivSound("coin")
        val Construction = UncivSound("construction")
        val Fire = UncivSound("fire")
        val Fortify = UncivSound("fortify")
        val Paper = UncivSound("paper")
        val Policy = UncivSound("policy")
        val Promote = UncivSound("promote")
        val Setup = UncivSound("setup")
        val Silent = UncivSound("")
        val Slider = UncivSound("slider")
        val Swap = UncivSound("swap")
        val Upgrade = UncivSound("upgrade")
        val Whoosh = UncivSound("whoosh")
    }
}

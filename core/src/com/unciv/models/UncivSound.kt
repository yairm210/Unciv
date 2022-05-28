package com.unciv.models

/**
 * Represents an Unciv Sound, either from a predefined set or custom with a specified filename.
 */
data class UncivSound(
    /** The base filename without extension. */
    val fileName: String
) {
    companion object {
        val Click = UncivSound("click")
        val Fortify = UncivSound("fortify")
        val Promote = UncivSound("promote")
        val Upgrade = UncivSound("upgrade")
        val Setup = UncivSound("setup")
        val Chimes = UncivSound("chimes")
        val Coin = UncivSound("coin")
        val Choir = UncivSound("choir")
        val Policy = UncivSound("policy")
        val Paper = UncivSound("paper")
        val Whoosh = UncivSound("whoosh")
        val Bombard = UncivSound("bombard")
        val Slider = UncivSound("slider")
        val Construction = UncivSound("construction")
        val Swap = UncivSound("swap")
        val Silent = UncivSound("")
        val Fire = UncivSound("fire")
    }
}

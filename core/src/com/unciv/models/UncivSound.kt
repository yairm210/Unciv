package com.unciv.models

/**
 * Represents an Unciv Sound, either from a predefined set or custom with a specified filename.
 */
data class UncivSound(
    /** The base filename without extension. */
    val fileName: String
) {
    /** For serialization */
    private constructor() : this("")

    companion object {
        val Bombard = UncivSound("bombard")
        val Chimes = UncivSound("chimes")
        val Choir = UncivSound("choir")
        val Click = UncivSound("click")
        val Coin = UncivSound("coin")
        val Construction = UncivSound("construction")
        val Fire = UncivSound("fire")
        val Fortify = UncivSound("fortify")
        val Notification1 = UncivSound("notification1")
        val Notification2 = UncivSound("notification2")
        val Paper = UncivSound("paper")
        val Policy = UncivSound("policy")
        val Promote = UncivSound("promote")
        val Setup = UncivSound("setup")
        val Silent = UncivSound("")
        val Slider = UncivSound("slider")
        val Swap = UncivSound("swap")
        val Upgrade = UncivSound("upgrade")
        val Whoosh = UncivSound("whoosh")
        val CityAncient = UncivSound("cityAncient")
        val CityClassical = UncivSound("cityClassical")
        val CityMedieval = UncivSound("cityMedieval")
        val CityRenaissance = UncivSound("cityRenaissance")
        val CityIndustrial = UncivSound("cityIndustrial")
        val CityModern = UncivSound("cityModern")
    }
}

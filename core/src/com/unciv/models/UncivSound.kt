package com.unciv.models

private enum class UncivSoundConstants (val value: String) {
    Click("click"),
    Fortify("fortify"),
    Promote("promote"),
    Upgrade("upgrade"),
    Setup("setup"),
    Chimes("chimes"),
    Coin("coin"),
    Choir("choir"),
    Fire("fire"),
    Policy("policy"),
    Paper("paper"),
    Whoosh("whoosh"),
    Bombard("bombard"),
    Slider("slider"),
    Construction("construction"),
    Swap("swap"),
    Silent(""),
    Custom("")
}

/**
 * Represents an Unciv Sound, either from a predefined set or custom with a specified filename.
 */
class UncivSound private constructor (
    private val type: UncivSoundConstants,
    filename: String? = null
) {
    /** The base filename without extension. */
    val value: String = filename ?: type.value

/*
    init {
        // Checking contract "use non-custom *w/o* filename OR custom *with* one
        // Removed due to private constructor
        if ((type == UncivSoundConstants.Custom) == filename.isNullOrEmpty()) {
            throw IllegalArgumentException("Invalid UncivSound constructor arguments")
        }
    }
*/

    companion object {
        val Click = UncivSound(UncivSoundConstants.Click)
        val Fortify = UncivSound(UncivSoundConstants.Fortify)
        val Promote = UncivSound(UncivSoundConstants.Promote)
        val Upgrade = UncivSound(UncivSoundConstants.Upgrade)
        val Setup = UncivSound(UncivSoundConstants.Setup)
        val Chimes = UncivSound(UncivSoundConstants.Chimes)
        val Coin = UncivSound(UncivSoundConstants.Coin)
        val Choir = UncivSound(UncivSoundConstants.Choir)
        val Policy = UncivSound(UncivSoundConstants.Policy)
        val Paper = UncivSound(UncivSoundConstants.Paper)
        val Whoosh = UncivSound(UncivSoundConstants.Whoosh)
        val Bombard = UncivSound(UncivSoundConstants.Bombard)
        val Slider = UncivSound(UncivSoundConstants.Slider)
        val Construction = UncivSound(UncivSoundConstants.Construction)
        val Swap = UncivSound(UncivSoundConstants.Swap)
        val Silent = UncivSound(UncivSoundConstants.Silent)
        val Fire = UncivSound(UncivSoundConstants.Fire)
        /** Creates an UncivSound instance for a custom sound.
         * @param filename The base filename without extension.
         */
        fun custom(filename: String) = UncivSound(UncivSoundConstants.Custom, filename)
    }

    // overrides ensure usability as hash key
    override fun hashCode(): Int {
        return type.hashCode() xor value.hashCode()
    }
    override fun equals(other: Any?): Boolean {
        if (other == null || other !is UncivSound) return false
        if (type != other.type) return false
        return type != UncivSoundConstants.Custom || value == other.value
    }

    override fun toString(): String = value
}

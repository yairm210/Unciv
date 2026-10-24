package com.unciv.models.skins

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants

class SkinConfig(initialCapacity: Int) {
    var baseColor: Color = Color(0x004085bf)
    var clearColor: Color = Color(0x000033ff)
    var defaultVariantTint: Color? = null
    var fallbackSkin: String? = Constants.defaultFallbackSkin
    var skinVariants: HashMap<String, SkinElement> = HashMap(initialCapacity)

    constructor() : this(16)  // = HashMap.DEFAULT_INITIAL_CAPACITY which is private

    /** Skin element, read from UI SKin json
     *
     *  **Immutable** */
    class SkinElement {
        val image: String? = null
        val tint: Color? = null
        val alpha: Float? = null
        val foregroundColor: Color? = null
        val iconColor: Color? = null
    }

    fun clone() = SkinConfig(skinVariants.size).also { it.updateConfig(this) }

    /** 'Merges' [other] into **`this`**
     *
     *  [baseColor], [clearColor], and [defaultVariantTint] are overwritten with clones from [other].
     *  [fallbackSkin] is overwritten with [other]'s value.
     *  [skinVariants] with the same key are copied and overwritten, new [skinVariants] are added. */
    fun updateConfig(other: SkinConfig) {
        baseColor = other.baseColor.cpy()
        clearColor = other.clearColor.cpy()
        defaultVariantTint = other.defaultVariantTint?.cpy()
        fallbackSkin = other.fallbackSkin
        skinVariants.putAll(other.skinVariants)
    }
}

package com.unciv.models.skins

import com.badlogic.gdx.graphics.Color

class SkinElement {
    var image: String? = null
    var tint: Color? = null
    var alpha: Float? = null

    fun clone(): SkinElement {
        val toReturn = SkinElement()
        toReturn.image = image
        toReturn.tint = tint?.cpy()
        toReturn.alpha = alpha
        return toReturn
    }
}

class SkinConfig {
    var baseColor: Color = Color(0x004085bf)
    var clearColor: Color = Color(0x000033ff)
    var skinVariants: HashMap<String, SkinElement> = HashMap()

    fun clone(): SkinConfig {
        val toReturn = SkinConfig()
        toReturn.baseColor = baseColor.cpy()
        toReturn.clearColor = clearColor.cpy()
        toReturn.skinVariants.putAll(skinVariants.map { Pair(it.key, it.value.clone()) })
        return toReturn
    }

    fun updateConfig(other: SkinConfig) {
        baseColor = other.baseColor.cpy()
        clearColor = other.clearColor.cpy()
        for ((variantName, element) in other.skinVariants){
            skinVariants[variantName] = element.clone()
        }
    }
}

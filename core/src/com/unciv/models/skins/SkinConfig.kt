package com.unciv.models.skins

import com.badlogic.gdx.graphics.Color

class SkinElement {
    var image: String? = null
    var tint: Color? = null
}

class SkinConfig {
    var baseColor: Color = Color(0x004085bf)
    var skinVariants: HashMap<String, SkinElement> = HashMap()
}

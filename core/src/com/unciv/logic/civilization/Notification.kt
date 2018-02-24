package com.unciv.logic.civilization

import com.badlogic.gdx.math.Vector2

class Notification {
    @JvmField var text: String = ""
    @JvmField var location: Vector2? = null

    internal constructor() {}

    constructor(text: String, location: Vector2?) {
        this.text = text
        this.location = location
    }
}

package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2

class Notification {
    var text: String = ""
    var location: Vector2? = null
    var color:Color = Color.BLACK

    internal constructor()

    constructor(text: String, location: Vector2?,color: Color) {
        this.text = text
        this.location = location
        this.color=color
    }
}

package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2

class Notification {
    var text: String = ""
    var locations: List<Vector2> = listOf()
    var color: Color = Color.BLACK

    internal constructor() // Needed for json deserialization

    constructor(text: String, location: List<Vector2> = listOf(), color: Color) {
        this.text = text
        this.locations = location
        this.color = color
    }
}

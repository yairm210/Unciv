package com.unciv.logic.civilization

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2

class Notification {
    var text: String = ""
    var locations: ArrayList<Vector2> = ArrayList()
    var color: Color = Color.BLACK
    var openTech: Boolean = false

    internal constructor() // Needed for json deserialization

    constructor(text: String, locations: List<Vector2> = ArrayList(), color: Color, openTech: Boolean = false) {
        this.text = text
        this.locations = ArrayList(locations)
        this.color = color
        this.openTech = openTech
    }

}

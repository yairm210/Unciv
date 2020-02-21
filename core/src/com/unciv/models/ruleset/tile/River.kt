package com.unciv.models.ruleset.tile

import com.badlogic.gdx.math.Vector2

data class River(var course: HashSet<Pair<Vector2, Vector2>> = HashSet()) : Cloneable {
    public override fun clone(): River {
        val newCourse = course.map { tilePair -> Pair(Vector2(tilePair.first), Vector2(tilePair.second)) }
        return River(HashSet(newCourse))
    }
}
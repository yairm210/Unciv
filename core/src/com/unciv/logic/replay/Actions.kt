package com.unciv.logic.replay

import com.badlogic.gdx.math.Vector2
import com.unciv.models.ruleset.unit.UnitType

class Action(val type: Int, val data: Any)

class MovementData(val unitType: UnitType, val origin: Vector2, val destination: Vector2, val path: List<Vector2>)
class ConstructionData(val constructionName: String, val position: Vector2)

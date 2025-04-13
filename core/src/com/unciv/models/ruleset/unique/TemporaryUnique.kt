package com.unciv.models.ruleset.unique

import com.unciv.logic.IsPartOfGameInfoSerialization

class TemporaryUnique() : IsPartOfGameInfoSerialization {

    constructor(uniqueObject: Unique, turns: Int) : this() {
        val turnsText = uniqueObject.getModifiers(UniqueType.ConditionalTimedUnique).first().text
        unique = uniqueObject.text.replaceFirst("<$turnsText>", "").trim()
        sourceObjectType = uniqueObject.sourceObjectType
        sourceObjectName = uniqueObject.sourceObjectName
        turnsLeft = turns
    }

    var unique: String = ""

    private var sourceObjectType: UniqueTarget? = null
    private var sourceObjectName: String? = null

    @delegate:Transient
    val uniqueObject: Unique by lazy { Unique(unique, sourceObjectType, sourceObjectName) }

    var turnsLeft: Int = 0
}


fun ArrayList<TemporaryUnique>.endTurn() {
    for (unique in this) {
        if (unique.turnsLeft >= 0)
            unique.turnsLeft -= 1
    }
    removeAll { it.turnsLeft == 0 }
}

fun ArrayList<TemporaryUnique>.getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals): Sequence<Unique> {
    return this.asSequence()
        .map { it.uniqueObject }
        .filter { it.type == uniqueType && it.conditionalsApply(stateForConditionals) }
        .flatMap { it.getMultiplied(stateForConditionals) }
}

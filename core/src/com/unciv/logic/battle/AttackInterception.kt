package com.unciv.logic.battle

import com.unciv.logic.IsPartOfGameInfoSerialization
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly

/**
 * One interception attempt against the containing [AttackEvent.attacker].
 * The interceptor is distinct from the mission's intended targets. Its participant totals and
 * outcome describe the entire mission, including any subsequent nuclear blast. The directional
 * damage fields below describe only this interception, so that attribution remains unambiguous.
 */
class AttackInterception() : IsPartOfGameInfoSerialization {
    var interceptor: AttackParticipant? = null
    /** False means the interceptor consumed an attack but missed; a ground air sweep engages for 0 HP. */
    var intercepted = false
    var damageToAttacker = 0
    var damageToInterceptor = 0

    constructor(interceptor: MapUnitCombatant) : this() {
        this.interceptor = AttackParticipant(interceptor)
    }

    @Readonly
    fun clone(): AttackInterception {
        @LocalState val result = AttackInterception()
        result.interceptor = interceptor?.clone()
        result.intercepted = intercepted
        result.damageToAttacker = damageToAttacker
        result.damageToInterceptor = damageToInterceptor
        return result
    }
}

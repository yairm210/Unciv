package com.unciv.models.ruleset.unique

import java.util.EnumSet

enum class UniqueFlag {
    HiddenToUsers,
    NoConditionals,
    AcceptsSpeedModifier,
    AcceptsGameProgressModifier
    ;
    companion object {
        val setOfHiddenToUsers: EnumSet<UniqueFlag> = EnumSet.of(HiddenToUsers)
        val setOfNoConditionals: EnumSet<UniqueFlag> = EnumSet.of(NoConditionals)
        val setOfHiddenNoConditionals: EnumSet<UniqueFlag> = EnumSet.of(HiddenToUsers, NoConditionals)
    }
}

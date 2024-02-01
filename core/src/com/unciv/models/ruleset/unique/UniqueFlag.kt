package com.unciv.models.ruleset.unique

import java.util.EnumSet

enum class UniqueFlag {
    HiddenToUsers,
    NoConditionals,
    ;
    companion object {
        val none: EnumSet<UniqueFlag> = EnumSet.noneOf(UniqueFlag::class.java)
        val setOfHiddenToUsers: EnumSet<UniqueFlag> = EnumSet.of(HiddenToUsers)
        val setOfNoConditionals: EnumSet<UniqueFlag> = EnumSet.of(NoConditionals)
    }
}

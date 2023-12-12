package com.unciv.models.ruleset.unique

enum class UniqueFlag {
    HiddenToUsers,
    ;
    companion object {
        val setOfHiddenToUsers = listOf(HiddenToUsers)
    }
}

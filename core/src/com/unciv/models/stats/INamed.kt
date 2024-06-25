package com.unciv.models.stats

interface INamed {
    // This is a var because unit tests set it (see `createRulesetObject` in TestGame.kt)
    // As of 2023-08-08 no core code modifies a name!
    // The main source of names are RuleSet json files, and Json deserialization can set a val just fine
    var name: String
}

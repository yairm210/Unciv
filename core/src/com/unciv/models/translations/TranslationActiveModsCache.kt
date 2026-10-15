package com.unciv.models.translations

import com.unciv.UncivGame
import com.unciv.utils.hashOf

object TranslationActiveModsCache {
    private var cachedHash = Int.MIN_VALUE

    var activeMods: HashSet<String> = hashSetOf()
        get() {
            val hash = getCurrentHash()
            if (hash != cachedHash) {
                cachedHash = hash
                field = getCurrentSet()
            }
            return field
        }
        private set

    private fun getCurrentHash(): Int {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null) {
            hashOf(gameInfo.gameParameters.mods.hashCode(), gameInfo.gameParameters.baseRuleset.hashCode())
        } else {
            UncivGame.Current.translations.translationActiveMods.hashCode()
        }
    }

    private fun getCurrentSet(): LinkedHashSet<String> {
        val gameInfo = UncivGame.Current.gameInfo
        return if (gameInfo != null) {
            val par = gameInfo.gameParameters
            // This is equivalent to (par.mods + par.baseRuleset) without the cast down to `Set`
            LinkedHashSet<String>(par.mods.size + 1).apply {
                addAll(par.mods)
                add(par.baseRuleset)
            }
        } else {
            UncivGame.Current.translations.translationActiveMods
        }
    }
}

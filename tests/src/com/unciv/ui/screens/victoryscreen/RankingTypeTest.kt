package com.unciv.ui.screens.victoryscreen

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class RankingTypeTests {

    @Test
    fun checkIdForSerializationUniqueness() {
        val uniqueIds = HashSet<String>()
        for (rankingType in RankingType.values()) {
            val id = rankingType.idForSerialization
            Assert.assertTrue(
                "Id $id for RankingType $rankingType is not unique",
                uniqueIds.add(id)
            )
        }
    }
}

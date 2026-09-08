package com.unciv.ui.screens.victoryscreen

import com.unciv.testing.BaseTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class RankingTypeTests {

    @Test
    fun checkIdForSerializationUniqueness() {
        val uniqueIds = HashSet<Char>()
        for (rankingType in RankingType.entries) {
            val id = rankingType.idForSerialization
            Assert.assertTrue(
                "Id $id for RankingType $rankingType is not unique",
                uniqueIds.add(id)
            )
        }
    }
}

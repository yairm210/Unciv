package com.unciv.models.tilesets

import com.unciv.testing.BaseTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class TileSetConfigTests {

    @Test
    fun cloneIsIndependentOfTheOriginal() {
        val original = TileSetConfig().apply {
            tileScales["Forest"] = 0.9f
            ruleVariants["Grassland"] = arrayOf("a", "b")
        }

        val copy = original.clone()
        copy.tileScales["Forest"] = 2f
        copy.tileScales["Hill"] = 3f
        copy.ruleVariants["Grassland"]!![0] = "changed"

        Assert.assertEquals(mapOf("Forest" to 0.9f), original.tileScales)
        Assert.assertEquals(listOf("a", "b"), original.ruleVariants["Grassland"]!!.toList())
    }

    @Test
    fun cloneCopiesTheContent() {
        val original = TileSetConfig().apply {
            tileScale = 1.5f
            fallbackTileSet = "Other"
            tileScales["Forest"] = 0.9f
        }

        val copy = original.clone()

        Assert.assertEquals(1.5f, copy.tileScale)
        Assert.assertEquals("Other", copy.fallbackTileSet)
        Assert.assertEquals(mapOf("Forest" to 0.9f), copy.tileScales)
    }
}

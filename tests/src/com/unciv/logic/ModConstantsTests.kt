package com.unciv.logic

import com.unciv.models.ModConstants
import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ulp

/**
 *  These exist because the ModConstants class makes its extensibility easier by using reflection to include new members automatically.
 *
 *  There was a bug in an earlier version where includion of the companion object created a recursion.
 *  This prevents such occurrences and guards against side effects from library/jvm changes.
 */
@RunWith(GdxTestRunner::class)
class ModConstantsTests {
    @Test
    fun `Test ModConstants hashCode`() {
        val hash1 = ModConstants.defaults.hashCode()
        val hash2 = ModConstants().hashCode()
        Assert.assertEquals(hash1, hash2)
    }

    @Test
    fun `Test ModConstants equals`() {
        val instance1 = ModConstants().apply { maxXPfromBarbarians = 99 }
        val instance2 = ModConstants().apply { cityStrengthBase = 6.0 }
        Assert.assertNotEquals(instance1, instance2)
        instance1.cityStrengthBase = instance2.cityStrengthBase
        instance2.maxXPfromBarbarians = instance1.maxXPfromBarbarians
        Assert.assertEquals(instance1, instance2)
    }

    @Test
    fun `Test ModConstants toString`() {
        val instance1 = ModConstants().apply {
            maxXPfromBarbarians = 99
            cityStrengthBase = 6.0
        }
        Assert.assertEquals(instance1.toString(), "{maxXPfromBarbarians:99,cityStrengthBase:6.0}")
        Assert.assertEquals(ModConstants.defaults.toString(), "defaults")
    }

    @Test
    fun `Test ModConstants merge`() {
        val instance1 = ModConstants().apply { maxXPfromBarbarians = 99 }
        val instance2 = ModConstants().apply { cityStrengthBase = 6.0 }
        instance1.merge(instance2)
        Assert.assertEquals(instance1.cityStrengthBase, 6.0, 1.0.ulp)
    }
}

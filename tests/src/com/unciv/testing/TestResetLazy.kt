package com.unciv.testing

import com.unciv.utils.resetLazy
import com.unciv.utils.resettableLazy
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.ClassCastException


@RunWith(GdxTestRunner::class)
class TestResetLazy {
    private val data = mutableListOf<Int>()

    private val test1 by resettableLazy {
        data.hashCode()
    }

    private val test2 by lazy {
        data.hashCode()
    }

    @Before
    fun resetTest() {
        ::test1.resetLazy()
        data.clear()
        data += listOf(1, 7, 19, 42)
    }

    @Test
    fun `resettableLazy should be functionally equivalent to lazy`() {
        Assert.assertEquals(data.hashCode(), test1)
        Assert.assertEquals(data.hashCode(), test2)
    }

    @Test
    fun `resettableLazy is able to re-evaluate`() {
        Assert.assertEquals(data.hashCode(), test1)
        Assert.assertEquals(data.hashCode(), test2)
        data += 666
        Assert.assertNotEquals(data.hashCode(), test1)
        ::test1.resetLazy()
        Assert.assertEquals(data.hashCode(), test1)
        Assert.assertNotEquals(data.hashCode(), test2)
    }
    @Test
    fun `resetLazy cannot be called on a normal Lazy`() {
        val threw = try {
            ::test2.resetLazy()
            false
        } catch (ex: ClassCastException) {
            true
        }
        Assert.assertTrue("resetLazy should throw when called on a normal Lazy", threw)
    }
}

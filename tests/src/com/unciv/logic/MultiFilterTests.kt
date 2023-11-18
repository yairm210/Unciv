package com.unciv.logic

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class MultiFilterTests {
    @Test
    fun testSplitTerms() {
        Assert.assertTrue(MultiFilter.multiFilter("{A} {B}", { it=="A" || it=="B"}))
        Assert.assertFalse(MultiFilter.multiFilter("{A} {B}", { it=="A"}))
        Assert.assertFalse(MultiFilter.multiFilter("{A} {B}", { it=="B"}))
    }

    @Test
    fun testNotTerm() {
        Assert.assertTrue(MultiFilter.multiFilter("non-[B]", { it=="A"}))
        Assert.assertFalse(MultiFilter.multiFilter("non-[A]", { it=="A"}))
    }

    @Test
    fun testSplitNotTerm() {
        Assert.assertTrue(MultiFilter.multiFilter("{non-[A]} {non-[B]}", { it=="C"}))
        Assert.assertFalse(MultiFilter.multiFilter("{non-[A]} {non-[B]}", { it=="A"}))
    }
}

package com.unciv.utils

import com.unciv.testing.GdxTestRunner
import org.junit.Assert
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.streams.toList

@RunWith(GdxTestRunner::class)
class LongPriorityQueueTest {
    
    @Test
    fun addAllThenPopAllIsOrdered() {
        // enough items to hit a depth of 4
        val expectedOutputOrder = longArrayOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
        val currentInputOrder = longArrayOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
        val queue = LongPriorityQueue(currentInputOrder.size)
        // Test all 40,320 permutations. On my machine, takes ~21ms.
        currentInputOrder.forEachPermutation {
            queue.clear()
            queue.addAll(currentInputOrder)
            val initialString = currentInputOrder.joinToString(",")
            assertArrayEquals(
                "After queuing and popping $initialString",
                expectedOutputOrder,
                queue.popAll()
            )    
        }
    }

    @Test
    fun addOneAtATimeThenPopAllIsOrdered() {
        // enough items to hit a depth of 4
        val expectedOutputOrder = longArrayOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
        val currentInputOrder = longArrayOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L)
        val queue = LongPriorityQueue(currentInputOrder.size)
        // Test all 40,320 permutations. On my machine, takes ~59ms.
        currentInputOrder.forEachPermutation {
            queue.clear()
            currentInputOrder.forEach { queue.add(it) }
            val initialString = currentInputOrder.joinToString(",")
            assertArrayEquals(
                "After queuing and popping $initialString",
                expectedOutputOrder,
                queue.popAll()
            )
        }
    }

    @Test
    fun iteratorIteratesAllElements() {
        val queue = LongPriorityQueue(16)
        queue.addAll(longArrayOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L))

        val r = queue.iterator().asSequence().toList()

        Assert.assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L), r)
    }
    
    @Test
    fun streamIteratesAllElements() {
        val queue = LongPriorityQueue(16)
        queue.addAll(longArrayOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L))
        
        val r = queue.stream().toList()

        Assert.assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L), r)
    }
    
    companion object {
        private fun LongPriorityQueue.popAll(): LongArray {
            val c = size()
            val result = LongArray(c)
            for (i in 0..<c)
                result[i] = this.poll()
            return result
        }
        
        private inline fun LongArray.findFirstPair(condition: (Long,Long)->Boolean): Int {
            for (i in 0 ..<size-1) {
                if (condition(get(i), get(i+1)))
                    return i
            }
            return -1
        }

        private fun factorial(n: Int): Int {
            if (n <2) return 1
            var result = 1
            for (i in 2..n)
                result *= i
            return result
        }
        
        private fun LongArray.swap(i: Int, j: Int) {
            val t = get(i)
            set(i, get(j))
            set(j, t)
        }

        // Non-recursive Heap's Algorithm for iterating permutations very quickly
        // https://en.wikipedia.org/wiki/Heap%27s_algorithm
        private inline fun LongArray.forEachPermutation(op: (LongArray)->Unit) {
            try {
                // c is an encoding of the stack state.
                // c[k] encodes the for-loop counter for when permutations(k + 1, A) is called
                val c = IntArray(size)
                op(this)
                var i = 1
                while (i<size) {
                    if (c[i] < i) {
                        if (i and 1 == 0)
                            swap(0, i)
                        else
                            swap(c[i], i)
                        op(this)
                        // Swap has occurred ending the while-loop. Simulate the increment of the while-loop counter
                        c[i] += 1
                        // Simulate recursive call reaching the base case by bringing the pointer to the base case analog in the array
                        i = 1
                    } else {
                        // Calling permutations(i+1, A) has ended as the while-loop terminated. Reset the state and simulate popping the stack by incrementing the pointer.
                        c[i] = 0
                        i++
                    }
                }
            } catch (ex: Throwable) {
                // if one iteration fails, note which iteration it was
                val initialString = joinToString(",")
                ex.addSuppressed(RuntimeException("during iteration $this, while queing and sorting $initialString"))
                throw ex
            }
        }
        
    }
}

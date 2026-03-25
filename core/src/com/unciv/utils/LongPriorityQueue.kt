package com.unciv.utils

import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import java.util.Objects
import java.util.Spliterator.CONCURRENT
import java.util.Spliterator.IMMUTABLE
import java.util.Spliterator.NONNULL
import java.util.Spliterator.SIZED
import java.util.Spliterator.SUBSIZED
import java.util.function.LongConsumer
import java.util.stream.LongStream
import java.util.stream.StreamSupport

// A PriorityQueue<Long>, except that it minimizes memory allocations
// 
// This does NOT extend any interfaces to avoid boxing Long to Long Objects.
@InternalState
class LongPriorityQueue(
    initialCapacity: Int = 100,
    val comparator : Comparator = DefaultComparator
) {
    private var queue: LongArray = LongArray(initialCapacity)
    private var size : Int = 0
    private var mutCounter: Int = 0
    
    constructor(c: Collection<Long>, comparator : Comparator = DefaultComparator) 
        : this(c.size, comparator) {
        addAll(c)
    }
    constructor(c: LongArray, comparator : Comparator = DefaultComparator) 
        : this(c.size, comparator) {
        addAll(c)
    }

    fun clear() {
        size = 0
        ++mutCounter
    }
    
    @Readonly
    fun size() : Int = size

    @Readonly
    fun isEmpty() : Boolean = size == 0
    
    @Readonly
    fun isNotEmpty() = size != 0

    @Readonly
    fun element() : Long {
        if (size == 0) throw NoSuchElementException("Priority queue is empty.")
        return queue[0]
    }
    
    @Readonly
    operator fun get(index: Int) : Long {
        if (index !in 0..<size) throw IndexOutOfBoundsException("Index $index out of bounds for priority queue of size $size.")
        return queue[index]
    }

    @Readonly
    fun contains(value: Long): Boolean {
        for (i in 0..<size) {
            if (queue[i] == value) return true
        }
        return false
    }

    @Readonly
    fun containsAll(c: Collection<Long>): Boolean {
        for (item in c) {
            if (!contains(item)) return false
        }
        return true
    }

    @Readonly
    fun containsAll(c: LongArray): Boolean {
        for (item in c) {
            if (!contains(item)) return false
        }
        return true
    }

    operator fun plus(value: Long) = add(value)
    
    fun add(value: Long) {
        if (size == queue.size) {
            resizeUp()
        }
        queue[size] = value
        ++size
        bubbleUp(size-1)
        ++mutCounter
    }
    
    fun offer(value: Long): Boolean {
        add(value)
        return true
    }
    
    fun addAll(c: Collection<Long>) : Boolean {
        ++mutCounter
        val oldSize = size
        if (size + c.size > queue.size) {
            resize(size + c.size)
        }
        c.forEach { queue[size++] = it }
        // looks inefficient, but 50% do not bubble at all, 25% bubble once, etc.
        for (i in size-1 downTo  oldSize-1) {
            bubbleUp(i)
        }
        ++mutCounter
        return false
    }

    fun addAll(c: LongArray) : Boolean {
        ++mutCounter
        val oldSize = size
        if (size + c.size > queue.size) {
            resize(size + c.size)
        }
        c.copyInto(queue, size)
        size = size + c.size
        // looks inefficient, but 50% do not bubble at all, 25% bubble once, etc.
        for (i in oldSize until size) {
            bubbleUp(i)
        }
        ++mutCounter
        return false
    }

    @Readonly
    fun peek() : Long? = if (size > 0) queue[0] else null
    
    fun poll() : Long {
        if (size == 0) throw NoSuchElementException("Priority queue is empty.")
        val top = queue[0]
        fillHole(0)
        ++mutCounter
        return top
    }
    
    operator fun minus(value: Long) = remove(value)
    
    fun remove(value: Long) : Boolean {
        if (size == 0) return false
        fillHole(0)
        ++mutCounter
        return true
    }
    
    fun removeAll(c: Collection<Long>) : Boolean {
        ++mutCounter
        if (c.size > size) {
            return removeIf { c.contains(it) }
        }
        for (item in c) {
            if (!remove(item)) return false
        }
        ++mutCounter
        return true
    }

    fun removeAll(c: LongArray) : Boolean {
        ++mutCounter
        if (c.size > size) {
            return removeIf { c.contains(it) }
        }
        for (item in c) {
            if (!remove(item)) return false
        }
        ++mutCounter
        return true
    }

    fun removeIf(predicate: (Long)->Boolean) : Boolean {
        ++mutCounter
        var i = 0
        while (i < size) {
            if (predicate(queue[i])) {
                fillHole(i)
                // do not increment i, as we have a new item at index i
            } else {
                ++i
            }
        }
        ++mutCounter
        return true
    }

    fun retainAll(c: Collection<Long>) : Boolean = removeIf { !c.contains(it) }

    fun retainAll(c: LongArray) : Boolean = removeIf { !c.contains(it) }

    private fun resizeUp() = resize(queue.size * 3 / 2 + 1)

    private fun resize(newSize: Int) {
        val newQueue = LongArray(newSize)
        queue.copyInto(newQueue)
        queue = newQueue
    }

    private fun bubbleUp(initialIndex: Int) {
        var index = initialIndex
        while (index > 0) {
            val parentIndex = (index - 1) / 2
            if (comparator(queue[parentIndex], queue[index]) < 0) {
                break
            }
            swap(parentIndex, index)
            index = parentIndex
        }
    }

    private fun fillHole(startIndex: Int) {
        var index = startIndex
        // bubble least child recursively to fill the hole
        while (true) {
            val child1 = index * 2 + 1
            val child2 = index * 2 + 2
            if (child1 >= size) {
                break
            }
            if (child2 >= size) {
                queue[index] = queue[child1]
                break
            }
            if (comparator(queue[child1], queue[child2]) <= 0) {
                queue[index] = queue[child1]
                index = child1
            } else {
                queue[index] = queue[child2]
                index = child2
            }
        }
        // if index is the last element, just reduce size
        if (index == size -1) {
            --size
            return
        }
        // otherwise, we still have a hole in the last layer, not at the end
        // move the last item to the hole, and bubble it up if needed.
        // This sounds inefficient, but in practice, this rarely bubbles even once.
        queue[index] = queue[size -1]
        queue[size-1] = Long.MIN_VALUE // unnecessary, but it makes "unused" elemnts more visible
        --size
        bubbleUp(index)
    }

    private fun swap(i: Int, j: Int) {
        val temp = queue[i]
        queue[i] = queue[j]
        queue[j] = temp
    }

    @Readonly
    fun toArray(array: LongArray) : LongArray {
        if (array.size >= size) {
            queue.copyInto(array, 0, 0, size)
            return array
        }
        val newArray = LongArray(size)
        queue.copyInto(newArray, 0, 0, size)
        return newArray
    }

    @Readonly
    fun clone() : LongPriorityQueue {
        val c = LongPriorityQueue(size, comparator)
        queue.copyInto(c.queue, 0, size)
        c.size = size
        return c
    }

    @Readonly
    override fun equals(other: Any?) : Boolean {
        if (other !is LongPriorityQueue) return false
        if (comparator != other.comparator) return false
        if (size != other.size) return false
        for (i in 0..<size) {
            if (queue[i] != other.queue[i]) return false
        }
        return true
    }

    @Readonly
    override fun hashCode() : Int {
        var hash = Objects.hash(comparator)
        for (i in 0..<size) {
            hash = Objects.hash(hash, queue[i])
        }
        return hash
    }

    @Readonly
    override fun toString() : String {
        return "LongPriorityQueue[size=$size top=${if(size>0)queue[0] else "null"}]"
    }

    @Readonly
    fun forEach(action: (Long) -> Unit) {
        for (i in 0 until size) {
            action(queue[i])
        }
    }

    @Readonly
    fun iterator() : Iterator = Iterator(mutCounter)

    @InternalState
   inner class Iterator(@Cache private var mutSnapshot: Int) : MutableIterator<Long> {
        @Cache private var index: Int = -1
        @Cache private var canRemove: Boolean = true
        
        override fun hasNext(): Boolean {
            if (mutSnapshot != mutCounter) {
                throw ConcurrentModificationException("Priority queue modified during iteration.")
            }
            return index < size - 1
        }

        override fun next(): Long {
            if (mutSnapshot != mutCounter) {
                throw ConcurrentModificationException("Priority queue modified during iteration.")
            }
            if (index >= size) {
                throw NoSuchElementException("No more elements in priority queue.")
            }
            ++index
            canRemove = true
            return queue[index]
        }

        override fun remove() {
            if (mutSnapshot != mutCounter) {
                throw ConcurrentModificationException("Priority queue modified during iteration.")
            }
            if (!canRemove) {
                throw IllegalStateException("Cannot remove element before calling next().")
            }
            fillHole(index)
            ++mutCounter
            ++mutSnapshot
            canRemove = false
        }
    }


    @Readonly
    fun spliterator() :Spliterator = Spliterator(-1, size-1, mutCounter)

    @InternalState
    inner class Spliterator(var index: Int, var endIndex: Int, val mutSnapshot: Int) : java.util.Spliterator.OfLong {
       
        override fun tryAdvance(action: LongConsumer?): Boolean {
            if (mutSnapshot != mutCounter) {
                throw ConcurrentModificationException("Priority queue modified during iteration.")
            }
            if (index >= endIndex) {
                return false
            }
            ++index
            action?.accept(queue[index])
            return true
        }

        override fun trySplit(): Spliterator? {
            if (mutSnapshot != mutCounter) {
                throw ConcurrentModificationException("Priority queue modified during iteration.")
            }
            if (endIndex - index < 2) {
                return null
            }
            val mid = (index + endIndex) / 2
            val split = Spliterator(mid, endIndex, mutSnapshot)
            endIndex = mid
            return split
        }

        override fun estimateSize(): Long {
            return endIndex - index.toLong()
        }

        override fun characteristics(): Int {
            return SIZED.or(NONNULL).or(IMMUTABLE).or(CONCURRENT).or(SUBSIZED)
        }
    }

    @Readonly
    fun stream() : LongStream = StreamSupport.longStream(spliterator(), false)

    @Readonly
    fun parallelStream() : LongStream = stream().parallel()
    
    companion object {
        interface Comparator {
            operator fun invoke(a: Long, b: Long): Int
        }
        object DefaultComparator : Comparator {
            override operator fun invoke(a: Long, b: Long): Int = a.compareTo(b)
        }
    }
}

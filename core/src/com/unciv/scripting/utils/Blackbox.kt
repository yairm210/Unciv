package com.unciv.scripting.utils


interface Blackbox {

    fun start() {  }

    /**
     * Try to shut down this black box.
     *
     * Because there might be normal situations where a "black box" isn't viable to cleanly shut down, I'm thinking that letting exceptions be returned will let those situations be distinguished from actual errors.
     *
     * E.G.: Making an invalid API call to a requests library should still throw the Exception as usual, but making the right call only to find out that the network's down or a process is frozen would be a more "normal" and uncontrollable situation, so in that case the exception should be a return value instead of being thrown.
     *
     * @return null on success, or an Exception() on failure.
     */
    fun stop(): Exception? = null

    val isAlive: Boolean
        get() = false

    /**
     * Return approximate number of items ready to be read. Should try to always return 0 correctly, but may return 1 if a greater number of items are available.
     */
    val readyForRead: Int
        get() = 0

    val readyForWrite: Boolean
        get() = false

    fun read(block: Boolean = true): String = ""// Return a single string if either blocking or ready to read, or throw an IllegalStateException() otherwise.

    /**
     * Read out all lines up to a limit if given a limit greater than zero.
     *
     * @param block Whether to wait until at least one line is available before returning.
     * @return Empty list if no lines are available and blocking is disabled. List of at least one string if blocking is allowed.
     */
    fun readAll(block: Boolean = true, limit: Int = 0): List<String> {
        val lines = ArrayList<String>()
        var i = 0
        if (block) {
            lines.add(read(block=true))
            i += 1
        }
        while (readyForRead > 0 && (limit == 0 || i < limit)) {
            lines.add(read(block=true))
            i += 1
        }
        return lines
    }

    fun write(string: String) {  }

}


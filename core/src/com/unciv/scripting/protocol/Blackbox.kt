package com.unciv.scripting.protocol


/**
 * Unified interface for anything that receives and responds to input without any access to or relevance for its internal states.
 *
 * Should be able to wrap STDIN/STDOUT, pipes, JNI, NDK, network sockets, external processes, embedded code, etc, and make them all interchangeable.
 */
interface Blackbox {

    fun start() {  }

    /**
     * Try to shut down this black box.
     *
     * Because there might be normal situations where a "black box" isn't viable to cleanly shut down, I'm thinking that letting exceptions be returned instead of thrown will let those situations be distinguished from actual errors.
     *
     * E.G.: Making an invalid API call to a requests library should still throw the Exception as usual, but making the right call only to find out that the network's down or a process is frozen would be a more "normal" and uncontrollable situation, so in that case the exception should be a return value instead of being thrown.
     *
     * This way, the entire call stack between where a predictable error happens and where it's eventually handled doesn't have to be wrapped in an overly broad try{} block.
     *
     * @return null on success, or an Exception() on failure.
     */
    fun stop(): Exception? = null

    /**
     * Whether this black box is "running", and able to receive and respond to input.
     */
    val isAlive: Boolean
        get() = false

    /**
     * Approximate number of items ready to be read. Should try to always return 0 correctly, but may return 1 if a greater number of items are available.
     */
    val readyForRead: Int
        get() = 0

    /**
     * Whether this black box is ready to be written to.
     */
    val readyForWrite: Boolean
        get() = false

    /**
     * @param block Whether to wait for the next available output, or throw an exception if none are available.
     * @throws IllegalStateException if blocking is disabled and black box is not ready for read.
     * @return String output from black box.
     */
    fun read(block: Boolean = true): String = ""

    /**
     * Read out all lines up to a limit if given a limit greater than zero.
     *
     * @param block Whether to wait until at least one line is available before returning.
     * @return Empty list if no lines are available and blocking is disabled. List of at least one string if blocking is allowed.
     */
    fun readAll(block: Boolean = true, limit: Int = 0): List<String> {
        //Should probably be Final.
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

    /**
     * Write a single string to the black box.
     *
     * @param string String to be written.
     */
    fun write(string: String) {  }

}


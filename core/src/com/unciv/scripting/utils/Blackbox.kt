package com.unciv.scripting.utils


interface Blackbox {

    fun start() {  }
    
    fun stop(): Exception? = null // Return null on success, or return an Exception() on failure. Because there might be normal situations where a "black box" isn't viable to cleanly shut down, I'm thinking that letting exceptions be returned will let those situations be distinguished from actual errors. E.G.: Making an invalid API call to a requests library should still throw the Exception as usual, but making the right call only to find out that the network's down or a process is frozen would be a more "normal" and uncontrollable situation, so in that case the exception should be a return value instead of thrown.
    
    val isAlive: Boolean
        get() = false

    val readyForRead: Int // Return approximate number of items ready to be read. Should try to always return 0 correctly, but may return 1 if a greater number of items are available.
        get() = 0
        
    val readyForWrite: Boolean
        get() = false
        
    fun read(block: Boolean = true): String = ""// Return a single string if either blocking or ready to read, or throw an IllegalStateException() otherwise.
    
    fun readAll(block: Boolean = true, limit: Int = 0): List<String> { // Read out all lines up to a limit if greater than zero, returning an empty list if none are available and no blocking is allowed 
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

class DummyBlackbox(): Blackbox {
}

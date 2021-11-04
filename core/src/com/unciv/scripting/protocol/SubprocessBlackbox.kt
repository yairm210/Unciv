package com.unciv.scripting.protocol

import com.unciv.scripting.utils.Blackbox
import java.io.*


class SubprocessBlackbox(val processCmd: Array<String>): Blackbox {

    var process: java.lang.Process? = null
    
    var inStream: BufferedReader? = null
    var outStream: BufferedWriter? = null
    
    var processLaunchFail = ""
    
    override val isAlive: Boolean
        get() = process != null && process!!.isAlive()
    
    override val readyForWrite: Boolean
        get() = isAlive
        
    override val readyForRead: Int
        get() = if (isAlive && inStream!!.ready()) 1 else 0
    
    init {
        start()
    }
    
    override fun toString(): String {
        return "${this::class.simpleName}(process=${process})"
    }
    
    override fun start() {
        if (isAlive) {
            throw RuntimeException("Process is already running: ${process}")
        }
        try {
            process = Runtime.getRuntime().exec(processCmd)
        } catch (e: Exception) {
            process = null
            processLaunchFail = e.toString()
            return
        }
        inStream = BufferedReader(InputStreamReader(process!!.getInputStream()))
        outStream = BufferedWriter(OutputStreamWriter(process!!.getOutputStream()))
    }
    
    override fun stop(): Exception? {
        try {
            if (isAlive) {
                process!!.destroy()
                inStream!!.close()
                outStream!!.close()
            }
        } catch (e: Exception) {
            return e
        }
        process = null
        inStream = null
        outStream = null
        return null
    }
    
    override fun read(block: Boolean): String {
        if (block || readyForRead > 0) {
            return inStream!!.readLine()
        } else {
            throw IllegalStateException("Empty STDOUT for ${process}.")
        }
    }
    
     override fun write(string: String) {
        outStream!!.write(string)
        outStream!!.flush()
    }

} 

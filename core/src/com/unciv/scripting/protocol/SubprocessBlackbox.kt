package com.unciv.scripting.protocol

import java.io.*


/**
 * Blackbox that launches and wraps a child process, allowing interacting with it using a common interface.
 *
 * @property processCmd String Array of the command to run to start the child process.
 */
class SubprocessBlackbox(val processCmd: Array<String>): Blackbox {

    /**
     * The wrapped process.
     */
    var process: Process? = null

    /**
     * STDOUT of the wrapped process, or null.
     */
    var inStream: BufferedReader? = null

    /**
     * STDIN of the wrapped process, or null.
     */
    var outStream: BufferedWriter? = null

    /**
     * Null, or error message string if launching the process produced an exception.
     */
    var processLaunchFail: String? = null

    override val isAlive: Boolean
        get() {
            return try {
                    @Suppress("NewApi")
                    process != null && process!!.isAlive()
                    // Usually process will be null on Android anyway.
                } catch(e: NoSuchMethodError) { true } // NoSuchMethodError is for compiled access. NoSuchMethodException is for reflective access. There's no reflection happening in the try{} block.
            // I'm not planning to use subprocesses on Android, so it's okay if the catch{} block returns an incorrect answer.
            // But if subprocesses are to be used on Android, then more work should be done to return an accurate answer in all cases.
        }

    override val readyForWrite: Boolean
        get() = isAlive

    override val readyForRead: Int
        get() = if (isAlive && inStream!!.ready()) 1 else 0

    init {
        start()
    }

    override fun toString(): String {
        return "${this::class.simpleName}(processCmd=${processCmd}).apply{ process=${process}; inStream=${inStream}; outStream=${outStream}; processLaunchFail=${processLaunchFail} }"
    }

    /**
     * Launch the child process.
     *
     * Set the inStream and outStream to readers and writers for its STDOUT and STDIN respectively if successful.
     * Set processLauchFail to the exception raised if launching produces an exception.
     *
     * @throws RuntimeException if the process is already running.
     */
    override fun start() {
        if (isAlive) {
            throw RuntimeException("Process is already running: ${process}")
        }
        try {
            process = Runtime.getRuntime().exec(processCmd)
        } catch (e: Exception) {
            process = null // Comment this out to test the API level thing.
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
            }
        } catch (e: Exception) {
            return e
        } finally {
            try {
                inStream!!.close()
                outStream!!.close()
            } catch (e: Exception) {
            }
            process = null
            inStream = null
            outStream = null
        }
        return null
    }

    override fun read(block: Boolean): String { // TODO: Max wait time (and periodic checking that process hasn't crashed) possible?
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

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
        get() = process != null && process!!.isAlive()
//        get() {
//            return try {
//                    @Suppress("NewApi")
//                    process != null && process!!.isAlive()
//                    // Usually process will be null on Android anyway.
//                } catch(e: NoSuchMethodError) { true } // Compiled access.
//                catch(e: NoSuchMethodException) { true } // Reflective access.
//                // TODO: API Level 26. But also, it's not like using subprocesses is actually planned for scripting on Android.
//        }

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

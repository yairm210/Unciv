package com.unciv.app.desktop

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Raspberry PI  helper class
 * https://stackoverflow.com/questions/37053271/the-ideal-way-to-detect-a-raspberry-pi-from-java-jar
 * @author wf
 */
object RaspberryPiDetector {
    var debug = false
    /**
     * check if this java vm runs on a raspberry PI
     *
     * @return true if this is running on a Raspbian Linux
     */
    fun isRaspberryPi(): Boolean {
        val osRelease = osRelease()
        return osRelease != null && osRelease.contains("Raspbian")
    }

    /**
     * read the first line from the given file
     *
     * @param file
     * @return the first line
     */
    private fun readFirstLine(file: File): String? {
        var firstLine: String? = null
        try {
            if (file.canRead()) {
                val fis = FileInputStream(file)
                val bufferedReader = BufferedReader(
                        InputStreamReader(fis))
                firstLine = bufferedReader.readLine()
                fis.close()
            }
        } catch (th: Throwable) {
            if (debug) th.printStackTrace()
        }
        return firstLine
    }

    /**
     * get the operating System release
     *
     * @return the first line from /etc/os-release or null
     */
    private fun osRelease(): String? {
        val os = System.getProperty("os.name")
        if (os.startsWith("Linux")) {
            val osRelease = File("/etc", "os-release")
            return readFirstLine(osRelease)
        }
        return null
    }

}
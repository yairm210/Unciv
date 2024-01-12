package com.unciv.logic.github

import com.badlogic.gdx.files.FileHandle
import com.unciv.utils.debug
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Utility - extract Zip archives
 * @see  [Zip.extractFolder]
 */
object Zip {
    private const val bufferSize = 2048

    /**
     * Extract one Zip file recursively (nested Zip files are extracted in turn).
     *
     * The source Zip is not deleted, but successfully extracted nested ones are.
     *
     * **Warning**: Extracting into a non-empty destination folder will merge contents. Existing
     * files also included in the archive will be partially overwritten, when the new data is shorter
     * than the old you will get _mixed contents!_
     *
     * @param zipFile The Zip file to extract
     * @param unzipDestination The folder to extract into, preferably empty (not enforced).
     */
    fun extractFolder(zipFile: FileHandle, unzipDestination: FileHandle) {
        // I went through a lot of similar answers that didn't work until I got to this gem by NeilMonday
        //  (with mild changes to fit the FileHandles)
        // https://stackoverflow.com/questions/981578/how-to-unzip-files-recursively-in-java

        debug("Extracting %s to %s", zipFile, unzipDestination)
        // establish buffer for writing file
        val data = ByteArray(bufferSize)

        fun streamCopy(fromStream: InputStream, toHandle: FileHandle) {
            val inputStream = BufferedInputStream(fromStream)
            var currentByte: Int

            // write the current file to disk
            val fos = FileOutputStream(toHandle.file())
            val dest = BufferedOutputStream(fos, bufferSize)

            // read and write until last byte is encountered
            while (inputStream.read(data, 0, bufferSize).also { currentByte = it } != -1) {
                dest.write(data, 0, currentByte)
            }
            dest.flush()
            dest.close()
            inputStream.close()
        }

        val file = zipFile.file()
        val zip = ZipFile(file)
        //unzipDestination.mkdirs()
        val zipFileEntries = zip.entries()

        // Process each entry
        while (zipFileEntries.hasMoreElements()) {
            // grab a zip file entry
            val entry = zipFileEntries.nextElement() as ZipEntry
            val currentEntry = entry.name
            val destFile = unzipDestination.child(currentEntry)
            val destinationParent = destFile.parent()

            // create the parent directory structure if needed
            destinationParent.mkdirs()
            if (!entry.isDirectory) {
                streamCopy ( zip.getInputStream(entry), destFile)
            }
            // The new file has a current last modification time
            // and not the  one stored in the archive - we could:
            //  'destFile.file().setLastModified(entry.time)'
            // but later handling will throw these away anyway,
            // and GitHub sets all timestamps to the download time.

            if (currentEntry.endsWith(".zip")) {
                // found a zip file, try to open
                extractFolder(destFile, destinationParent)
                destFile.delete()
            }
        }
        zip.close() // Needed so we can delete the zip file later
    }
}

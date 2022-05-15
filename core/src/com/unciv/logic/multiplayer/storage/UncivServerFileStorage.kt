package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import java.io.FileNotFoundException
import java.lang.Exception

class UncivServerFileStorage(val serverUrl: String, val timeout: Int = 30000) : FileStorage {
    override fun saveFileData(fileName: String, data: String, overwrite: Boolean) {
        SimpleHttp.sendRequest(Net.HttpMethods.PUT, fileUrl(fileName), data, timeout) {
                success, result, _ ->
            if (!success) {
                println(result)
                throw Exception(result)
            }
        }
    }

    override fun loadFileData(fileName: String): String {
        var fileData = ""
        SimpleHttp.sendGetRequest(fileUrl(fileName), timeout = timeout){
                success, result, code ->
            if (!success) {
                println(result)
                when (code) {
                    404 -> throw FileNotFoundException(result)
                    else -> throw Exception(result)
                }

            }
            else fileData = result
        }
        return fileData
    }

    override fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not yet implemented")
    }

    override fun deleteFile(fileName: String) {
        SimpleHttp.sendRequest(Net.HttpMethods.DELETE, fileUrl(fileName), "", timeout) {
                success, result, code ->
            if (!success) {
                when (code) {
                    404 -> throw FileNotFoundException(result)
                    else -> throw Exception(result)
                }
            }
        }
    }

    private fun fileUrl(fileName: String) = "$serverUrl/files/$fileName"
}

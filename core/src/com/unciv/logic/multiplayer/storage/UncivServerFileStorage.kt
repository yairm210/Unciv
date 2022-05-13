package com.unciv.logic.multiplayer.storage

import com.badlogic.gdx.Net
import java.lang.Exception

class UncivServerFileStorage(val serverUrl:String): FileStorage {
    override fun saveFileData(fileName: String, data: String, overwrite: Boolean) {
        SimpleHttp.sendRequest(Net.HttpMethods.PUT, "$serverUrl/files/$fileName", data) { success: Boolean, result: String ->
            if (!success) {
                println(result)
                throw Exception(result)
            }
        }
    }

    override fun loadFileData(fileName: String): String {
        var fileData = ""
        SimpleHttp.sendGetRequest("$serverUrl/files/$fileName") { success: Boolean, result: String ->
            if (!success) {
                println(result)
                throw Exception(result)
            }
            fileData = result
        }
        return fileData
    }

    override fun getFileMetaData(fileName: String): FileMetaData {
        TODO("Not yet implemented")
    }

    override fun deleteFile(fileName: String) {
        SimpleHttp.sendRequest(Net.HttpMethods.DELETE, "$serverUrl/files/$fileName", "") { success: Boolean, result: String ->
            if (!success) throw Exception(result)
        }
    }

}

package com.unciv.ui.worldscreen.optionstable

import com.unciv.logic.GameSaver
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class DropBox(){

    /**
     * @param dropboxApiArg If true, then the data will be sent via a Dropbox-Api-Arg header and not via the post body
     */
    fun dropboxApi(url:String, data:String="",dropboxApiArg:Boolean=false):String{

        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "POST"  // optional default is GET

            setRequestProperty("Authorization","Bearer LTdBbopPUQ0AAAAAAAACxh4_Qd1eVMM7IBK3ULV3BgxzWZDMfhmgFbuUNF_rXQWb")
            if(!dropboxApiArg) setRequestProperty("Content-Type","application/json")
            setRequestProperty("Dropbox-API-Arg", data)
            doOutput = true

            try {
                if(!dropboxApiArg) {
                    val postData: ByteArray = data.toByteArray(StandardCharsets.UTF_8)
                    val outputStream = DataOutputStream(outputStream)
                    outputStream.write(postData)
                    outputStream.flush()
                }

                val reader = BufferedReader(InputStreamReader(inputStream))
                val output = reader.readText()


                println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")
                println(output)
                return output
            }
            catch (ex:Exception){
                println(ex.message)
                return "Error!"
            }
        }
    }

    fun getFolderList(folder:String):FolderList{
        val response = dropboxApi("https://api.dropboxapi.com/2/files/list_folder",
                "{\"path\":\"$folder\"}")
        return GameSaver().json().fromJson(FolderList::class.java,response)
    }

    fun getFileInfo(fileName:String):String{
        val response = dropboxApi("https://content.dropboxapi.com/2/files/download",
                "{\"path\":\"$fileName\"}",true)
        return response
    }

    class FolderList{
        var entries = ArrayList<FolderListEntry>()
    }

    class FolderListEntry{
        var name=""
        var path_display=""
    }

}
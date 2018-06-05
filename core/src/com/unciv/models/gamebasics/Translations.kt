package com.unciv.models.gamebasics

import com.badlogic.gdx.utils.JsonReader
import java.util.*

class Translations() : HashMap<String, HashMap<String, String>>(){

    constructor(json:String):this(){
        val jsonValue = JsonReader().parse(json)!!

//        """
//            {
//                "a": {"k1":"v1",k2:"v2"},
//                b:{k3:"v3"}
//            }"""

        var currentEntry = jsonValue.child
        while(currentEntry!=null){
            val entryMap = HashMap<String,String>()
            this[currentEntry.name!!]=entryMap

            var currentLanguage = currentEntry.child
            while(currentLanguage!=null){
                entryMap[currentLanguage.name!!]=currentLanguage.asString()
                currentLanguage = currentLanguage.next
            }
            currentEntry = currentEntry.next
        }
    }

    fun get(text:String,language:String): String {
        if(!containsKey(text) || !get(text)!!.containsKey(language)) return text
        return get(text)!![language]!!
    }

    fun getLanguages(): List<String> {
        return mutableListOf("English").apply { addAll(values.flatMap { it.keys }) }
    }
}
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
//
//        val squareBraceRegex = Regex("\\[(.*?)\\]")
//        for (word in values)
//            for(translationLanguage in word.keys)
//                if(word[translationLanguage]!!.contains(squareBraceRegex))
//                    word[translationLanguage] = word[translationLanguage]!!.replace(squareBraceRegex,"[]")

    }

    fun get(text:String,language:String): String {
        if(!hasTranslation(text,language)) return text
        return get(text)!![language]!!
    }

    fun hasTranslation(text:String,language:String): Boolean {
        return containsKey(text) && get(text)!!.containsKey(language)
    }

    fun getLanguages(): List<String> {
        return mutableListOf("English").apply { addAll(values.flatMap { it.keys }.distinct()) }
    }
}
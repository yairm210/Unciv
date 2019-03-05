package com.unciv.models.gamebasics

import com.badlogic.gdx.utils.JsonReader
import com.unciv.UnCivGame
import java.util.*

class Translations() : HashMap<String, HashMap<String, String>>(){

    constructor(json:String):this(){
        val jsonValue = JsonReader().parse(json)!!

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
        if(!hasTranslation(text,language)) return text
        return get(text)!![language]!!
    }

    fun hasTranslation(text:String,language:String): Boolean {
        return containsKey(text) && get(text)!!.containsKey(language)
    }

    fun getLanguages(): List<String> {
        val toReturn =  mutableListOf("English")
        toReturn.addAll(values.flatMap { it.keys }.distinct())
        toReturn.remove("Japanese")
        return toReturn
    }

    companion object {
        fun translateBonusOrPenalty(unique:String): String {
            val regexResult = Regex("""(Bonus|Penalty) vs (.*) (\d*)%""").matchEntire(unique)
            if(regexResult==null) return unique.tr()
            else{
                val start = regexResult.groups[1]!!.value+" vs ["+regexResult.groups[2]!!.value+"]"
                val translatedUnique = start.tr() + " "+ regexResult.groups[3]!!.value+"%"
                return translatedUnique
            }
        }
    }
}


fun String.tr(): String {
    if(contains("[")){ // Placeholders!
        /**
         * I'm SURE there's an easier way to do this but I can't think of it =\
         * So what's all this then?
         * Well, not all languages are like English. So say I want to say "work on Library has completed in Akkad",
         * but in a completely different language like Japanese or German,
         * It could come out "Akkad hast die worken onner Library gerfinishen" or whatever,
         * basically, the order of the words in the sentance is not guaranteed.
         * So to translate this, I give a sentence like "work on [building] has completed in [city]"
         * and the german can put those placeholders where he wants, so  "[city] hast die worken onner [building] gerfinishen"
         * The string on which we call tr() will look like "work on [library] has completed in [Akkad]"
         * We will find the german placeholder text, and replace the placeholders with what was filled in the text we got!
         */

        val squareBraceRegex = Regex("\\[(.*?)\\]")
        val englishTranslationPlaceholder = GameBasics.Translations.keys
                .firstOrNull { it.replace(squareBraceRegex,"[]") == replace(squareBraceRegex,"[]") }
        if(englishTranslationPlaceholder==null ||
                !GameBasics.Translations[englishTranslationPlaceholder]!!.containsKey(UnCivGame.Current.settings.language)){
            // Translation placeholder doesn't exist for this language
            return this.replace("[","").replace("]","")
        }

        val termsInMessage = squareBraceRegex.findAll(this).map { it.groups[1]!!.value }.toMutableList()
        val termsInTranslationPlaceholder = squareBraceRegex.findAll(englishTranslationPlaceholder).map { it.value }.toMutableList()
        if(termsInMessage.size!=termsInTranslationPlaceholder.size)
            throw Exception("Message $this has a different number of terms than the placeholder $englishTranslationPlaceholder!")

        var languageSpecificPlaceholder = GameBasics.Translations[englishTranslationPlaceholder]!![UnCivGame.Current.settings.language]!!
        for(i in 0 until termsInMessage.size){
            languageSpecificPlaceholder = languageSpecificPlaceholder.replace(termsInTranslationPlaceholder[i], termsInMessage[i].tr())
        }
        return languageSpecificPlaceholder.tr()
    }
    if(contains("{")){ // sentence
        return Regex("\\{(.*?)\\}").replace(this) { it.groups[1]!!.value.tr() }
    }
    val translation = GameBasics.Translations.get(this, UnCivGame.Current.settings.language) // single word
    return translation
}

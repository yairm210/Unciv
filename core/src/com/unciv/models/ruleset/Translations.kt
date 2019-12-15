package com.unciv.models.ruleset

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.utils.JsonReader
import com.unciv.UncivGame

class TranslationEntry(val entry: String) : HashMap<String, String>() {

    /** For memory performance on .tr(), which was atrociously memory-expensive */
    var entryWithShortenedSquareBrackets =""

    init {
        if(entry.contains('['))
            entryWithShortenedSquareBrackets=entry.replace(squareBraceRegex,"[]")
    }
}

class Translations : LinkedHashMap<String, TranslationEntry>(){

    fun add(json:String){
        val jsonValue = JsonReader().parse(json)!!

        var currentEntry = jsonValue.child
        while(currentEntry!=null){
            val currentEntryName = currentEntry.name!!
            val translationEntry = TranslationEntry(currentEntryName)
            this[currentEntryName]=translationEntry

            var currentLanguage = currentEntry.child
            while(currentLanguage!=null){
                translationEntry[currentLanguage.name!!]=currentLanguage.asString()
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
        val toReturn =  mutableListOf<String>()

        for(entry in values)
            for(languageName in entry.keys)
                if(!toReturn.contains(languageName)) toReturn.add(languageName)

        toReturn.remove("Japanese") // These were for tests but were never actually seriously translated
        toReturn.remove("Thai")
        return toReturn
    }

    companion object {
        fun translateBonusOrPenalty(unique:String): String {
            val regexResult = Regex("""(Bonus|Penalty) vs (.*) (\d*)%""").matchEntire(unique)
            if(regexResult==null) return unique.tr()
            else{
                var separatorCharacter = " "
                if (UncivGame.Current.settings.language=="Simplified_Chinese")separatorCharacter = ""
                val start = regexResult.groups[1]!!.value+" vs ["+regexResult.groups[2]!!.value+"]"
                val translatedUnique = start.tr() + separatorCharacter + regexResult.groups[3]!!.value+"%"
                return translatedUnique
            }
        }
    }
}

class TranslationFileReader(){
    fun read(translationFile:String): LinkedHashMap<String, String> {
        val translations = LinkedHashMap<String,String>()
        val text = Gdx.files.internal(translationFile)
        for(line in text.reader().readLines()){
            if(!line.contains(" = ")) continue
            val splitLine = line.split(" = ")
            val key = splitLine[0].replace("\\n","\n")
            val value = splitLine[1].replace("\\n","\n")
            if(value!="") // this means this wasn't translated yet
                translations[key] = value
        }
        return translations
    }

    fun writeByTemplate(language:String, translations: HashMap<String,String>){
        val templateFile = Gdx.files.internal("jsons/translationsByLanguage/template.properties")
        val stringBuilder = StringBuilder()
        for(line in templateFile.reader().readLines()){
            if(!line.contains(" = ")){ // copy as-is
                stringBuilder.appendln(line)
                continue
            }
            val translationKey = line.split(" = ")[0].replace("\\n","\n")
            var translationValue = ""
            if(translations.containsKey(translationKey)) translationValue = translations[translationKey]!!
            else stringBuilder.appendln(" # Requires translation!")
            val lineToWrite = translationKey.replace("\n","\\n") +
                    " = "+ translationValue.replace("\n","\\n")
            stringBuilder.appendln(lineToWrite)
        }
        Gdx.files.local("jsons/translationsByLanguage/$language.properties")
                .writeString(stringBuilder.toString(),false,Charsets.UTF_8.name())
    }
}

val squareBraceRegex = Regex("\\[(.*?)\\]") // we don't need to allocate different memory for this every time we .tr()

val eitherSquareBraceRegex=Regex("\\[|\\]")

fun String.tr(): String {

    // THIS IS INCREDIBLY INEFFICIENT and causes loads of memory problems!
    if(contains("[")){ // Placeholders!
        /**
         * I'm SURE there's an easier way to do this but I can't think of it =\
         * So what's all this then?
         * Well, not all languages are like English. So say I want to say "work on Library has completed in Akkad",
         * but in a completely different language like Japanese or German,
         * It could come out "Akkad hast die worken onner Library gerfinishen" or whatever,
         * basically, the order of the words in the sentence is not guaranteed.
         * So to translate this, I give a sentence like "work on [building] has completed in [city]"
         * and the german can put those placeholders where he wants, so  "[city] hast die worken onner [building] gerfinishen"
         * The string on which we call tr() will look like "work on [library] has completed in [Akkad]"
         * We will find the german placeholder text, and replace the placeholders with what was filled in the text we got!
         */

        val translationStringWithSquareBracketsOnly = replace(squareBraceRegex,"[]")

        val translationEntry = UncivGame.Current.ruleset.Translations.values
                .firstOrNull { translationStringWithSquareBracketsOnly == it.entryWithShortenedSquareBrackets }

        if(translationEntry==null ||
                !translationEntry.containsKey(UncivGame.Current.settings.language)){
            // Translation placeholder doesn't exist for this language, default to English
            return this.replace(eitherSquareBraceRegex,"")
        }

        val termsInMessage = squareBraceRegex.findAll(this).map { it.groups[1]!!.value }.toList()
        val termsInTranslationPlaceholder = squareBraceRegex.findAll(translationEntry.entry).map { it.value }.toList()
        if(termsInMessage.size!=termsInTranslationPlaceholder.size)
            throw Exception("Message $this has a different number of terms than the placeholder $translationEntry!")

        var languageSpecificPlaceholder = translationEntry[UncivGame.Current.settings.language]!!
        for(i in termsInMessage.indices){
            languageSpecificPlaceholder = languageSpecificPlaceholder.replace(termsInTranslationPlaceholder[i], termsInMessage[i].tr())
        }
        return languageSpecificPlaceholder.tr()
    }

    if(contains("{")){ // sentence
        return Regex("\\{(.*?)\\}").replace(this) { it.groups[1]!!.value.tr() }
    }

    val translation = UncivGame.Current.ruleset.Translations.get(this, UncivGame.Current.settings.language) // single word
    return translation
}

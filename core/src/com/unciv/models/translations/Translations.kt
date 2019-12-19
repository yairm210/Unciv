package com.unciv.models.translations

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class Translations : LinkedHashMap<String, TranslationEntry>(){

    var percentCompleteOfLanguages = HashMap<String,Int>()

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

        return toReturn
    }


    fun tryReadTranslationForLanguage(language: String){
        val translationStart = System.currentTimeMillis()

        val translationFileName = "jsons/translationsByLanguage/$language.properties"
        if (!Gdx.files.internal(translationFileName).exists()) return
        val languageTranslations = TranslationFileReader()
                .read(translationFileName)

        for (translation in languageTranslations) {
            if (!containsKey(translation.key))
                this[translation.key] = TranslationEntry(translation.key)

            // why not in one line, Because there were actual crashes.
            // I'm pretty sure I solved this already, but hey double-checking doesn't cost anything.
            val entry = this[translation.key]
            if(entry!=null) entry[language] = translation.value
        }

        val translationFilesTime = System.currentTimeMillis() - translationStart
        println("Loading translation file for $language - "+translationFilesTime+"ms")
    }

    fun tryReadTranslationForCurrentLanguage(){
        tryReadTranslationForLanguage(UncivGame.Current.settings.language)
    }

    fun getLanguagesWithTranslationFile(): List<String> {

        val languages = ArrayList<String>()
        // So apparently the Locales don't work for everyone, which is horrendous
        // So for those players, which seem to be Android-y, we try to see what files exist directly...yeah =/
        try{
            for(file in Gdx.files.internal("jsons/translationsByLanguage").list())
                languages.add(file.nameWithoutExtension())
        }
        catch (ex:Exception){} // Iterating on internal files will not work when running from a .jar

        languages.addAll(Locale.getAvailableLocales() // And this should work for Desktop, meaning from a .jar
                .map { it.getDisplayName(Locale.ENGLISH) }) // Maybe THIS is the problem, that the DISPLAY locale wasn't english
        // and then languages were displayed according to the player's locale... *sweatdrop*

        // These should probably be renamed
        languages.add("Simplified_Chinese")
        languages.add("Traditional_Chinese")

        languages.remove("template")
        languages.remove("completionPercentages")
        languages.remove("Thai") // Until we figure out what to do with it

        return languages.distinct()
                .filter { Gdx.files.internal("jsons/translationsByLanguage/$it.properties").exists() }
    }

    fun readAllLanguagesTranslation() {
        // Apparently you can't iterate over the files in a directory when running out of a .jar...
        // https://www.badlogicgames.com/forum/viewtopic.php?f=11&t=27250
        // which means we need to list everything manually =/

        val translationStart = System.currentTimeMillis()

        for (language in getLanguagesWithTranslationFile()) {
            tryReadTranslationForLanguage(language)
        }

        val translationFilesTime = System.currentTimeMillis() - translationStart
        println("Loading translation files - "+translationFilesTime+"ms")
    }

    fun loadPercentageCompleteOfLanguages(){
        val startTime = System.currentTimeMillis()

        percentCompleteOfLanguages = TranslationFileReader().readLanguagePercentages()

        val translationFilesTime = System.currentTimeMillis() - startTime
        println("Loading percent complete of languages - "+translationFilesTime+"ms")
    }

    fun calculatePercentageCompleteOfLanguages():HashMap<String,Int> {
        val percentComplete = HashMap<String,Int>()
        val translationStart = System.currentTimeMillis()

        var allTranslations = 0
        Gdx.files.internal("jsons/translationsByLanguage/template.properties")
                .reader().forEachLine { if(it.contains(" = ")) allTranslations+=1 }

        for(language in getLanguagesWithTranslationFile()){
            val translationFileName = "jsons/translationsByLanguage/$language.properties"
            var translationsOfThisLanguage=0
            Gdx.files.internal(translationFileName).reader()
                    .forEachLine { if(it.contains(" = ") && !it.endsWith(" = "))
                        translationsOfThisLanguage+=1 }
            percentComplete[language] = translationsOfThisLanguage*100/allTranslations
        }


        val translationFilesTime = System.currentTimeMillis() - translationStart
        println("Calculating percentage complete of languages - "+translationFilesTime+"ms")
        return percentComplete
    }


    companion object {
        fun translateBonusOrPenalty(unique:String): String {
            val regexResult = Regex("""(Bonus|Penalty) vs (.*) (\d*)%""").matchEntire(unique)
            if(regexResult==null) return unique.tr()
            else{
                var separatorCharacter = " "
                if (UncivGame.Current.settings.language=="Simplified_Chinese") separatorCharacter = ""
                val start = regexResult.groups[1]!!.value+" vs ["+regexResult.groups[2]!!.value+"]"
                val translatedUnique = start.tr() + separatorCharacter + regexResult.groups[3]!!.value+"%"
                return translatedUnique
            }
        }
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

        val translationEntry = UncivGame.Current.translations.values
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

    val translation = UncivGame.Current.translations
            .get(this, UncivGame.Current.settings.language) // single word
    return translation
}

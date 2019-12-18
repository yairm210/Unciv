package com.unciv.models.translations

import java.util.HashMap

class TranslationEntry(val entry: String) : HashMap<String, String>() {

    /** For memory performance on .tr(), which was atrociously memory-expensive */
    var entryWithShortenedSquareBrackets =""

    init {
        if(entry.contains('['))
            entryWithShortenedSquareBrackets=entry.replace(squareBraceRegex,"[]")
    }
}
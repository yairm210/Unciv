package com.unciv.models.translations

import java.util.HashMap

class TranslationEntry(val entry: String) : HashMap<String, String>() {

    // Now stored in the key of the hashmap storing the instances of this class
//    /** For memory performance on .tr(), which was atrociously memory-expensive */
//    val entryWithShortenedSquareBrackets =
//            if (entry.contains('['))
//                entry.replace(squareBraceRegex,"[]")
//            else ""
}

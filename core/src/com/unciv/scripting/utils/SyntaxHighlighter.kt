package com.unciv.scripting.utils


interface SyntaxHighlighter {
    fun cmlFromText(text: String): String {
        // https://github.com/libgdx/libgdx/wiki/Color-Markup-Language
        return text
    }
}

open class FunctionalSyntaxHighlighter() {
    open val transformList: List<(String) -> String> = listOf()
    fun cmlFromText(text: String): String {
        return transformList.fold(text){ t: String, f: (String) -> String -> f(t) }
    }
}

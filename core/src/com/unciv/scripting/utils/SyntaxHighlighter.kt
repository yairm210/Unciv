package com.unciv.scripting.utils


interface SyntaxHighlighter {
    // TODO: Implement/use these.
    fun cmlFromText(text: String): String {
        // https://github.com/libgdx/libgdx/wiki/Color-Markup-Language
        return text
    }
}

class FunctionalSyntaxHighlighter(val transformList: List<(String) -> String>): SyntaxHighlighter {
    override fun cmlFromText(text: String): String {
        return transformList.fold(text){ t: String, f: (String) -> String -> f(t) }
    }
}

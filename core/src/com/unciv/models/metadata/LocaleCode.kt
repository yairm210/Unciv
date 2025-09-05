package com.unciv.models.metadata

import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly
import java.text.NumberFormat
import java.util.Locale

/** Map Unciv language key to Java locale, for the purpose of getting a Collator for sorting.
 *  - Effect depends on the Java libraries and may not always conform to expectations.
 *    If in doubt, debug and see what Locale instance you get and compare its properties with `Locale.getDefault()`.
 *    (`Collator.getInstance(LocaleCode.*.run { Locale(language, country) }) to Collator.getInstance()`, drill to both `rules`, compare hashes - if equal and other properties equal, then Java doesn't know your Language))
 *  - For languages without an easy predefined Locale, collation or numeric formats can be forced using [Unicode Extensions for BCP 47](https://www.unicode.org/reports/tr35/#Locale_Extension_Key_and_Type_Data).
 *
 *  @property name **Must** be the same as the translation file name with ' ', '_', '-', '(', ')' removed
 *  @property languageTag IETF BCP 47 language tag - see [forLanguageTag][Locale.forLanguageTag] or [Android reference][https://developer.android.com/reference/java/util/Locale#forLanguageTag(java.lang.String)]
 *                        Usually the ISO 639-1 code for the language, a dash, and the ISO 3166 code for the nation this is predominantly spoken in
 *  @property fastlaneFolder If set, it's used instead of the language part of [languageTag] as fastlane folder name
 */
enum class LocaleCode(val languageTag: String, private val fastlaneFolder: String? = null) {
    Afrikaans("af-ZA"),
    Arabic("ar-IQ"),
    Bangla("bn-BD"),
    Belarusian("be-BY"),
    Bosnian("bs-BA"),
    BrazilianPortuguese("pt-BR"),
    Bulgarian("bg-BG"),
    Catalan("ca-ES"),
    Croatian("hr-HR"),
    Czech("cs-CZ"),
    Danish("da-DK"),
    Dutch("nl-NL"),
    English("en-US"),
    Estonian("et-EE"),
    Finnish("fi-FI"),
    French("fr-FR"),
    Galician("gl-ES"),
    German("de-DE"),
    Greek("el-GR"),
    Hindi("hi-IN"),
    Hungarian("hu-HU"),
    Indonesian("in-ID"),
    Italian("it-IT"),
    Japanese("ja-JP"),
    Korean("ko-KR"),
    Latin("la-IT"),
    Latvian("lv-LV"),
    Lithuanian("lt-LT"),
    Malay("ms-MY"),
    Maltese("mt-MT),
    Norwegian("no-NO"),
    NorwegianNynorsk("nn-NO"),
    PersianPinglishDIN("fa-IR"), // These might just fall back to default
    PersianPinglishUN("fa-IR"),
    Polish("pl-PL"),
    Portuguese("pt-PT"),
    Romanian("ro-RO"),
    Russian("ru-RU"),
    Rusyn("rue-SK-u-kr-cyrl-latn-digit", "rue"), // No specific locale exists, so use explicit cyrillic collation. Chose country with most speakers.
    Serbian("sr-RS"),
    SimplifiedChinese("zh-CN"),
    Slovak("sk-SK"),
    Spanish("es-ES"),
    Swedish("sv-SE"),
    Thai("th-TH"),
    TraditionalChinese("zh-TW"),
    Turkish("tr-TR"),
    Ukrainian("uk-UA"),
    Vietnamese("vi-VN"),
    Zulu("zu-ZA")
    ;

    @Readonly fun locale(): Locale = Locale.forLanguageTag(languageTag)
    fun fastlaneFolder(): String = this.fastlaneFolder ?: locale().language

    companion object {
        private val bannedCharacters = listOf(' ', '_', '-', '(', ')') // Things not to have in enum names

        /** Find a LocaleCode for a [language] as stored in GameSettings */
        @Readonly
        fun find(language: String): LocaleCode? {
            val languageName = language.filterNot { it in bannedCharacters }
            return LocaleCode.entries.firstOrNull { it.name == languageName }
        }

        /** Get a Java Locale for a [language] as stored in GameSettings */
        @Readonly
        fun getLocale(language: String): Locale =
            find(language)?.locale() ?: Locale.getDefault()

        /** Get the fastlane folder name for a [language] as stored in GameSettings */
        fun fastlaneFolder(language: String) =
            find(language)?.fastlaneFolder() ?: "en"

        // NumberFormat cache, key: language, value: NumberFormat
        @Cache private val languageToNumberFormat = mutableMapOf<String, NumberFormat>()

        @Readonly
        fun getNumberFormatFromLanguage(language: String): NumberFormat =
            languageToNumberFormat.getOrPut(language) {
                NumberFormat.getInstance(getLocale(language))
            }
    }
}

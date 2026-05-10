package com.unciv.models.metadata

import com.unciv.UncivGame
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly
import java.text.NumberFormat
import java.util.Locale

/** Map Unciv language key to Java locale, for the purpose of getting a Collator for sorting.
 *  Can also be used to list all available languages ([getSupportedLanguages]).
 *  Can translate Java [Locale] to/from an Unciv language as stored in [GameSettings] using [find] followed by [locale] or [languageName] calls.
 *
 *  - Effect depends on the Java libraries and may not always conform to expectations.
 *    If in doubt, debug and see what Locale instance you get and compare its properties with `Locale.getDefault()`.
 *    (`Collator.getInstance(LocaleCode.*.run { Locale(language, country) }) to Collator.getInstance()`, drill to both `rules`, compare hashes - if equal and other properties equal, then Java doesn't know your Language)
 *  - For languages without an easy predefined Locale, collation or numeric formats can be forced using [Unicode Extensions for BCP 47](https://www.unicode.org/reports/tr35/#Locale_Extension_Key_and_Type_Data).
 *
 *  @property name **Should** be the same as the translation file name with ' ', '_', '-', '(', ')' removed
 *  @property languageTag IETF BCP 47 language tag - see [forLanguageTag][Locale.forLanguageTag] or [Android reference][https://developer.android.com/reference/java/util/Locale#forLanguageTag(java.lang.String)]
 *                        Usually the ISO 639-1 code for the language, a dash, and the ISO 3166 code for the nation this is predominantly spoken in
 *  @property fastlaneFolder If set, it's used instead of the language part of [languageTag] as fastlane folder name. Query via [fastlaneFolder] method.
 *  @property languageName The Unciv language setting and file name, if different from [name]. Query via [languageName] method.
 */
enum class LocaleCode(
    val languageTag: String,
    private val fastlaneFolder: String? = null,
    private val languageName: String? = null,
    val unused: Boolean = false
) {
    Afrikaans("af-ZA"),
    Arabic("ar-IQ", unused = true),
    Bangla("bn-BD"),
    Belarusian("be-BY"),
    Bosnian("bs-BA"),
    BrazilianPortuguese("pt-BR", languageName = "Brazilian_Portuguese"),
    Bulgarian("bg-BG"),
    Catalan("ca-ES"),
    Croatian("hr-HR"),
    Czech("cs-CZ"),
    Danish("da-DK", unused = true),
    Dutch("nl-NL"),
    English("en-US"),
    Estonian("et-EE", unused = true),
    Filipino("fil-PH-u-co-search"),
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
    Latvian("lv-LV", unused = true),
    Lithuanian("lt-LT"),
    Malay("ms-MY"),
    Maltese("mt-MT"),
    Norwegian("no-NO"),
    NorwegianNynorsk("nn-NO", unused = true),
    PersianPinglishDIN("fa-IR", languageName = "Persian_(Pinglish-DIN)"), // These might just fall back to default
    PersianPinglishUN("fa-IR", languageName = "Persian_(Pinglish-UN)"),
    Polish("pl-PL"),
    Portuguese("pt-PT"),
    Romanian("ro-RO"),
    Russian("ru-RU"),
    Rusyn("rue-SK-u-kr-cyrl-latn-digit", "rue"), // No specific locale exists, so use explicit cyrillic collation. Chose country with most speakers.
    Serbian("sr-RS", unused = true),
    SimplifiedChinese("zh-CN", languageName = "Simplified_Chinese"),
    Slovak("sk-SK", unused = true),
    Spanish("es-ES"),
    Swedish("sv-SE"),
    Thai("th-TH"),
    TraditionalChinese("zh-TW", languageName = "Traditional_Chinese"),
    Turkish("tr-TR"),
    Ukrainian("uk-UA"),
    Vietnamese("vi-VN"),
    Zulu("zu-ZA")
    ;

    @Readonly fun locale(): Locale = Locale.forLanguageTag(languageTag)
    @Readonly fun fastlaneFolder(): String = this.fastlaneFolder ?: locale().language
    @Readonly fun languageName(): String = this.languageName ?: name

    companion object {
        /** Find a LocaleCode for a [language] as stored in GameSettings */
        @Readonly
        fun find(language: String) =
            LocaleCode.entries.firstOrNull { it.languageName() == language }

        @Readonly
        private fun Locale.softEquals(other: Locale) = language == other.language && country == other.country

        /** Find a LocaleCode with translation file for a java [locale] */
        @Readonly
        // Don't use Locale.equals directly - ignore script, variant and extensions
        fun find(locale: Locale) =
            LocaleCode.entries.firstOrNull { !it.unused && it.locale().softEquals(locale) }

        /** Return the system default language as setting name = file name,
         *  but only if we support a translation, otherwise an empty string
         */
        fun getSystemLanguage(): String =
            find(UncivGame.Current.getDefaultLocale())?.takeUnless { it.unused }?.languageName().orEmpty()

        /** Get a Java Locale for a [language] as stored in GameSettings */
        @Readonly
        fun getLocale(language: String): Locale =
            find(language)?.locale() ?: Locale.getDefault()

        @Readonly
        fun getSupportedLanguages() =
            entries.asSequence().filterNot { it.unused }.map { it.languageName() }

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

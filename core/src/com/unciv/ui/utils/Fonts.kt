package com.unciv.ui.utils

import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.unciv.UncivGame
import core.java.nativefont.NativeFont
import core.java.nativefont.NativeFontPaint

object Fonts {
    // caches for memory and time saving
    private val characterSetCache = HashMap<String, String>()
    private val fontCache = HashMap<String, BitmapFont>()

    private fun getCharactersForFont(language:String=""): String {
        if (characterSetCache.containsKey(language)) return characterSetCache[language]!!

        val startTime = System.currentTimeMillis()

        // Basic Character Set - symbols missing from this will not be displayed, unless one of the
        // 'complex' languages is chosen, in which case the translation file is used as character set as well.
        // This means that for example user-set city names might not be displayed as entered.
        // Missing Characters will be entirely invisible, not even take up horizontal space.
        // Note that " (normal double quotes) and _ (underscore) _are_ such invisible characters.
        val defaultText =
                "AÀÁÀÄĂÂEÈÉÊĚÉÈIÌÍÏÍÎOÒÓÖÔÓÖƠUÙÚÜƯŮÚÜ" +            // Latin uppercase vowels and similar symbols
                "aäàâăäâąáeéèêęěèiìîìíoòöôöơóuùüưůûú" +             // Latin lowercase vowels and similar symbols
                "BCČĆDĐĎFGHJKLŁĹĽMNPQRŘŔSŠŚTŤVWXYÝZŽŻŹ" +           // Latin uppercase consonants and similar symbols
                "bcčćçdđďfghjklłĺľmnńňñpqrřŕsșšśtțťvwxyýzžżź" +     // Latin lowercase consonants and similar symbols
                "АБВГҐДЂЕЁЄЖЗЅИІЇЙЈКЛЉМНЊОПРСТЋУЎФХЦЧЏШЩЪЫЬЭЮЯабвгґдђеёєжзѕиіїйјклљмнњопрстћуўфхцчџшщъыьэюя" +  // Russian
                "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψωάßΆέΈέΉίϊΐΊόΌύΰϋΎΫΏ" +                         // Greek
                "กขฃคฅฆงจฉชซฌญฎฏฐฑฒณดตถทธนบปผฝพฟภมยรฤลฦวศษสหฬอฮฯะัาำิีึืฺุู฿เแโใไๅๆ็่้๊๋์ํ๎๏๐๑๒๓๔๕๖๗๘๙๚๛" +                 // Thai
                "İıÇŞşĞğ"+      // Turkish
                "øæå" +         // Scandinavian
                "ÃÕãõ" +        // Portuguese
                "1234567890" +
                "‘?ʼ’'“!”(%)[#]{@}/&\\<-+÷×=>®©\$€£¥¢:;,.…¡*|«»—∞✘✔"
        val charSet = HashSet<Char>()
        charSet.addAll(defaultText.asIterable())

        if (language != "") {
            for (entry in UncivGame.Current.translations.entries) {
                for (lang in entry.value) {
                    if (lang.key == language) charSet.addAll(lang.value.asIterable())
                }
            }
        }
        val characterSetString = charSet.joinToString("")
        characterSetCache[language]=characterSetString

        val totalTime = System.currentTimeMillis() - startTime
        println("Loading characters for font - "+totalTime+"ms")

        return characterSetString
    }

   fun getFont(size: Int): BitmapFont {
       val language = UncivGame.Current.settings.language
       val fontForLanguage ="Nativefont"
       val isUniqueFont = language.contains("Chinese") || language == "Korean" || language=="Japanese"
       val keyForFont = if(!isUniqueFont) "$fontForLanguage $size" else "$fontForLanguage $size $language"
       if (fontCache.containsKey(keyForFont)) return fontCache[keyForFont]!!

       val font=NativeFont(NativeFontPaint(size))
       val charsForFont = getCharactersForFont(if(isUniqueFont) language else "")


       font.appendText(charsForFont)


       fontCache[keyForFont] = font
       return font
   }
}

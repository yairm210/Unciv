package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import core.java.nativefont.NativeFont
import core.java.nativefont.NativeFontPaint
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class Fonts {
    companion object {
        val characterSetCache = HashMap<String, String>()
        val fontCache = HashMap<String, BitmapFont>()
    }
    fun download(link: String,fontForLanguage: String) {
        if (!Gdx.files.local("fonts").exists())
            Gdx.files.local("fonts").mkdirs()
            val input = URL(link).openStream()
            val output = FileOutputStream(Gdx.files.local("fonts/$fontForLanguage.ttf").file())
            input.use {
                output.use {
                    input.copyTo(output)
                }
            }
    }
    fun getMD5(fontForLanguage: String):String {
        val sb = StringBuffer("")
        val md = MessageDigest.getInstance("MD5")
        if (Gdx.files.local("fonts/$fontForLanguage.ttf").exists()) {
            md.update(FileInputStream(Gdx.files.local("fonts/$fontForLanguage.ttf").file()).readBytes())
            val b = md.digest()
            for (i in b) {
                var d = i.toInt()
                if (d < 0) d = i + 256
                if (d < 16) sb.append("0")
                sb.append(Integer.toHexString(d))
            }
            return sb.toString()
        }
        return ""
    }
    fun containsFont(): Boolean {
        if (Gdx.files.local("fonts/WenQuanYiMicroHei.ttf").exists())
            return true
        return false
    }

    fun getCharactersForFont(language:String=""): String {
        if (characterSetCache.containsKey(language)) return characterSetCache[language]!!

        val defaultText = "AÀÁBCČĆDĐEÈÉFGHIÌÍÏJKLMNOÒÓÖPQRSŠTUÙÚÜVWXYZŽaäàâăbcčćçdđeéèfghiìîjklmnoòöpqrsșštțuùüvwxyzž" +
                "АБВГҐДЂЕЁЄЖЗЅИІЇЙЈКЛЉМНЊОПРСТЋУЎФХЦЧЏШЩЪЫЬЭЮЯабвгґдђеёєжзѕиіїйјклљмнњопрстћуўфхцчџшщъыьэюя" + // Russian
                "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψωάßΆέΈέΉίϊΐΊόΌύΰϋΎΫΏ" +  // Greek
                "ÀÄĂÂĎÊĚÉÈÍÎŁĹĽÔÓÖƠŘŔŚŤƯŮÚÜÝŻŹäâąďêęěłĺľńňôöơřŕśťưůýżźáèìíóú" +
                "กขฃคฅฆงจฉชซฌญฎฏฐฑฒณดตถทธนบปผฝพฟภมยรฤลฦวศษสหฬอฮฯะัาำิีึืฺุู฿เแโใไๅๆ็่้๊๋์ํ๎๏๐๑๒๓๔๕๖๗๘๙๚๛" +  // Thai
                "1234567890" +
                "‘?’'“!”(%)[#]{@}/&\\<-+÷×=>®©\$€£¥¢:;,.¡*|"
        val charSet = HashSet<Char>()
        charSet.addAll(defaultText.asIterable())

        if (language != "") {
            if (Gdx.files.internal("jsons/BasicHelp/BasicHelp_$language.json").exists())
                charSet.addAll(Gdx.files.internal("jsons/BasicHelp/BasicHelp_$language.json").readString().asIterable())
            if (Gdx.files.internal("jsons/Nations/Nations_$language.json").exists())
                charSet.addAll(Gdx.files.internal("jsons/Nations/Nations_$language.json").readString().asIterable())
            if (Gdx.files.internal("jsons/Tutorials/Tutorials_$language.json").exists())
                charSet.addAll(Gdx.files.internal("jsons/Tutorials/Tutorials_$language.json").readString().asIterable())

            for (entry in GameBasics.Translations.entries) {
                for (lang in entry.value) {
                    if (lang.key == language) charSet.addAll(lang.value.asIterable())
                }
            }
        }
        val characterSetString = charSet.joinToString("")
        characterSetCache[language]=characterSetString
        return characterSetString
    }

   fun getFont(size: Int): BitmapFont {
       val language = UnCivGame.Current.settings.language
       val fontForLanguage ="Nativefont"
       val isUniqueFont = language.contains("Chinese") || language == "Korean"
       val keyForFont = if(!isUniqueFont) "$fontForLanguage $size" else "$fontForLanguage $size $language"
       if (fontCache.containsKey(keyForFont)) return fontCache[keyForFont]!!

       val font=NativeFont(NativeFontPaint(size))
       val charsForFont = getCharactersForFont(if(isUniqueFont) language else "")
       font.appendText(charsForFont)
       fontCache[keyForFont] = font
       return font
   }
}

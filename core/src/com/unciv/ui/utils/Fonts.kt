package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.unciv.models.gamebasics.GameBasics
import core.java.nativefont.NativeFont
import core.java.nativefont.NativeFontPaint

class Fonts {
    companion object {
        // Contains e.g. "Arial 22", fontname and size, to BitmapFont
        val fontCache = HashMap<String, NativeFont>()
    }
   fun getCharsForFont(): String {
       val defaultText = "ABCČĆDĐEFGHIJKLMNOPQRSŠTUVWXYZŽaäàâăbcčćçdđeéfghiîjklmnoöpqrsșštțuüvwxyzž" +
               "АБВГҐДЂЕЁЄЖЗЅИІЇЙЈКЛЉМНЊОПРСТЋУЎФХЦЧЏШЩЪЫЬЭЮЯабвгґдђеёєжзѕиіїйјклљмнњопрстћуўфхцчџшщъыьэюя" +
               "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψωάßΆέΈέΉίϊΐΊόΌύΰϋΎΫΏÄĂÂÊÉÎÔÖƠƯÜäăâêôöơưüáéèíóú1234567890" +
               "‘?’'“!”(%)[#]{@}/&\\<-+÷×=>®©\$€£¥¢:;,.*|"
       val charSet = HashSet<Char>()
       charSet.addAll(defaultText.asIterable())

       if (Gdx.files.internal("jsons/BasicHelp/BasicHelp_Simplified_Chinese.json").exists())
           charSet.addAll(Gdx.files.internal("jsons/BasicHelp/BasicHelp_Simplified_Chinese.json").readString().asIterable())
       if (Gdx.files.internal("jsons/Nations_Simplified_Chinese.json").exists())
           charSet.addAll(Gdx.files.internal("jsons/Nations_Simplified_Chinese.json").readString().asIterable())
       if (Gdx.files.internal("jsons/Tutorials/Tutorials_Simplified_Chinese.json").exists())
           charSet.addAll(Gdx.files.internal("jsons/Tutorials/Tutorials_Simplified_Chinese.json").readString().asIterable())

       for (entry in GameBasics.Translations.entries) {
           for (lang in entry.value) {
               if (lang.key.contains("Chinese")) charSet.addAll(lang.value.asIterable())
           }
       }
       return charSet.joinToString()
   }
   fun getFont(size: Int): NativeFont {
     val fontForLanguage ="Nativefont"
       val keyForFont = "$fontForLanguage $size"
       val font=NativeFont(NativeFontPaint(size))
       font.appendText(getCharsForFont())
       fontCache[keyForFont] = font
       return font
   }
}
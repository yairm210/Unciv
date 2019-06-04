package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import core.java.nativefont.NativeFont
import core.java.nativefont.NativeFontPaint
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.Texture
import java.io.FileOutputStream
import java.net.URL

class Fonts {
    companion object {
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
   fun getFont(size: Int): BitmapFont {
       if(UnCivGame.Current.settings.fontSet=="WenQuanYiMicroHei"){
           val fontForLanguage="WenQuanYiMicroHei"
           val keyForFont = "$fontForLanguage $size"
           if (fontCache.containsKey(keyForFont))return fontCache[keyForFont]!!
           if (!Gdx.files.local("fonts/WenQuanYiMicroHei.ttf").exists())
               download("https://github.com/layerssss/wqy/raw/gh-pages/fonts/WenQuanYiMicroHei.ttf",fontForLanguage)
           val generator = FreeTypeFontGenerator(Gdx.files.local("fonts/WenQuanYiMicroHei.ttf"))
           val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
           parameter.size = size
           parameter.minFilter = Texture.TextureFilter.Linear
           parameter.magFilter = Texture.TextureFilter.Linear
           parameter.characters = getCharsForFont()
           val font = generator.generateFont(parameter)
           fontCache[keyForFont] = font
           return font
       }
     val fontForLanguage ="Nativefont"
       val keyForFont = "$fontForLanguage $size"
       if (fontCache.containsKey(keyForFont))return fontCache[keyForFont]!!
       val font=NativeFont(NativeFontPaint(size))
       font.appendText(getCharsForFont())
       fontCache[keyForFont] = font
       return font
   }
}

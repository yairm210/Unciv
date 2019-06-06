package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import core.java.nativefont.NativeFont
import core.java.nativefont.NativeFontPaint
import java.io.FileOutputStream
import java.io.FileInputStream
import java.net.URL
import java.security.*

class Fonts {
    companion object {
        val fontCache = HashMap<String, BitmapFont>()
        var fontDownloadIsWell=1
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
        md.update(FileInputStream(Gdx.files.local("fonts/$fontForLanguage.ttf").file()).readBytes())
        val b = md.digest()
        for(i in b) {
            var d = i.toInt()
            if (d < 0) d = i+256
            if (d < 16) sb.append("0")
            sb.append(Integer.toHexString(d))
        }
        return sb.toString()
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
           if (!Gdx.files.local("fonts/$fontForLanguage.ttf").exists())
               download("https://github.com/layerssss/wqy/raw/gh-pages/fonts/WenQuanYiMicroHei.ttf",fontForLanguage)
           val MD5_WenQuanYiMicroHei=getMD5(fontForLanguage)
           if (MD5_WenQuanYiMicroHei!="96574d6f2f2bbd4a3ce56979623b1952"){
               fontDownloadIsWell=0
               Gdx.files.local("fonts/$fontForLanguage.ttf").delete()
           }
           else {
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

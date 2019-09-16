package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tr
import com.unciv.ui.worldscreen.optionstable.PopupTable
import core.java.nativefont.NativeFont
import core.java.nativefont.NativeFontPaint
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

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

    fun getCharsForFont(withChinese:Boolean): String {
        val defaultText = "ABCČĆDĐEFGHIJKLMNOPQRSŠTUVWXYZŽaäàâăbcčćçdđeéfghiîjklmnoöpqrsșštțuüvwxyzž" +
                "АБВГҐДЂЕЁЄЖЗЅИІЇЙЈКЛЉМНЊОПРСТЋУЎФХЦЧЏШЩЪЫЬЭЮЯабвгґдђеёєжзѕиіїйјклљмнњопрстћуўфхцчџшщъыьэюя" +
                "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψωάßΆέΈέΉίϊΐΊόΌύΰϋΎΫΏ" + 
                "ÀÄĂÂĎÊĚÉÈÍÎŁĹĽÔÓÖƠŘŔŚŤƯŮÚÜŻŹäâąďêęěłĺľńňôöơřŕśťưůýżźáèìíóú1234567890" +
                "‘?’'“!”(%)[#]{@}/&\\<-+÷×=>®©\$€£¥¢:;,.¡*|"
            val charSet = HashSet<Char>()
            charSet.addAll(defaultText.asIterable())

            if(withChinese) {
                if (Gdx.files.internal("jsons/BasicHelp/BasicHelp_Simplified_Chinese.json").exists())
                    charSet.addAll(Gdx.files.internal("jsons/BasicHelp/BasicHelp_Simplified_Chinese.json").readString().asIterable())
                if (Gdx.files.internal("jsons/Nations/Nations_Simplified_Chinese.json").exists())
                    charSet.addAll(Gdx.files.internal("jsons/Nations/Nations_Simplified_Chinese.json").readString().asIterable())
                if (Gdx.files.internal("jsons/Tutorials/Tutorials_Simplified_Chinese.json").exists())
                    charSet.addAll(Gdx.files.internal("jsons/Tutorials/Tutorials_Simplified_Chinese.json").readString().asIterable())

                for (entry in GameBasics.Translations.entries) {
                    for (lang in entry.value) {
                        if (lang.key.contains("Chinese")) charSet.addAll(lang.value.asIterable())
                    }
                }
            }
            return charSet.joinToString("")
    }
   fun getFont(size: Int): BitmapFont {
       if(UnCivGame.Current.settings.fontSet=="WenQuanYiMicroHei"){
           val fontForLanguage="WenQuanYiMicroHei"
           val keyForFont = "$fontForLanguage $size"
           if (fontCache.containsKey(keyForFont))
               return fontCache[keyForFont]!!
           if (getMD5(fontForLanguage)!="96574d6f2f2bbd4a3ce56979623b1952"){
               Gdx.files.local("fonts/$fontForLanguage.ttf").delete()
               UnCivGame.Current.settings.fontSet="NativeFont(Recommended)"
               Gdx.app.postRunnable {
                   val checksumFailed = PopupTable(UnCivGame.Current.worldScreen)
                   checksumFailed.add("Checksum error!\nIf you want to use the font \"WenQuanYiMicroHei\", please download again.".toLabel().setFontColor(Color.RED)).row()
                   checksumFailed.addButton("Close".tr()) { checksumFailed.remove() }.row()
                   checksumFailed.open()
               }
           }
           else {
               val generator = FreeTypeFontGenerator(Gdx.files.local("fonts/WenQuanYiMicroHei.ttf"))
               val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
               parameter.size = size
               parameter.minFilter = Texture.TextureFilter.Linear
               parameter.magFilter = Texture.TextureFilter.Linear
               parameter.characters = getCharsForFont(true)
               val font = generator.generateFont(parameter)
               fontCache[keyForFont] = font
               return font
           }
       }
       val fontForLanguage ="Nativefont"
       val withChinese = UnCivGame.Current.settings.language.contains("Chinese")
       val keyForFont = if(!withChinese) "$fontForLanguage $size" else "$fontForLanguage $size withChinese" // different cache for chinese
       if (fontCache.containsKey(keyForFont)) return fontCache[keyForFont]!!
       val font=NativeFont(NativeFontPaint(size))
       val charsForFont = getCharsForFont(withChinese)
       font.appendText(charsForFont)
       fontCache[keyForFont] = font
       return font
   }
}

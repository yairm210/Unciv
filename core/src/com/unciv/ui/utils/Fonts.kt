package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.unciv.UnCivGame
import com.unciv.models.gamebasics.GameBasics
import java.io.FileOutputStream
import java.net.URL

class Fonts {
    companion object {
        // Contains e.g. "Arial 22", fontname and size, to BitmapFont
        val fontCache = HashMap<String, BitmapFont>()
    }

    fun getFontForLanguage(language:String): String {
        if (language.contains("Chinese")) return chineseFont
        else return "Arial"
    }

    val chineseFont = "WenQuanYiMicroHei"

    fun getCharsForFont(font: String): String {
        val defaultText = "ABCČĆDĐEFGHIJKLMNOPQRSŠTUVWXYZŽaäàâăbcčćçdđeéfghiîjklmnoöpqrsșštțuüvwxyzž" +
                "АБВГҐДЂЕЁЄЖЗЅИІЇЙЈКЛЉМНЊОПРСТЋУЎФХЦЧЏШЩЪЫЬЭЮЯабвгґдђеёєжзѕиіїйјклљмнњопрстћуўфхцчџшщъыьэюя" +
                "ΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρστυφχψωάßΆέΈέΉίϊΐΊόΌύΰϋΎΫΏÄĂÂÊÉÎÔÖƠƯÜäăâêôöơưüáéèíóú1234567890" +
                "‘?’'“!”(%)[#]{@}/&\\<-+÷×=>®©\$€£¥¢:;,.*|"
        if (font == "Arial") return defaultText
        if (font == chineseFont) {
            val constants = "‘?’'“!”(%)[#]{@}/&\\<-+÷×=>®©\$€£¥¢:;,.*|"
            val charSet = HashSet<Char>()
            charSet.addAll(constants.asIterable())
            charSet.addAll(defaultText.asIterable())
            for (entry in GameBasics.Translations.entries) {
                for (lang in entry.value) {
                    if (lang.key.contains("Chinese")) charSet.addAll(lang.value.asIterable())
                }
            }
            return charSet.joinToString()
        }
        return ""
    }

    fun download(link: String, path: String) {
        val input = URL(link).openStream()
        val output = FileOutputStream(Gdx.files.local(path).file())
        input.use {
            output.use {
                input.copyTo(output)
            }
        }
    }

    fun containsFont(font:String): Boolean {
        if (Gdx.files.internal("skin/$font.ttf").exists())
            return true
        if (Gdx.files.local("fonts/$font.ttf").exists())
            return true

        return false
    }

    fun downloadFontForLanguage(language:String){
        val font = getFontForLanguage(language)
        if(containsFont(language)) return

        if (!Gdx.files.local("fonts").exists())
            Gdx.files.local("fonts").mkdirs()

        val localPath = "fonts/$font.ttf"
        if (font == chineseFont)
            download("https://github.com/layerssss/wqy/raw/gh-pages/fonts/WenQuanYiMicroHei.ttf", localPath)//This font is licensed under Apache2.0 or GPLv3
    }

    fun getFont(size: Int): BitmapFont {
        val language = UnCivGame.Current.settings.language
        val fontForLanguage = getFontForLanguage(language)
        val keyForFont = "$fontForLanguage $size"
        if (fontCache.containsKey(keyForFont)) return fontCache[keyForFont]!!
        val generator: FreeTypeFontGenerator

        if (Gdx.files.internal("skin/$fontForLanguage.ttf").exists())
            generator = FreeTypeFontGenerator(Gdx.files.internal("skin/$fontForLanguage.ttf"))
        else {
            val localPath = "fonts/$fontForLanguage.ttf"
            if(!containsFont(fontForLanguage))  downloadFontForLanguage(language)
            generator = FreeTypeFontGenerator(Gdx.files.local(localPath))
        }

        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
        parameter.size = size
        parameter.minFilter = Texture.TextureFilter.Linear
        parameter.magFilter = Texture.TextureFilter.Linear

        parameter.characters = getCharsForFont(fontForLanguage)

        val font = generator.generateFont(parameter)
        generator.dispose() // don't forget to dispose to avoid memory leaks!
        fontCache[keyForFont] = font
        return font
    }
}
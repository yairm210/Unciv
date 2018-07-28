package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Base64Coder
import com.badlogic.gdx.utils.Json
import com.unciv.UnCivGame
import com.unciv.logic.GameSaver
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.getRandom
import com.unciv.ui.utils.tr
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.text.Charsets.UTF_8


class SaveScreen : PickerScreen() {
    val textField = TextField("", skin)

    init {
        val currentSaves = Table()

        currentSaves.add(Label("Current saves".tr(),skin)).row()
        val saves = GameSaver().getSaves()
        saves.forEach {
            val textButton = TextButton(it, skin)
            textButton.addClickListener {
                textField.text = it
            }
            currentSaves.add(textButton).pad(5f).row()

        }
        topTable.add(currentSaves)


        val newSave = Table()
        val adjectives = listOf("Prancing","Obese","Junior","Senior","Abstract","Discombobulating","Simple","Awkward","Holy",
                "Dangerous","Greasy","Stinky","Purple","Majestic","Incomprehensible","Cardboard","Chocolate","Robot","Ninja",
                "Fluffy","Magical","Invisible")
        val nouns = listOf("Moose","Pigeon","Weasel","Ferret","Onion","Marshmallow","Crocodile","Unicorn",
                "Sandwich","Elephant","Kangaroo","Marmot","Beagle","Dolphin","Fish","Tomato","Duck","Dinosaur")
        val defaultSaveName = adjectives.getRandom()+" "+nouns.getRandom()
        textField.text = defaultSaveName

        newSave.add(Label("Saved game name".tr(),skin)).row()
        newSave.add(textField).width(300f).pad(10f).row()

        val copyJsonButton = TextButton("Copy game info".tr(),skin)
        copyJsonButton.addClickListener {
            val json = Json().toJson(game.gameInfo)
            val base64Gzip = Gzip.encoder(Gzip.compress(json))
            Gdx.app.clipboard.contents =  base64Gzip
        }
        newSave.add(copyJsonButton)

        topTable.add(newSave)
        topTable.pack()

        rightSideButton.setText("Save game".tr())
        rightSideButton.addClickListener {
            GameSaver().saveGame(UnCivGame.Current.gameInfo, textField.text)
            UnCivGame.Current.setWorldScreen()
        }
        rightSideButton.enable()
    }

}


object Gzip {

    fun compress(data: String): ByteArray {
        val bos = ByteArrayOutputStream(data.length)
        val gzip = GZIPOutputStream(bos)
        gzip.write(data.toByteArray())
        gzip.close()
        val compressed = bos.toByteArray()
        bos.close()
        return compressed
    }

    fun decompress(compressed: ByteArray): String {
        val bis = ByteArrayInputStream(compressed)
        val gis = GZIPInputStream(bis)
        val br = BufferedReader(InputStreamReader(gis, "UTF-8"))
        val sb = StringBuilder()
        var line: String? = br.readLine()
        while (line != null) {
            sb.append(line)
            line = br.readLine()
        }
        br.close()
        gis.close()
        bis.close()
        return sb.toString()
    }


    fun gzip(content: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).bufferedWriter(UTF_8).use { it.write(content) }
        return bos.toByteArray()
    }

    fun ungzip(content: ByteArray): String =
            GZIPInputStream(content.inputStream()).bufferedReader(UTF_8).use { it.readText() }

    fun encoder(bytes:ByteArray): String{
        return String(Base64Coder.encode(bytes))
    }

    fun decoder(base64Str: String): ByteArray{
        return Base64Coder.decode(base64Str)
    }
}
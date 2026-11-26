package com.unciv.json

import com.badlogic.gdx.files.FileHandle
import org.junit.Assert
import org.junit.Test
import java.io.File

class UncivJsonTests {
    class Item {
        var name = ""
        var cost = 0
    }

    private fun tempJson(content: String): FileHandle {
        val file = File.createTempFile("unciv-json-test", ".json")
        file.deleteOnExit()
        file.writeText(content, Charsets.UTF_8)
        return FileHandle(file)
    }

    private fun tempJsonWithBom(content: String): FileHandle {
        val file = File.createTempFile("unciv-json-test", ".json")
        file.deleteOnExit()
        // Write explicit UTF-8 BOM bytes (EF BB BF) followed by the content.
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        file.writeBytes(bom + content.toByteArray(Charsets.UTF_8))
        return FileHandle(file)
    }

    @Test
    fun `reads a json file`() {
        val item = json().fromJsonFile(Item::class.java, tempJson("""{"name":"Monument","cost":40}"""))
        Assert.assertEquals("Monument", item.name)
        Assert.assertEquals(40, item.cost)
    }

    /** Windows editors such as Notepad can save UTF-8 with a byte order mark, as mod files are often written. */
    @Test
    fun `reads a json file that starts with a byte order mark`() {
        val item = json().fromJsonFile(Item::class.java, tempJsonWithBom("""{"name":"Monument","cost":40}"""))
        Assert.assertEquals("Monument", item.name)
        Assert.assertEquals(40, item.cost)
    }

    @Test
    fun `reads a json array file that starts with a byte order mark`() {
        val items = json().fromJsonFile(Array<Item>::class.java, tempJsonWithBom("""[{"name":"Monument"},{"name":"Shrine"}]"""))
        Assert.assertEquals(listOf("Monument", "Shrine"), items.map { it.name })
    }
}

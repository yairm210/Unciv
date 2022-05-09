package com.unciv.json

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.SerializationException
import kotlin.math.max


class Vector2Serializer : Json.Serializer<Vector2> {
    companion object {
        private const val outputFormat = '0'  // {=object, [=array, ~=compact, 0=base64

        private fun Int.toBase64Char() = Char(when(this) {
            in 0..25 -> 65 + this  // 'A'..'Z'
            in 26..51 -> 97 - 26 + this  // 'a'..'z'
            in 52..61 -> 48 - 52 + this  // '0'..'9'
            62 -> 64  // '@', bash flavour
            63 -> 95  // '_', bash flavour
            else -> 61  // '='
        })
        private fun Int.toBase64String(): String {
            val sb = StringBuilder()
            var residue = this
            while (true) {
                sb.append(residue.mod(64).toBase64Char())
                residue /= 64
                if (residue == 0) return sb.toString()
            }
        }
        private fun Int.charsNeeded(): Int = when(this) {
            in -32..31 -> 1
            in -2048..2047 -> 2
            in -131072..131071 -> 3
            else -> 4 // actually -8388608..8388608 but who's gonna exceed that
        }
        private fun Int.charsNeededToOffset() = when(this) {
            1 -> -32
            2 -> -2048
            3 -> -131072
            else -> -8388608
        }
        private fun Vector2.toBase64(): String {
            val x = x.toInt()
            val y = y.toInt()
            if (x in -4..3 && y in -4..3)
                return ((x + 4) + 8 * (y + 4)).toBase64Char().toString()
            val xChars = x.charsNeeded()
            val yChars = y.charsNeeded()
            if (xChars == 1 && yChars == 1)
                return String(charArrayOf((x + 32).toBase64Char(), (y + 32).toBase64Char()))
            val charsPerCoord = max(xChars, yChars)
            val offset = charsPerCoord.charsNeededToOffset()
            return (x - offset).toBase64String().padEnd(charsPerCoord, 'A') +
                    (y - offset).toBase64String().padEnd(charsPerCoord, '=')
        }

        private fun Char.parseBase64(): Int = when(this) {
            in 'A'..'Z' ->  this - 'A'
            in 'a'..'z' ->  this - 'a' + 26
            in '0'..'9' -> this - '0' + 52
            '@' -> 62
            '_' -> 63
            '=' -> 0
            else -> throw SerializationException("Invalid Base64 string for a Vector2")
        }
        private fun String.parseBase64(): Int {
            var sum = 0
            for (index in this.indices.reversed()) {
                sum = sum * 64 + get(index).parseBase64()
            }
            return sum
        }
        private fun vectorFromInts(x: Int, y: Int) = Vector2(x.toFloat(), y.toFloat())
        private fun String.base64ToVector2(): Vector2 {
            if (length == 0) return Vector2.Zero
            if (length == 1) {
                val z = get(0).parseBase64()
                return vectorFromInts(z % 8 - 4, z / 8 - 4)
            }
            if (length == 2)
                return vectorFromInts(get(0).parseBase64() - 32, get(1).parseBase64() - 32)
            val charsPerCoord = (length + 1) / 2
            val offset = charsPerCoord.charsNeededToOffset()
            val x = this.take(charsPerCoord).parseBase64() + offset
            val y = this.drop(charsPerCoord).parseBase64() + offset
            return vectorFromInts(x, y)
        }
    }

    override fun write(json: Json, vector: Vector2?, knownType: Class<*>?) {
        when (outputFormat) {
            '{' -> {
                json.writeObjectStart()
                if (vector != null) {
                    if (vector.x != 0f)
                        json.writeValue("x", vector.x.toInt())
                    if (vector.y != 0f)
                        json.writeValue("y", vector.y.toInt())
                }
                json.writeObjectEnd()
            }
            '[' -> {
                json.writeArrayStart()
                if (vector != null) {
                    json.writeValue(vector.x.toInt())
                    if (vector.y != 0f)
                        json.writeValue(vector.y.toInt())
                }
                json.writeArrayEnd()
            }
            '~' -> {
                if (vector != null) {
                    json.writeValue("${vector.x.toInt()}~${vector.y.toInt()}")
                }
            }
            '0' ->
                if (vector != null) {
                    json.writeValue(vector.toBase64())
                }
        }
    }

    override fun read(json: Json, jsonData: JsonValue?, type: Class<*>?): Vector2 {
        fun JsonValue.countXY() = (if (has("x")) 1 else 0) + (if (has("y")) 1 else 0)
        return when {
            jsonData == null ->
                Vector2(Vector2.Zero)
            // Format {x:Float,y:Float}
            jsonData.isObject && jsonData.size == jsonData.countXY() -> {
                val x = jsonData["x"]?.asFloat() ?: 0f
                val y = jsonData["y"]?.asFloat() ?: 0f
                Vector2(x, y)
            }
            // Format [Float,Float]
            jsonData.isArray && jsonData.size <= 2 -> {
                val x = jsonData[0]?.asFloat() ?: 0f
                val y = jsonData[1]?.asFloat() ?: 0f
                Vector2(x, y)
            }
            // Format Int~Int
            jsonData.isString && '~' in jsonData.asString() -> {
                val parts = jsonData.asString().split('~', limit = 2)
                val x = parts[0].toFloatOrNull() ?: 0f
                val y = parts[1].toFloatOrNull() ?: 0f
                Vector2(x, y)
            }
            jsonData.isString || jsonData.isLong ->
                jsonData.asString().base64ToVector2()
            else ->
                throw SerializationException("Invalid format for Vector2 data")
        }
    }

}
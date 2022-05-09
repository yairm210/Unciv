package com.unciv.json

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.SerializationException
import kotlin.math.max


class Vector2Serializer : Json.Serializer<Vector2> {
    companion object {
        // Helpers for base64, both encoding and decoding. Note a nibble is 3 bits here instead of the usual 4.
        // For operator precedence see https://kotlinlang.org/docs/reference/grammar.html#expressions:
        // bit shifts are omitted from officially documented precedence, therefore we use brackets galore.
        private fun Int.nibblesNeeded(): Int {
            var nibbles = 0
            while (nibbles < 11) {
                val min = if (nibbles == 0) 0 else -1 shl ((nibbles * 3) - 1)
                val max = if (nibbles == 0) 0 else (1 shl ((nibbles * 3) - 1)) - 1
                if (this in min..max) return nibbles
                nibbles++
            }
            return 11
        }
        private fun Int.nibblesToOffset(): Int {
            if (this <= 0) return 0
            return -1 shl (this * 3 - 1)
        }
        private fun Int.nibblesToDivisor(): Int {
            return 1 shl (this * 3)
        }

        // encoding base64
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
        private fun Vector2.toBase64(): String {
            val x = x.toInt()
            val y = y.toInt()
            val xNibbles = x.nibblesNeeded()
            val yNibbles = y.nibblesNeeded()
            if (xNibbles <= 1 && yNibbles <= 1)  // this optimization also makes Vector2.Zero take 1 char instead of 0
                return ((x + 4) + 8 * (y + 4)).toBase64Char().toString()
            if (xNibbles <= 2 && yNibbles <= 2)  // optimization only
                return String(charArrayOf((x + 32).toBase64Char(), (y + 32).toBase64Char()))
            val nibblesPerCoord = max(xNibbles, yNibbles)
            val offset = nibblesPerCoord.nibblesToOffset()
            val charsPerCoord = nibblesPerCoord / 2  // when odd the extra char carrying bits from both coords goes separately
            if (nibblesPerCoord % 2 == 0)
                // Easy case: even number of chars, half for x, half for y
                return (x - offset).toBase64String().padEnd(charsPerCoord, 'A') +
                        (y - offset).toBase64String()
            // Not so easy case: odd number of chars, the middle one carrying one nibble from x and y each
            val divisor = (nibblesPerCoord - 1).nibblesToDivisor()  // don't count the nibble sharing a char
            val xFirstPart = (x - offset) % divisor
            val middlePart = ((x - offset) / divisor) + 8 * ((y - offset) % 8)
            val yLastPart = (y - offset) / 8
            return xFirstPart.toBase64String().padEnd(charsPerCoord, 'A') +
                    middlePart.toBase64Char() +
                    yLastPart.toBase64String()
        }

        // decoding base64
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
            val nibblesPerCoord = length  // just to make it clear
            if (nibblesPerCoord == 0) return Vector2.Zero
            // Two optimizations only, should give the same result without
            if (nibblesPerCoord == 1) {
                val z = get(0).parseBase64()
                return vectorFromInts(z % 8 - 4, z / 8 - 4)
            }
            if (nibblesPerCoord == 2)
                return vectorFromInts(get(0).parseBase64() - 32, get(1).parseBase64() - 32)
            val offset = nibblesPerCoord.nibblesToOffset()
            val charsPerCoord = nibblesPerCoord / 2  // again not counting the shared char
            if (nibblesPerCoord % 2 == 0) {
                val x = take(charsPerCoord).parseBase64() + offset
                val y = drop(charsPerCoord).parseBase64() + offset
                return vectorFromInts(x, y)
            }
            val divisor = (nibblesPerCoord - 1).nibblesToDivisor()  // don't count the nibble sharing a char
            val xFirstPart = take(charsPerCoord).parseBase64()
            val middlePart = get(charsPerCoord).parseBase64()
            val yLastPart = drop(charsPerCoord + 1).parseBase64()
            val x = (xFirstPart + (middlePart % 8) * divisor) + offset
            val y = (yLastPart * 8 + (middlePart / 8)) + offset
            return vectorFromInts(x, y)
        }
    }

    override fun write(json: Json, vector: Vector2?, knownType: Class<*>?) {
        if (vector != null) {
            json.writeValue(vector.toBase64())
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
            // base64 is just a string to Json, but if it's only decimal digits isLong takes precedence
            jsonData.isString || jsonData.isLong ->
                jsonData.asString().base64ToVector2()
            else ->
                throw SerializationException("Invalid format for Vector2 data")
        }
    }
}

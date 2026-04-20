package com.unciv.utils

/**
 * Minimal SHA-1 implementation so checksum behavior stays identical across JVM and TeaVM.
 */
object Sha1 {
    fun digest(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8L
        val paddedSize = ((input.size + 9 + 63) / 64) * 64
        val padded = ByteArray(paddedSize)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        for (index in 0 until 8) {
            padded[paddedSize - 1 - index] = (bitLength ushr (index * 8)).toByte()
        }

        var h0 = 0x67452301
        var h1 = 0xefcdab89.toInt()
        var h2 = 0x98badcfe.toInt()
        var h3 = 0x10325476
        var h4 = 0xc3d2e1f0.toInt()
        val words = IntArray(80)

        var offset = 0
        while (offset < padded.size) {
            for (wordIndex in 0 until 16) {
                val base = offset + wordIndex * 4
                words[wordIndex] =
                    ((padded[base].toInt() and 0xff) shl 24) or
                    ((padded[base + 1].toInt() and 0xff) shl 16) or
                    ((padded[base + 2].toInt() and 0xff) shl 8) or
                    (padded[base + 3].toInt() and 0xff)
            }
            for (wordIndex in 16 until words.size) {
                words[wordIndex] = Integer.rotateLeft(
                    words[wordIndex - 3] xor
                        words[wordIndex - 8] xor
                        words[wordIndex - 14] xor
                        words[wordIndex - 16],
                    1
                )
            }

            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4

            for (wordIndex in words.indices) {
                val (f, k) = when (wordIndex) {
                    in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5a827999
                    in 20..39 -> (b xor c xor d) to 0x6ed9eba1
                    in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8f1bbcdc.toInt()
                    else -> (b xor c xor d) to 0xca62c1d6.toInt()
                }

                val temp = Integer.rotateLeft(a, 5) + f + e + k + words[wordIndex]
                e = d
                d = c
                c = Integer.rotateLeft(b, 30)
                b = a
                a = temp
            }

            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            offset += 64
        }

        return ByteArray(20).also {
            writeInt(it, 0, h0)
            writeInt(it, 4, h1)
            writeInt(it, 8, h2)
            writeInt(it, 12, h3)
            writeInt(it, 16, h4)
        }
    }

    private fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }
}

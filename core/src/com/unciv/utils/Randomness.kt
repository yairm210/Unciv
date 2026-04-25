package com.unciv.utils

import yairm210.purity.annotations.Pure
import kotlin.random.Random

@Pure
fun Int.withHash(hash: Int) = this * 31 + hash

@Pure
fun hashOf(vararg hashes: Int): Int {
    var finalHash = 0
    hashes.forEach { finalHash = finalHash.withHash(it) } 
    return finalHash
}


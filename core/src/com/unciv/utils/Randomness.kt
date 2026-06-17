package com.unciv.utils

import yairm210.purity.annotations.Pure
import java.security.SecureRandom
import java.util.UUID
import kotlin.random.Random
import kotlin.random.asKotlinRandom

@Pure
fun Int.withHash(hash: Int) = this * 31 + hash

@Pure
fun hashOf(vararg hashes: Int): Int {
    var finalHash = 0
    hashes.forEach { finalHash = finalHash.withHash(it) } 
    return finalHash
}

fun pseudoRandomUuid(rng: Random): UUID {
    // RFC requires the high 4 bits of the 7th byte to be the _version_ 0x4 (aka random),
    val mostSigBits: Long = (rng.nextLong() and 0xf000L.inv()) or 0x4000L
    // and the 2 most-significant bits of the ninth byte to be the _variant_ 0x8 (aka OSF DCE).
    val leastSigBits: Long = (rng.nextLong() and 0x3fffffffffffffffL) or (1L shl 63)
    return UUID(mostSigBits, leastSigBits)
}
fun pseudoRandomUuid(rng: SecureRandom) = pseudoRandomUuid(rng.asKotlinRandom())



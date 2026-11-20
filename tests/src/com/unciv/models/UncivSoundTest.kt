package com.unciv.models

import kotlin.reflect.full.declaredMemberProperties
import org.junit.Assert.assertEquals
import org.junit.Test

class UncivSoundTest {
    @Test
    fun `built in sound registry matches companion sound properties and order`() {
        val reflectedNames = UncivSound.Companion::class.declaredMemberProperties
            .filter { it.returnType.classifier == UncivSound::class }
            .map { (it.get(UncivSound.Companion) as UncivSound).fileName }

        assertEquals(reflectedNames, UncivSound.builtInSounds.map { it.fileName })
    }
}

package com.unciv.models.gamebasics

import com.unciv.models.stats.INamed

class BasicHelp : ICivilopedia, INamed {
    override lateinit var name: String
    override val description: String = ""
    override fun toString() = name
}

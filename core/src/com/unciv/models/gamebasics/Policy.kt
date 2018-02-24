package com.unciv.models.gamebasics

import com.unciv.models.linq.Linq
import com.unciv.models.stats.INamed

open class Policy : INamed {
    override lateinit var name: String
    @JvmField var description: String? = null
    @JvmField var branch: String? = null
    @JvmField var row: Int = 0
    @JvmField var column: Int = 0
    @JvmField var requires: Linq<String>? = null

}


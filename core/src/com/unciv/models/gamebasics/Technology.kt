package com.unciv.models.gamebasics

import java.util.HashSet

class Technology {
    @JvmField var name: String? = null

    @JvmField var description: String? = null
    @JvmField var cost: Int = 0
    @JvmField var prerequisites = HashSet<String>()

    @JvmField var column: TechColumn? = null // The column that this tech is in the tech tree
    @JvmField var row: Int = 0
}
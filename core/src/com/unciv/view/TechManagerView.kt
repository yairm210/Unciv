package com.unciv.view

import com.unciv.logic.civilization.managers.TechManager
import yairm210.purity.annotations.Readonly

class TechManagerView(private val techManager: TechManager) {
    @Readonly fun isResearched(techName: String): Boolean = techManager.isResearched(techName)
}

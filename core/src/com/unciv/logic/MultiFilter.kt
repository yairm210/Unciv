package com.unciv.logic

object MultiFilter {
    fun multiFilter(input: String, filterFunction: (String)->Boolean,
                    /** Unique validity doesn't check for actual matching */ forUniqueValidityTests: Boolean=false): Boolean {
        if (input.contains("} {"))
            return input.removePrefix("{").removeSuffix("}").split("} {")
                .all{ multiFilter(it, filterFunction, forUniqueValidityTests) }
        if (input.startsWith("non-[") && input.endsWith("]")) {
            val internalResult = multiFilter(input.removePrefix("non-[").removeSuffix("]"), filterFunction)
            return if (forUniqueValidityTests) internalResult else !internalResult
        }
        return filterFunction(input)
    }


}

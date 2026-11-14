package com.unciv.models.translations

/** A completion percentage with exactly one fraction digit (0.0 .. 100.0), stored as tenths.
 *  File format is always "99.9" / "98.0" / "33.7".
 *  The display form rounds fractions **down** except when >= 99%, which are displayed with the fraction as translator incentive.
 *  A single missing translation will always be "99.9" as rounding up would be a lie. */
@JvmInline
value class CompletionPercentage private constructor(private val tenths: Int) : Comparable<CompletionPercentage> {

    /** The whole-percent part, for code that still wants the old integer behavior */
    fun toWholeInt() = tenths / 10

    /** For the percentages file: always one fraction digit, e.g. "100.0", "99.9", "98.0" */
    fun toFileString() = "${tenths / 10}.${tenths % 10}"

    /** For the UI: "100", "99.9", "98" */
    fun toDisplayString() = if (tenths in 990..<1000) toFileString() else (tenths / 10).toString()

    override fun compareTo(other: CompletionPercentage) = tenths.compareTo(other.tenths)
    override fun toString() = toFileString()

    companion object {
        val ZERO = CompletionPercentage(0)
        val COMPLETE = CompletionPercentage(1000)

        /** Truncates (never rounds up), so 100.0 means exactly nothing missing. */
        fun of(translated: Int, total: Int): CompletionPercentage = when {
            total <= 0 || translated >= total -> COMPLETE
            translated <= 0 -> ZERO
            else -> CompletionPercentage((translated * 1000L / total).toInt())
        }

        /** Accepts "99.9", "98.0" and the legacy integer form "98". Extra fraction digits are truncated.
         *  @return null if malformed or out of range */
        fun parse(text: String): CompletionPercentage? {
            val trimmed = text.trim()
            val dot = trimmed.indexOf('.')
            val whole = (if (dot < 0) trimmed else trimmed.substring(0, dot)).toIntOrNull() ?: return null
            val fraction = if (dot < 0) 0 else trimmed.substring(dot + 1).take(1).toIntOrNull() ?: return null
            if (whole < 0 || fraction < 0) return null
            val tenths = whole * 10 + fraction
            return if (tenths > 1000) null else CompletionPercentage(tenths)
        }
    }
}

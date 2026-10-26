package com.unciv.ui.components.tilegroups

// Helpers for building a cheap change-detection hash of everything an update reads. See TileLayer.getUpdateKey
internal fun Long.mix(value: Int): Long = this * 31 + value
internal fun Long.mix(value: Boolean): Long = mix(if (value) 1 else 0)
internal fun Long.mix(value: Any?): Long = mix(value?.hashCode() ?: 0)

package com.unciv.logic.map

enum class NeighborDirection(val clockPosition: Int) {
    TopRight(2),
    BottomRight(4),
    Bottom(6),
    BottomLeft(8),
    TopLeft(10),
    Top(12);

    companion object {
        val byClockPosition = entries.associateBy { it.clockPosition }
    }
}

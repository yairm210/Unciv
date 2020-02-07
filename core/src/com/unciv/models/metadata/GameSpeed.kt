package com.unciv.models.metadata

/** Game speed
 *
 *  @param modifier cost modifier
 *  @param turnLimit turn at which the score victory should be triggered
 * */
enum class GameSpeed(val modifier: Float, val turnLimit: Int) {
    Quick(0.67f, 330),
    Standard(1f, 500),
    Epic(1.5f, 750),
    Marathon(3f, 1500);
}
package com.unciv.models.metadata

const val BASE_GAME_DURATION_TURNS = 500f

/** Game speed
 *
 *  @param modifier cost modifier
 * */
enum class GameSpeed(val modifier: Float) {
    Quick(0.67f),
    Standard(1f),
    Epic(1.5f),
    Marathon(3f);
}
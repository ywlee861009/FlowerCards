package com.flowercards.data

import com.flowercards.domain.engine.GameState

/**
 * Data boundary for future persistence, replay, and saved game support.
 */
interface GameRepository {
    fun save(state: GameState)
    fun load(): GameState?
    fun clear()
}

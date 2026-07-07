package com.flowercards.data

import com.flowercards.domain.engine.GameState

class InMemoryGameRepository : GameRepository {
    private var savedState: GameState? = null

    override fun save(state: GameState) {
        savedState = state
    }

    override fun load(): GameState? = savedState

    override fun clear() {
        savedState = null
    }
}

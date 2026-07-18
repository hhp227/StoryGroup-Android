package kr.hhp227.storygroup.shared.data.storage

import kr.hhp227.storygroup.shared.domain.model.AuthTokens

interface TokenStorage {
    fun load(): AuthTokens?
    fun save(tokens: AuthTokens)
    fun clear()
}

class InMemoryTokenStorage : TokenStorage {
    private var tokens: AuthTokens? = null

    override fun load(): AuthTokens? = tokens

    override fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override fun clear() {
        tokens = null
    }
}

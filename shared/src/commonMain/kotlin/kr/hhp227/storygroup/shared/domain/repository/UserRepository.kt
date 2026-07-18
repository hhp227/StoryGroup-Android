package kr.hhp227.storygroup.shared.domain.repository

import kr.hhp227.storygroup.shared.domain.model.Profile

interface UserRepository {
    suspend fun getMyProfile(): Result<Profile>
}

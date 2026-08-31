package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterPushTokenRequest(val token: String, val platform: String)

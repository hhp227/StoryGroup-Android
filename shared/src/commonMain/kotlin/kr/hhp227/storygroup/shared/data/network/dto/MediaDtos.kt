package kr.hhp227.storygroup.shared.data.network.dto

import kotlinx.serialization.Serializable

// StoryGroup-WebApp /api/images 계약과 1:1 (common/storage/ImageUploadController.kt)

@Serializable
data class UploadedImageResponse(val url: String)

@file:OptIn(ExperimentalForeignApi::class)

package kr.hhp227.storygroup.shared.bridge

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * NSData → ByteArray. 업로드 유스케이스는 ByteArray를 받는데 Swift는 Data를 들고 있어 변환이 필요하다.
 *
 * Swift에서 KotlinByteArray를 만들어 set(index:value:)로 한 바이트씩 넣으면 바이트마다 Swift↔Kotlin
 * 경계를 넘는다 — 프로필 사진 수백 KB에선 티가 안 났지만 10MB 동영상이면 천만 번이라 눈에 띄게 멈춘다.
 * 여기서는 Kotlin/Native 안에서 memcpy 한 번으로 끝낸다.
 */
fun nsDataToByteArray(data: NSData): ByteArray {
    val size = data.length.toInt()
    if (size == 0) return ByteArray(0)

    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
    }
}

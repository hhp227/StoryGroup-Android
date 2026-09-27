package kr.hhp227.storygroup.ui.auth

import java.security.MessageDigest
import java.security.SecureRandom

private val random = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

internal actual fun sha256Digest(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

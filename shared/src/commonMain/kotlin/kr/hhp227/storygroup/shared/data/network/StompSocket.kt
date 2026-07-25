package kr.hhp227.storygroup.shared.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kr.hhp227.storygroup.shared.data.storage.TokenStorage

/** STOMP 세션에서 흘러나오는 저수준 이벤트 — Repository가 도메인 이벤트로 매핑한다 */
internal sealed interface StompSessionEvent {
    data object Connected : StompSessionEvent
    data object Disconnected : StompSessionEvent
    data class Message(val body: String) : StompSessionEvent
}

/**
 * 서버 /ws 엔드포인트용 최소 STOMP 1.2 클라이언트(수신 전용 — 메시지 전송은 REST 계약).
 * - 인증: 핸드셰이크가 아니라 CONNECT 프레임 native 헤더 `Authorization: Bearer`(서버 인터셉터 계약)
 * - 하트비트: 서버 10s/10s 계약 — 10초마다 LF 송신, 30초(3주기) 무수신이면 죽은 연결로 보고 재연결
 * - 재연결: 유실 시 5초 간격 무한 재시도(웹 stompjs reconnectDelay 미러). CONNECTED에 이르렀던
 *   세션이 끊길 때만 Disconnected를 흘려, 화면의 REST 재조회(→토큰 리프레시 부수효과)를 유도한다
 */
internal class StompSocket(
    private val client: HttpClient,
    baseUrl: String,
    private val tokenStorage: TokenStorage
) {
    private val wsUrl = baseUrl.replaceFirst("http", "ws") + "/ws"
    private val host = baseUrl.substringAfter("://").substringBefore('/')

    fun subscribe(destination: String): Flow<StompSessionEvent> = flow {
        while (true) {
            var reachedConnected = false
            try {
                client.webSocket(urlString = wsUrl, request = {
                    // 장수명 연결 — ApiClient의 30초 타임아웃(콜드 스타트 대비)이 세션을 끊지 않게 해제
                    timeout {
                        requestTimeoutMillis = Long.MAX_VALUE
                        socketTimeoutMillis = Long.MAX_VALUE
                    }
                }) {
                    val accessToken = tokenStorage.load()?.accessToken
                        ?: throw IllegalStateException("세션 없음")
                    sendFrame(
                        "CONNECT",
                        "accept-version" to "1.2",
                        "host" to host,
                        "Authorization" to "Bearer $accessToken",
                        "heart-beat" to "$HEARTBEAT_MS,$HEARTBEAT_MS"
                    )
                    awaitConnected()
                    sendFrame("SUBSCRIBE", "id" to "sub-0", "destination" to destination)
                    reachedConnected = true
                    emit(StompSessionEvent.Connected)

                    val heartbeatJob = launch {
                        while (true) {
                            delay(HEARTBEAT_MS)
                            send(Frame.Text("\n"))
                        }
                    }
                    try {
                        while (true) {
                            val stompFrame = receiveStompFrame(WATCHDOG_MS) ?: continue
                            when (stompFrame.command) {
                                "MESSAGE" -> emit(StompSessionEvent.Message(stompFrame.body))
                                "ERROR" -> throw IllegalStateException(stompFrame.headers["message"] ?: "STOMP 오류")
                                else -> Unit
                            }
                        }
                    } finally {
                        heartbeatJob.cancel()
                    }
                }
            } catch (_: TimeoutCancellationException) {
                // 하트비트 침묵 — 죽은 연결로 간주하고 아래에서 재연결
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 연결 실패/유실 — 아래에서 재연결
            }
            currentCoroutineContext().ensureActive()
            if (reachedConnected) emit(StompSessionEvent.Disconnected)
            delay(RECONNECT_DELAY_MS)
        }
    }

    /** CONNECTED를 기다린다 — ERROR(토큰 만료 등)면 예외로 세션을 접는다 */
    private suspend fun DefaultClientWebSocketSession.awaitConnected() {
        while (true) {
            val stompFrame = receiveStompFrame(CONNECT_TIMEOUT_MS) ?: continue
            when (stompFrame.command) {
                "CONNECTED" -> return
                "ERROR" -> throw IllegalStateException(stompFrame.headers["message"] ?: "STOMP 연결 거부")
                else -> Unit
            }
        }
    }

    /** 다음 STOMP 프레임 수신 — 하트비트(공백 프레임)면 null */
    private suspend fun DefaultClientWebSocketSession.receiveStompFrame(timeoutMs: Long): StompFrame? {
        val frame = withTimeout(timeoutMs) { incoming.receive() }
        val text = when (frame) {
            is Frame.Text -> frame.readText()
            is Frame.Binary -> frame.readBytes().decodeToString()
            else -> return null
        }
        if (text.isBlank()) return null
        return parseStompFrame(text)
    }

    private suspend fun DefaultClientWebSocketSession.sendFrame(
        command: String,
        vararg headers: Pair<String, String>
    ) {
        val raw = buildString {
            append(command).append('\n')
            headers.forEach { (key, value) -> append(key).append(':').append(value).append('\n') }
            append('\n')
            append('\u0000')
        }
        send(Frame.Text(raw))
    }

    private companion object {
        const val HEARTBEAT_MS = 10_000L
        const val WATCHDOG_MS = 30_000L
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val RECONNECT_DELAY_MS = 5_000L
    }
}

internal class StompFrame(
    val command: String,
    val headers: Map<String, String>,
    val body: String
)

/**
 * STOMP 프레임 파싱 — 서버(Spring SimpleBroker)는 프레임당 웹소켓 메시지 1개로 보낸다.
 * 헤더 이스케이프(\n, \c)는 우리 페이로드에 등장하지 않아 처리하지 않는다.
 */
internal fun parseStompFrame(raw: String): StompFrame? {
    // 하트비트 개행이 프레임 앞에 붙어 올 수 있어 선행 개행은 걷어낸다
    val normalized = raw.replace("\r\n", "\n").trimStart('\n')
    val headerEnd = normalized.indexOf("\n\n")
    if (headerEnd < 0) return null
    val lines = normalized.substring(0, headerEnd).split('\n')
    val command = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val headers = lines.drop(1).mapNotNull { line ->
        val separator = line.indexOf(':')
        if (separator < 0) null else line.substring(0, separator) to line.substring(separator + 1)
    }.toMap()
    val body = normalized.substring(headerEnd + 2).substringBefore('\u0000')
    return StompFrame(command, headers, body)
}

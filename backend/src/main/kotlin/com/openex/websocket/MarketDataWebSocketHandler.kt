package com.openex.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

data class WsEnvelope(val type: String, val data: Any)

@Component
class MarketDataWebSocketHandler(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // Clients only send lightweight pings; nothing to act on.
    }

    fun broadcast(type: String, data: Any) {
        val payload = objectMapper.writeValueAsString(WsEnvelope(type, data))
        val message = TextMessage(payload)
        sessions.forEach { s ->
            if (s.isOpen) {
                try {
                    synchronized(s) { s.sendMessage(message) }
                } catch (ex: Exception) {
                    // drop broken sessions silently; afterConnectionClosed will clean up
                }
            }
        }
    }

    /** Sends the current snapshot to a single newly-connected session. */
    fun sendTo(session: WebSocketSession, type: String, data: Any) {
        val payload = objectMapper.writeValueAsString(WsEnvelope(type, data))
        synchronized(session) { session.sendMessage(TextMessage(payload)) }
    }
}

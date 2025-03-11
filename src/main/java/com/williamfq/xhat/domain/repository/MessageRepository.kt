package com.williamfq.xhat.domain.repository

import com.williamfq.domain.model.MessageStatus
import com.williamfq.domain.model.MessageType
import com.williamfq.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor() {
    private val mutex = Mutex()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: Flow<List<Message>> = _messages.asStateFlow()

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        chatId: String,
        content: String,
        senderName: String,
        type: MessageType = MessageType.TEXT
    ): Message {
        val message = Message(
            id = generateMessageId(),
            chatId = chatId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            senderName = senderName,
            type = type,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT
        )

        mutex.withLock {
            _messages.value = _messages.value + message
        }

        return message
    }

    suspend fun getMessagesBetweenUsers(
        userId1: String,
        userId2: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<Message> = mutex.withLock {
        _messages.value
            .filter { msg ->
                (msg.senderId == userId1 && msg.receiverId == userId2) ||
                        (msg.senderId == userId2 && msg.receiverId == userId1)
            }
            .sortedByDescending { it.timestamp }
            .drop(offset)
            .take(limit)
    }

    suspend fun markMessageAsRead(messageId: String) {
        mutex.withLock {
            _messages.value = _messages.value.map { message ->
                if (message.id == messageId) {
                    message.copy(status = MessageStatus.READ)
                } else {
                    message
                }
            }
        }
    }

    suspend fun deleteMessage(messageId: String) {
        mutex.withLock {
            _messages.value = _messages.value.filterNot { it.id == messageId }
        }
    }

    private fun generateMessageId(): String = "msg_${System.currentTimeMillis()}"
}
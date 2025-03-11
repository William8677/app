/*
 * Updated: 2025-01-27 23:59:18
 * Author: William8677
 */

package com.williamfq.xhat.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.williamfq.domain.model.ChatMessage
import com.williamfq.xhat.domain.model.chat.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatroomRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    fun getChatRooms(filter: ChatRoomFilter? = null): Flow<List<ChatRoom>> = callbackFlow {
        var query = db.collection("chatRooms")
            .orderBy("memberCount", Query.Direction.DESCENDING)

        // Aplicar filtros
        filter?.let {
            if (it.type != null) {
                query = query.whereEqualTo("type", it.type)
            }
            if (it.category != null) {
                query = query.whereEqualTo("category", it.category)
            }
            if (it.country != null) {
                query = query.whereEqualTo("location.country", it.country)
            }
            if (it.language != null) {
                query = query.whereEqualTo("language", it.language)
            }
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val rooms = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(ChatRoom::class.java)
            } ?: emptyList()

            trySend(rooms)
        }

        awaitClose { subscription.remove() }
    }

    fun getChatMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        val subscription = db.collection("chatRooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)
                } ?: emptyList()

                trySend(messages)
            }

        awaitClose { subscription.remove() }
    }

    suspend fun createSystemRooms() {
        // Crear salas de sistema si no existen
        val systemRooms = listOf(
            ChatRoom(
                name = "Sala Global",
                description = "Sala de chat global para todos los usuarios",
                type = ChatRoomType.SYSTEM_LOCATION,
                category = ChatRoomCategory.GENERAL
            ),
            ChatRoom(
                name = "Sala de Amistad",
                description = "Conoce nuevos amigos de todo el mundo",
                type = ChatRoomType.SYSTEM_FRIENDSHIP,
                category = ChatRoomCategory.FRIENDSHIP
            )
        )

        systemRooms.forEach { room ->
            val existingRoom = db.collection("chatRooms")
                .whereEqualTo("name", room.name)
                .whereEqualTo("type", room.type)
                .get()
                .await()

            if (existingRoom.isEmpty) {
                db.collection("chatRooms").add(room).await()
            }
        }
    }

    suspend fun createLocationBasedRoom(location: ChatRoomLocation) {
        // Crear salas basadas en ubicación
        val locationRooms = listOf(
            ChatRoom(
                name = "${location.country} Chat",
                description = "Sala de chat para ${location.country}",
                type = ChatRoomType.SYSTEM_LOCATION,
                location = location,
                category = ChatRoomCategory.GENERAL
            ),
            ChatRoom(
                name = "${location.region} Chat",
                description = "Sala de chat para ${location.region}",
                type = ChatRoomType.SYSTEM_LOCATION,
                location = location,
                category = ChatRoomCategory.GENERAL
            ),
            ChatRoom(
                name = "${location.city} Chat",
                description = "Sala de chat para ${location.city}",
                type = ChatRoomType.SYSTEM_LOCATION,
                location = location,
                category = ChatRoomCategory.GENERAL
            )
        )

        locationRooms.forEach { room ->
            val existingRoom = db.collection("chatRooms")
                .whereEqualTo("name", room.name)
                .whereEqualTo("type", room.type)
                .get()
                .await()

            if (existingRoom.isEmpty) {
                db.collection("chatRooms").add(room).await()
            }
        }
    }

    suspend fun createUserRoom(room: ChatRoom): String {
        val docRef = db.collection("chatRooms").add(room).await()
        return docRef.id
    }

    suspend fun sendMessage(message: ChatMessage) {
        db.collection("chatRooms")
            .document(message.roomId)
            .collection("messages")
            .add(message)
            .await()
    }

    suspend fun joinRoom(roomId: String, member: ChatRoomMember) {
        db.collection("chatRooms")
            .document(roomId)
            .collection("members")
            .document(member.userId)
            .set(member)
            .await()

        // Actualizar contador de miembros usando FieldValue.increment
        db.collection("chatRooms")
            .document(roomId)
            .update("memberCount", FieldValue.increment(1))
            .await()
    }

    suspend fun leaveRoom(roomId: String, userId: String) {
        db.collection("chatRooms")
            .document(roomId)
            .collection("members")
            .document(userId)
            .delete()
            .await()

        // Actualizar contador de miembros usando FieldValue.increment con valor negativo
        db.collection("chatRooms")
            .document(roomId)
            .update("memberCount", FieldValue.increment(-1))
            .await()
    }
}

data class ChatRoomFilter(
    val type: ChatRoomType? = null,
    val category: ChatRoomCategory? = null,
    val country: String? = null,
    val region: String? = null,
    val city: String? = null,
    val language: String? = null
)
package com.williamfq.xhat.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.williamfq.xhat.domain.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    fun getChannels(filter: ChannelFilter = ChannelFilter()): Flow<List<Channel>> = callbackFlow {
        var query = db.collection("channels")
            .orderBy("stats.subscribersCount", Query.Direction.DESCENDING)

        // Aplicar filtros
        filter.category?.let {
            query = query.whereEqualTo("category", it)
        }
        filter.language?.let {
            query = query.whereEqualTo("settings.language", it)
        }
        if (filter.verified) {
            query = query.whereEqualTo("isVerified", true)
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val channels = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Channel::class.java)
            } ?: emptyList()

            trySend(channels)
        }

        awaitClose { subscription.remove() }
    }

    fun getChannelPosts(
        channelId: String,
        limit: Long = 50
    ): Flow<List<ChannelPost>> = callbackFlow {
        val subscription = db.collection("channels")
            .document(channelId)
            .collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val posts = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChannelPost::class.java)
                } ?: emptyList()

                trySend(posts)
            }

        awaitClose { subscription.remove() }
    }

    // Alias para que el ViewModel pueda llamar a getPosts(channelId)
    fun getPosts(channelId: String, limit: Long = 50): Flow<List<ChannelPost>> {
        return getChannelPosts(channelId, limit)
    }

    suspend fun createChannel(channel: Channel) {
        db.collection("channels")
            .document(channel.id)
            .set(channel)
    }

    suspend fun createPost(channelId: String, post: ChannelPost) {
        db.collection("channels")
            .document(channelId)
            .collection("posts")
            .add(post)
    }

    suspend fun updateChannelStats(channelId: String, stats: ChannelStats) {
        db.collection("channels")
            .document(channelId)
            .update("stats", stats)
    }

    suspend fun subscribeToChannel(subscription: ChannelSubscription) {
        db.collection("channelSubscriptions")
            .add(subscription)
    }

    suspend fun unsubscribeFromChannel(channelId: String, userId: String) {
        db.collection("channelSubscriptions")
            .whereEqualTo("channelId", channelId)
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    document.reference.delete()
                }
            }
    }

    suspend fun addReactionToPost(
        channelId: String,
        postId: String,
        emoji: String
    ) {
        db.collection("channels")
            .document(channelId)
            .collection("posts")
            .document(postId)
            .update("reactions.$emoji", FieldValue.increment(1))
    }

    // Función para eliminar una publicación.
    suspend fun deletePost(channelId: String, postId: String) {
        db.collection("channels")
            .document(channelId)
            .collection("posts")
            .document(postId)
            .delete()
    }

    // Función para fijar (pin) una publicación.
    suspend fun pinPost(channelId: String, postId: String) {
        db.collection("channels")
            .document(channelId)
            .collection("posts")
            .document(postId)
            .update("isPinned", true)
    }
}

data class ChannelFilter(
    val category: ChannelCategory? = null,
    val language: String? = null,
    val verified: Boolean = false,
    val searchQuery: String = ""
)

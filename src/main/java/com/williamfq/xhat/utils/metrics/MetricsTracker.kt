package com.williamfq.xhat.utils.metrics

import com.williamfq.domain.model.ReactionType

interface StoryMetricsTracker {
    suspend fun initializeTracking(userId: String)
    suspend fun trackStoryView(storyId: Int, viewDuration: Long)
    suspend fun trackEngagement(storyId: Int, engagementType: EngagementType)
    suspend fun trackCompletion(storyId: Int)
    suspend fun generateReport(storyId: Int): StoryMetricsReport
}

enum class EngagementType {
    VIEW,
    REACTION,
    COMMENT,
    SHARE,
    SAVE,
    POLL_VOTE,
    SWIPE_UP,
    AR_INTERACTION
}

data class StoryMetricsReport(
    val viewCount: Int,
    val uniqueViewers: Int,
    val averageViewDuration: Double,
    val completionRate: Double,
    val engagementRate: Double,
    val topReactions: Map<ReactionType, Int>,
    val viewerRetention: List<Double>,
    val peakViewingTimes: List<Long>
)
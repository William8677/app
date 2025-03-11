package com.williamfq.xhat.ui.stories.viewmodel

import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.williamfq.domain.model.*
import com.williamfq.domain.repository.StoryRepository
import com.williamfq.domain.repository.UserRepository
import com.williamfq.xhat.domain.repository.MessageRepository
import com.williamfq.xhat.ads.manager.NativeStoryAdManager
import com.williamfq.xhat.media.download.MediaDownloadManager
import com.williamfq.xhat.utils.share.ShareManager
import com.williamfq.xhat.utils.analytics.AnalyticsManager
import com.williamfq.xhat.utils.logging.LoggerInterface
import com.williamfq.xhat.utils.logging.LogLevel
import com.williamfq.xhat.core.encryption.E2EEncryption
import com.williamfq.xhat.core.compression.MediaCompressor
import com.williamfq.xhat.core.ads.AdType
import com.williamfq.xhat.ui.stories.components.StoryEffect
import com.williamfq.xhat.ui.stories.model.StoryMetrics
import com.williamfq.xhat.utils.metrics.StoryMetricsTracker
import com.williamfq.xhat.utils.metrics.EngagementType
import com.williamfq.xhat.ui.stories.model.CompletionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class StoriesViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val shareManager: ShareManager,
    private val mediaDownloadManager: MediaDownloadManager,
    private val mediaCompressor: MediaCompressor,
    private val e2eEncryption: E2EEncryption,
    private val nativeStoryAdManager: NativeStoryAdManager,
    private val analyticsManager: AnalyticsManager,
    private val metricsTracker: StoryMetricsTracker,
    private val logger: LoggerInterface
) : ViewModel() {

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _stories = MutableStateFlow<List<Story>>(emptyList())
    val stories: StateFlow<List<Story>> = _stories.asStateFlow()

    private val _selectedStory = MutableStateFlow<Story?>(null)
    val selectedStory: StateFlow<Story?> = _selectedStory.asStateFlow()

    private val _storyMetrics = MutableStateFlow<Map<Int, StoryMetrics>>(emptyMap())
    val storyMetrics: StateFlow<Map<Int, StoryMetrics>> = _storyMetrics.asStateFlow()

    private val _activeStoryEffects = MutableStateFlow<List<StoryEffect>>(emptyList())
    val activeStoryEffects: StateFlow<List<StoryEffect>> = _activeStoryEffects.asStateFlow()

    private val _replyChains = MutableStateFlow<Map<Int, List<StoryReply>>>(emptyMap())
    val replyChains: StateFlow<Map<Int, List<StoryReply>>> = _replyChains.asStateFlow()

    private val _currentStoryIndex = MutableStateFlow(0)
    val currentStoryIndex: StateFlow<Int> = _currentStoryIndex

    private val _showingAd = MutableStateFlow(false)
    val showingAd: StateFlow<Boolean> = _showingAd

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _uiEvent = MutableStateFlow<UiEvent?>(null)
    val uiEvent: StateFlow<UiEvent?> = _uiEvent.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _mediaCache = MutableStateFlow<Map<String, Uri>>(emptyMap())
    private val _compressionQueue = MutableStateFlow<List<MediaCompressionTask>>(emptyList())
    private val _encryptedContent = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _viewerMetrics = MutableStateFlow<Map<Int, ViewerMetrics>>(emptyMap())

    private var storyLoadingJob: Job? = null
    private var mediaPreloadingJob: Job? = null
    private var metricTrackingJob: Job? = null
    private var compressionJob: Job? = null

    companion object {
        private const val TAG = "StoriesViewModel"
        private const val DEFAULT_STORY_DURATION = 5000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val STORIES_PAGE_SIZE = 10
        private const val MAX_STORY_SIZE_MB = 50
        private const val PRELOAD_THRESHOLD = 3
        private const val COMPRESSION_QUALITY = 85
        private const val METRICS_UPDATE_INTERVAL = 1000L
        private const val AD_FREQUENCY = 5
        private const val CACHE_SIZE_MB = 100
    }
    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            try {
                initializeCurrentUser()
                initializeE2EEncryption()
                initializeMediaCache()
                initializeMetricsTracking()
                loadStories()
                startMetricsTracking()
                startCompressionQueue()
            } catch (e: Exception) {
                handleError(e, "Error initializing ViewModel")
            }
        }
    }

    fun loadStories() {
        storyLoadingJob?.cancel()
        storyLoadingJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null

                val stories = withContext(Dispatchers.IO) {
                    storyRepository.getStories()
                }

                _stories.value = stories
                initializeStoryMetrics(stories)
                preloadStoryMedia(stories)

            } catch (e: Exception) {
                handleError(e, "Error loading stories")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun initializeCurrentUser() {
        try {
            _currentUserId.value = userRepository.getCurrentUserId()
            analyticsManager.logEvent("stories_viewer_initialized", Bundle().apply {
                putString("user_id", _currentUserId.value)
                putLong("timestamp", System.currentTimeMillis())
            })
        } catch (e: Exception) {
            handleError(e, "Error initializing current user")
        }
    }

    private suspend fun initializeMediaCache() {
        withContext(Dispatchers.IO) {
            mediaDownloadManager.apply {
                clearCache()
                setCacheSize(CACHE_SIZE_MB * 1024 * 1024L)
                setCompressionQuality(COMPRESSION_QUALITY)
            }
        }
    }

    private suspend fun initializeE2EEncryption() {
        try {
            _currentUserId.value?.let { userId ->
                e2eEncryption.initialize(userId)
            }
        } catch (e: Exception) {
            handleError(e, "Error initializing E2E encryption")
        }
    }

    private suspend fun initializeMetricsTracking() {
        _currentUserId.value?.let { userId ->
            metricsTracker.initializeTracking(userId)
        }
    }

    private fun initializeStoryMetrics(stories: List<Story>) {
        val metricsMap = stories.associate { story ->
            story.id to StoryMetrics()
        }
        _storyMetrics.value = metricsMap
    }

    private suspend fun preloadStoryMedia(stories: List<Story>) {
        stories.take(PRELOAD_THRESHOLD).forEach { story ->
            try {
                when (story.mediaType) {
                    MediaType.IMAGE -> mediaDownloadManager.preloadImage(story.mediaUrl ?: return@forEach)
                    MediaType.VIDEO -> mediaDownloadManager.preloadVideo(story.mediaUrl ?: return@forEach, MAX_STORY_SIZE_MB)
                    else -> {}
                }
            } catch (e: Exception) {
                logger.logEvent(TAG, "Error preloading media for story ${story.id}", LogLevel.ERROR, e)
            }
        }
    }

    fun selectStory(story: Story) {
        viewModelScope.launch {
            _selectedStory.value = story
            _currentStoryIndex.value = _stories.value.indexOf(story)

            val showAd = nativeStoryAdManager.shouldShowAd(_currentStoryIndex.value)
            _showingAd.value = showAd

            _uiState.value = UiState.ViewingStory(
                story = story,
                index = _currentStoryIndex.value,
                showingAd = showAd
            )

            updateStoryMetrics(story.id)
            metricsTracker.trackEngagement(story.id, EngagementType.VIEW)
        }
    }

    fun closeStory() {
        viewModelScope.launch {
            _selectedStory.value?.let { story ->
                updateStoryMetrics(story.id, completionStatus = CompletionStatus.VIEWING)
            }
            _selectedStory.value = null
            _uiState.value = UiState.Initial
        }
    }

    fun handleReaction(
        storyId: Int,
        reaction: Reaction,
        reactionId: String = UUID.randomUUID().toString()  // Añadido parámetro con valor por defecto
    ) {
        viewModelScope.launch {
            try {
                val updatedReaction = reaction.copy(
                    id = reactionId,  // Usar el reactionId proporcionado
                    content = if (reaction.isPrivate) {
                        e2eEncryption.encrypt(reaction.content)
                    } else reaction.content
                )

                storyRepository.addReaction(storyId, updatedReaction)
                updateStoryMetrics(storyId, reactionAdded = true)
                metricsTracker.trackEngagement(storyId, EngagementType.REACTION)
            } catch (e: Exception) {
                handleError(e, "Error adding reaction")
            }
        }
    }

    fun handleComment(
        storyId: Int,
        commentText: String,
        commentId: String = UUID.randomUUID().toString()  // Añadido parámetro con valor por defecto
    ) {
        viewModelScope.launch {
            try {
                val comment = Comment(
                    id = commentId,  // Usar el commentId proporcionado
                    content = commentText,
                    userId = _currentUserId.value ?: return@launch,
                    timestamp = System.currentTimeMillis(),
                    isPrivate = false
                )

                storyRepository.addComment(storyId, comment.id, comment.content)
                updateStoryMetrics(storyId, commentAdded = true)

                if (commentText.startsWith("@")) {
                    initializeReplyChain(storyId, comment)
                }

                metricsTracker.trackEngagement(storyId, EngagementType.COMMENT)
            } catch (e: Exception) {
                handleError(e, "Error adding comment")
            }
        }
    }

    private fun startMetricsTracking() {
        metricTrackingJob?.cancel()
        metricTrackingJob = viewModelScope.launch {
            while (isActive) {
                _selectedStory.value?.let { story ->
                    trackViewerMetrics(story.id)
                }
                delay(METRICS_UPDATE_INTERVAL)
            }
        }
    }

    private suspend fun trackViewerMetrics(storyId: Int) {
        val currentMetrics = _viewerMetrics.value[storyId] ?: ViewerMetrics()
        val updatedMetrics = currentMetrics.copy(
            viewDuration = currentMetrics.viewDuration + METRICS_UPDATE_INTERVAL,
            lastUpdated = System.currentTimeMillis()
        )
        _viewerMetrics.value = _viewerMetrics.value + (storyId to updatedMetrics)

        metricsTracker.trackStoryView(storyId, updatedMetrics.viewDuration)
        metricsTracker.trackEngagement(storyId, EngagementType.VIEW)
    }

    private fun startCompressionQueue() {
        compressionJob?.cancel()
        compressionJob = viewModelScope.launch {
            _compressionQueue.collect { queue ->
                queue.firstOrNull()?.let { task ->
                    try {
                        val compressedUri = when (task.type) {
                            MediaType.IMAGE -> mediaCompressor.compressImage(task.uri, COMPRESSION_QUALITY)
                            MediaType.VIDEO -> mediaCompressor.compressVideo(task.uri)
                            else -> task.uri
                        }
                        _mediaCache.value = _mediaCache.value + (task.id to compressedUri)
                        _compressionQueue.value = queue.drop(1)
                    } catch (e: Exception) {
                        handleError(e, "Error compressing media")
                        _compressionQueue.value = queue.drop(1)
                    }
                }
            }
        }
    }

    private fun updateStoryMetrics(
        storyId: Int,
        viewed: Boolean = true,
        reactionAdded: Boolean = false,
        commentAdded: Boolean = false,
        completionStatus: CompletionStatus? = null
    ) {
        val currentMetrics = _storyMetrics.value.toMutableMap()
        val storyMetrics = currentMetrics[storyId] ?: StoryMetrics()

        currentMetrics[storyId] = storyMetrics.copy(
            viewCount = if (viewed) storyMetrics.viewCount + 1 else storyMetrics.viewCount,
            reactionCount = if (reactionAdded) storyMetrics.reactionCount + 1 else storyMetrics.reactionCount,
            commentCount = if (commentAdded) storyMetrics.commentCount + 1 else storyMetrics.commentCount,
            lastViewed = if (viewed) System.currentTimeMillis() else storyMetrics.lastViewed,
            completionStatus = completionStatus ?: storyMetrics.completionStatus
        )

        _storyMetrics.value = currentMetrics
    }

    private suspend fun initializeReplyChain(storyId: Int, comment: Comment) {
        val currentChains = _replyChains.value.toMutableMap()
        val newReply = StoryReply(
            id = comment.id,
            content = comment.content,
            timestamp = comment.timestamp,
            userId = comment.userId
        )
        currentChains[storyId] = (currentChains[storyId] ?: emptyList()) + newReply
        _replyChains.value = currentChains
    }

    private fun handleError(error: Exception, message: String) {
        viewModelScope.launch {
            logger.logEvent(TAG, message, LogLevel.ERROR, error)
            _error.value = error.message ?: message
            _uiEvent.value = UiEvent.ShowSnackbar(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        storyLoadingJob?.cancel()
        mediaPreloadingJob?.cancel()
        metricTrackingJob?.cancel()
        compressionJob?.cancel()
        viewModelScope.launch {
            try {
                mediaDownloadManager.clearCache()
                mediaCompressor.clearCache()
                metricsTracker.generateReport(_selectedStory.value?.id ?: return@launch)
            } catch (e: Exception) {
                logger.logEvent(TAG, "Error in onCleared", LogLevel.ERROR, e)
            }
        }
    }

    data class MediaCompressionTask(
        val id: String,
        val uri: Uri,
        val type: MediaType
    )

    data class StoryReply(
        val id: String,
        val content: String,
        val timestamp: Long,
        val userId: String,
        val mentions: List<String> = emptyList(),
        val attachments: List<String> = emptyList()
    )

    data class ViewerMetrics(
        val viewDuration: Long = 0L,
        val interactionCount: Int = 0,
        val lastUpdated: Long = System.currentTimeMillis(),
        val completionStatus: CompletionStatus = CompletionStatus.VIEWING
    )

    sealed class UiEvent {
        data class ShowSnackbar(val message: String) : UiEvent()
        object NavigateToUserSelection : UiEvent()
        data class ShowAd(val adType: AdType) : UiEvent()
        data class ShowEffect(val effect: StoryEffect) : UiEvent()
    }

    sealed class UiState {
        object Initial : UiState()
        data class SelectUsers(val story: Story) : UiState()
        data class ViewingStory(
            val story: Story,
            val index: Int,
            val showingAd: Boolean
        ) : UiState()
    }
}
package com.williamfq.xhat.ui.stories.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.williamfq.domain.model.*
import com.williamfq.xhat.ui.stories.StoryReaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

data class StoryUnsupportedContent(
    val message: String = "Tipo de contenido no soportado"
)

data class StoryInteractiveElement(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val icon: ImageVector,
    val position: Offset,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null
)

sealed class StoryEffect {
    data class Sparkle(val position: Offset) : StoryEffect()
    data class Heart(val position: Offset) : StoryEffect()
    data class Star(val position: Offset) : StoryEffect()
}

data class Effect(
    val position: Offset,
    val type: EffectType,
    val createdAt: Long = System.currentTimeMillis()
)

enum class EffectType {
    SPARKLE, HEART, STAR
}

@Composable
private fun AnimatedReactionPanel(
    visible: Boolean,
    onReaction: (StoryReaction) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut(),
        modifier = modifier
    ) {
        // Panel de reacciones animado
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items(StoryReaction.entries.toTypedArray()) { reaction ->
                    IconButton(
                        onClick = { onReaction(reaction) }
                    ) {
                        Icon(
                            imageVector = reaction.icon,
                            contentDescription = reaction.name,
                            tint = reaction.color
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryViewer(
    story: Story,
    onDismiss: () -> Unit,
    onReaction: (StoryReaction) -> Unit,
    onComment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var showReactions by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var isPaused by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var isDoubleTapped by remember { mutableStateOf(false) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pressOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(story, isPaused) {
        if (!isPaused) {
            progress = 0f
            val totalDuration = story.durationSeconds * 1000L
            val steps = 100
            val stepDuration = totalDuration / steps

            for (i in 0..steps) {
                if (!isPaused) {
                    progress = i.toFloat() / steps
                    delay(stepDuration)
                } else {
                    break
                }
            }
            if (!isPaused && !isDoubleTapped) {
                onDismiss()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationChange ->
                    scale = (scale * zoom).coerceIn(0.5f, 3f)
                    rotation += rotationChange
                    pressOffset += pan.x
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 300) {
                            isDoubleTapped = true
                            scope.launch {
                                onReaction(StoryReaction.LIKE)
                                delay(300)
                                isDoubleTapped = false
                            }
                        }
                        lastTapTime = currentTime
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                translationX = pressOffset
            }
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .animateContentSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp)
        ) {
            when (story.mediaType) {
                MediaType.IMAGE -> StoryImage(story)
                MediaType.VIDEO -> StoryVideo(
                    story = story,
                    onProgressUpdate = { progress = it },
                    isPaused = isPaused
                )
                MediaType.TEXT -> StoryText(story)
                MediaType.AUDIO -> StoryAudio(
                    story = story,
                    onProgressUpdate = { progress = it },
                    isPaused = isPaused
                )
                MediaType.AR -> StoryAR(story)
                MediaType.POLL -> StoryPoll(story)
                MediaType.QUIZ -> StoryQuiz(story)
                else -> StoryUnsupported()
            }
        }

        TopControls(
            story = story,
            onDismiss = onDismiss,
            onPauseToggle = { isPaused = !isPaused },
            onShare = { /* Implementar compartir */ },
            onReport = { /* Implementar reporte */ },
            isFlashOn = isFlashOn,
            onFlashToggle = { isFlashOn = !isFlashOn },
            onSwitchCamera = { /* Implementar cambio de cámara */ }
        )

        BottomControls(
            story = story,
            commentText = commentText,
            onCommentChange = { commentText = it },
            onCommentSend = {
                if (commentText.isNotEmpty()) {
                    onComment(commentText)
                    commentText = ""
                }
            },
            onReactionClick = { showReactions = !showReactions },
            onVoiceRecordingStart = { /* Implementar grabación */ },
            onCameraClick = { isCapturing = !isCapturing },
            isCapturing = isCapturing,
            onCaptureClick = { /* Implementar captura */ },
            onGalleryClick = { /* Implementar galería */ },
            onFiltersClick = { /* Implementar filtros */ }
        )

        AnimatedReactionPanel(
            visible = showReactions,
            onReaction = { reaction ->
                scope.launch {
                    onReaction(reaction)
                    showReactions = false
                }
            }
        )

        if (story.hasEffects) {
            StoryEffectsOverlay(story)
        }

        if (story.hasInteractiveElements) {
            StoryInteractiveElements(story)
        }

        AnimatedVisibility(
            visible = isDoubleTapped,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = Color.Red
                )
            }
        }
    }
}
@Composable
private fun StoryImage(
    story: Story,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = story.mediaUrl,
            contentDescription = story.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
            onLoading = { isLoading = true },
            onSuccess = { isLoading = false },
            onError = { isLoading = false }
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun StoryVideo(
    story: Story,
    onProgressUpdate: (Float) -> Unit,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(story.mediaUrl ?: return@apply))
            prepare()
            playWhenReady = !isPaused
            addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (duration > 0) {
                        onProgressUpdate(currentPosition.toFloat() / duration.toFloat())
                    }
                }
            })
        }
    }

    DisposableEffect(isPaused) {
        if (isPaused) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isPaused) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Play",
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.Center),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun StoryPoll(story: Story) {
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var hasVoted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = story.question ?: "¿Qué opinas?",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        story.options?.forEachIndexed { index, option ->
            PollOption(
                option = option,
                isSelected = selectedOption == index,
                percentage = if (hasVoted) calculatePollPercentage(story, index) else null,
                onSelect = {
                    if (!hasVoted) {
                        selectedOption = index
                        hasVoted = true
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PollOption(
    option: String,
    isSelected: Boolean,
    percentage: Float?,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = percentage == null, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else
                Color.White.copy(alpha = 0.1f)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (percentage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(percentage)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        .height(48.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )

                if (percentage != null) {
                    Text(
                        text = "${(percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryQuiz(story: Story) {
    var selectedAnswer by remember { mutableStateOf<Int?>(null) }
    var hasAnswered by remember { mutableStateOf(false) }
    var showExplanation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = story.question ?: "Responde la pregunta",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        story.options?.forEachIndexed { index, option ->
            QuizOption(
                option = option,
                isSelected = selectedAnswer == index,
                isCorrect = if (hasAnswered) index == story.correctAnswer else null,
                onSelect = {
                    if (!hasAnswered) {
                        selectedAnswer = index
                        hasAnswered = true
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = hasAnswered,
            enter = expandVertically() + fadeIn()
        ) {
            QuizFeedback(
                isCorrect = selectedAnswer == story.correctAnswer,
                explanation = story.explanation,
                showExplanation = showExplanation,
                onToggleExplanation = { showExplanation = !showExplanation }
            )
        }
    }
}

@Composable
private fun QuizFeedback(
    isCorrect: Boolean,
    explanation: String?,
    showExplanation: Boolean,
    onToggleExplanation: () -> Unit
) {
    Column {
        Text(
            text = if (isCorrect) "¡Correcto!" else "¡Inténtalo de nuevo!",
            style = MaterialTheme.typography.titleMedium,
            color = if (isCorrect) Color.Green else Color.Red,
            modifier = Modifier.padding(top = 16.dp)
        )

        if (!explanation.isNullOrEmpty()) {
            OutlinedButton(
                onClick = onToggleExplanation,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = if (showExplanation) "Ocultar explicación" else "Ver explicación",
                    color = Color.White
                )
            }

            AnimatedVisibility(visible = showExplanation) {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun calculatePollPercentage(story: Story, optionIndex: Int): Float {
    val totalVotes = story.votes?.sum() ?: 0
    if (totalVotes == 0) return 0f
    return (story.votes?.getOrNull(optionIndex) ?: 0).toFloat() / totalVotes
}

private fun DrawScope.drawSparkle(position: Offset, alpha: Float) {
    val size = 40f
    val strokeWidth = 2f
    val color = Color.Yellow.copy(alpha = alpha)

    for (i in 0 until 8) {
        val angle = i * Math.PI / 4
        val startX = position.x + size * 0.2f * cos(angle).toFloat()
        val startY = position.y + size * 0.2f * sin(angle).toFloat()
        val endX = position.x + size * 0.5f * cos(angle).toFloat()
        val endY = position.y + size * 0.5f * sin(angle).toFloat()

        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth
        )
    }
}
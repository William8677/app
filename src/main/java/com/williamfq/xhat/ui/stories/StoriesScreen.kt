package com.williamfq.xhat.ui.stories

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.williamfq.domain.model.*
import com.williamfq.xhat.ui.stories.components.StoryViewer
import com.williamfq.xhat.ui.stories.viewmodel.StoriesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

@Composable
private fun StoryCard(
    story: Story,
    onLongPress: () -> Unit,
    onPress: (Offset) -> Unit,
    onPressRelease: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onPress = { offset ->
                        onPress(offset)
                        tryAwaitRelease()
                        onPressRelease()
                    }
                )
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (story.mediaType) {
                MediaType.IMAGE, MediaType.VIDEO -> {
                    AsyncImage(
                        model = story.mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Overlay con información
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(8.dp)
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
    }
}

@Composable
fun StoriesScreen(
    navController: NavHostController,
    viewModel: StoriesViewModel = hiltViewModel(),
    onNavigateToAddStory: () -> Unit = {}
) {
    val stories by viewModel.stories.collectAsState()
    val selectedStory by viewModel.selectedStory.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    var previewStory by remember { mutableStateOf<Story?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.loadStories()
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(stories) { story ->
                StoryCard(
                    story = story,
                    onLongPress = {
                        previewStory = story
                        showPreview = true
                    },
                    onPress = {
                        viewModel.selectStory(story)
                    },
                    onPressRelease = {
                        scope.launch {
                            delay(100)
                            if (previewStory == story) {
                                showPreview = false
                                previewStory = null
                            }
                        }
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = stories.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay historias disponibles",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { showOptionsMenu = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.MoreVert, "Más opciones")
            }

            FloatingActionButton(
                onClick = onNavigateToAddStory
            ) {
                Icon(Icons.Default.Add, "Agregar historia")
            }
        }

        if (showOptionsMenu) {
            DropdownMenu(
                expanded = showOptionsMenu,
                onDismissRequest = { showOptionsMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Crear grupo de historias") },
                    onClick = { /* Implementar creación de grupo */ },
                    leadingIcon = {
                        Icon(Icons.Default.Group, null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Historias archivadas") },
                    onClick = { /* Implementar vista de archivo */ },
                    leadingIcon = {
                        Icon(Icons.Default.Archive, null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Configuración") },
                    onClick = { /* Implementar configuración */ },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, null)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showPreview && previewStory != null,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            Dialog(
                onDismissRequest = { },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                previewStory?.let { story ->
                    StoryPreviewContent(story = story)
                }
            }
        }

        selectedStory?.let { story ->
            StoryViewer(
                story = story,
                onDismiss = { viewModel.closeStory() },
                onReaction = { reaction ->
                    val reactionType = mapStoryReactionToType(reaction)
                    viewModel.handleReaction(
                        storyId = story.id,
                        reaction = Reaction(
                            id = UUID.randomUUID().toString(),
                            userId = currentUserId ?: return@StoryViewer,
                            type = reactionType,
                            content = reaction.name,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                },
                onComment = { commentText ->
                    viewModel.handleComment(
                        storyId = story.id,
                        commentText = commentText
                    )
                }
            )
        }
    }
}

@Composable
private fun StoryPreviewContent(story: Story) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (story.mediaType) {
            MediaType.IMAGE, MediaType.VIDEO -> {
                StoryMediaContent(story)
            }
            MediaType.TEXT -> {
                StoryTextContent(story)
            }
            MediaType.AUDIO -> {
                StoryAudioContent(story)
            }
            MediaType.AR -> {
                StoryARContent(story)
            }
            MediaType.POLL -> {
                StoryPollContent(story)
            }
            MediaType.QUIZ -> {
                StoryQuizContent(story)
            }
            else -> {
                StoryUnsupportedContent()
            }
        }
    }
}

@Composable
private fun StoryMediaContent(story: Story) {
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        story.mediaUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onLoading = { isLoading = true },
                onSuccess = { isLoading = false },
                onError = { isLoading = false }
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        StoryOverlay(story)
    }
}

@Composable
private fun StoryTextContent(story: Story) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = story.description,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

@Composable
private fun StoryAudioContent(story: Story) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AudioFile,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = story.title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }
}

@Composable
private fun StoryARContent(story: Story) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Contenido AR",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White
        )
    }
}

@Composable
private fun StoryPollContent(story: Story) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = story.pollQuestion ?: "Encuesta",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        story.pollOptions?.forEach { option ->
            Button(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text(text = option)
            }
        }
    }
}

@Composable
private fun StoryQuizContent(story: Story) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = story.quizQuestion ?: "Quiz",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        story.quizOptions?.forEach { option ->
            OutlinedButton(
                onClick = { },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(text = option)
            }
        }
    }
}

@Composable
private fun StoryUnsupportedContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Tipo de contenido no soportado",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

@Composable
private fun StoryOverlay(story: Story) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = getTimeAgo(story.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun mapStoryReactionToType(reaction: StoryReaction): ReactionType {
    return when (reaction) {
        StoryReaction.LIKE -> ReactionType.LIKE
        StoryReaction.LOVE -> ReactionType.LOVE
        StoryReaction.LAUGH -> ReactionType.HAHA
        StoryReaction.WOW -> ReactionType.WOW
        StoryReaction.SAD -> ReactionType.SAD
        StoryReaction.ANGRY -> ReactionType.ANGRY
        StoryReaction.SUPPORT -> ReactionType.CARE
        StoryReaction.CELEBRATE -> ReactionType.CELEBRATE
        StoryReaction.FIRE -> ReactionType.FIRE
        StoryReaction.CLAP -> ReactionType.CLAP
        StoryReaction.THINK -> ReactionType.THINK
        StoryReaction.EYES -> ReactionType.EYES
        StoryReaction.HUNDRED -> ReactionType.HUNDRED
        StoryReaction.HEART_EYES -> ReactionType.HEART_EYES
        StoryReaction.MINDBLOWN -> ReactionType.MINDBLOWN
    }
}

private fun getTimeAgo(timestamp: Long): String {
    val now = LocalDateTime.now()
    val storyTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(timestamp),
        ZoneId.systemDefault()
    )

    val minutes = ChronoUnit.MINUTES.between(storyTime, now)
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 1440 -> "${minutes / 60}h"
        minutes < 10080 -> "${minutes / 1440}d"
        else -> "${minutes / 10080}w"
    }
}
package com.williamfq.xhat.ui.stories.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.williamfq.domain.model.Story

@Composable
fun StoryText(story: Story) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(story.backgroundColor ?: Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = story.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = story.textColor ?: Color.White,
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.padding(16.dp)
        )
    }
}
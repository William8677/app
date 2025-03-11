/*
 * Updated: 2025-02-09 21:38:26
 * Author: William8677
 */
package com.williamfq.xhat.ads.ui.components

import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.williamfq.xhat.core.config.AdMobConfig
import kotlinx.coroutines.delay
import android.widget.Button
import android.widget.TextView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import android.view.ViewGroup.LayoutParams

@Composable
fun NativeStoryAd(
    nativeAd: NativeAd,
    onAdComplete: () -> Unit,
    onAdSkipped: () -> Unit
) {
    var canSkip by remember { mutableStateOf(false) }
    var remainingTime by remember { mutableStateOf(5L) }

    LaunchedEffect(Unit) {
        delay(timeMillis = AdMobConfig.MIN_TIME_TO_SKIP_AD_MS.toLong())
        canSkip = true
    }

    LaunchedEffect(Unit) {
        val totalDuration = remainingTime
        for (i in totalDuration downTo 1L) {
            remainingTime = i
            delay(timeMillis = 1000L)
        }
        onAdComplete()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Anuncio",
                    style = MaterialTheme.typography.labelMedium
                )
                if (canSkip) {
                    TextButton(onClick = onAdSkipped) {
                        Text("Saltar")
                    }
                } else {
                    Text("$remainingTime")
                }
            }

            NativeAdContent(nativeAd)

            Button(
                onClick = {
                    nativeAd.callToAction?.let {
                        // Manejar click en CTA
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(nativeAd.callToAction ?: "Más información")
            }
        }
    }
}

@Composable
private fun NativeAdContent(nativeAd: NativeAd) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = nativeAd.headline ?: "",
            style = MaterialTheme.typography.headlineMedium
        )

        nativeAd.body?.let { body ->
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        nativeAd.mediaContent?.let { mediaContent ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f/16f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        NativeAdView(ctx).apply {
                            val adMediaView = MediaView(ctx).apply {
                                layoutParams = LayoutParams(
                                    LayoutParams.MATCH_PARENT,
                                    LayoutParams.MATCH_PARENT
                                )
                                setMediaContent(mediaContent)
                            }

                            val adIconView = ImageView(ctx).apply {
                                layoutParams = LayoutParams(
                                    LayoutParams.WRAP_CONTENT,
                                    LayoutParams.WRAP_CONTENT
                                )
                                adjustViewBounds = true
                            }

                            nativeAd.icon?.drawable?.let { drawable ->
                                adIconView.setImageDrawable(drawable)
                                adIconView.visibility = View.VISIBLE
                            } ?: run {
                                adIconView.visibility = View.GONE
                            }

                            setMediaView(adMediaView)
                            setIconView(adIconView)
                            setHeadlineView(TextView(ctx).apply {
                                text = nativeAd.headline
                            })
                            setBodyView(TextView(ctx).apply {
                                text = nativeAd.body
                            })
                            setCallToActionView(Button(ctx).apply {
                                text = nativeAd.callToAction ?: "Más información"
                            })

                            setNativeAd(nativeAd)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            nativeAd.advertiser?.let { advertiser ->
                Text(
                    text = advertiser,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            nativeAd.starRating?.let { rating ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(rating.toInt()) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Estrella",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
/*
 * Updated: 2025-02-09 19:51:05
 * Author: William8677
 */
package com.williamfq.xhat.ads.data.source.remote

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MediaAspectRatio
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.williamfq.xhat.core.config.AdMobConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdMobService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    init {
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(emptyList()) // Remover dispositivos de prueba para producción
            .build()
        MobileAds.setRequestConfiguration(configuration)
        MobileAds.initialize(context)
    }

    fun loadNativeStoryAd(): AdLoader {
        return AdLoader.Builder(context, AdMobConfig.NATIVE_STORY_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                // Manejar el anuncio nativo cargado
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Timber.e("Error al cargar el anuncio: ${error.message}")
                }
                override fun onAdLoaded() {
                    Timber.d("Anuncio cargado exitosamente")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setMediaAspectRatio(MediaAspectRatio.PORTRAIT)
                    .setVideoOptions(
                        VideoOptions.Builder()
                            .setStartMuted(true)
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
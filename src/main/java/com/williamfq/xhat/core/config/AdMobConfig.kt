/*
 * Updated: 2025-02-09 17:04:06
 * Author: William8677
 */
package com.williamfq.xhat.core.config

object AdMobConfig {
    // IDs de la aplicación
    const val APP_ID = "ca-app-pub-2587938308176637~6448560139"
    const val NATIVE_STORY_AD_UNIT_ID = "ca-app-pub-2587938308176637/4265820740"

    // Configuración de frecuencia de anuncios
    const val STORIES_BETWEEN_ADS = 8 // Mostrar un anuncio cada 8 historias
    const val MAX_STORY_DURATION_SECONDS = 90
    const val MIN_STORY_DURATION_SECONDS = 30
    const val DEFAULT_STORY_DURATION_SECONDS = 60

    // Configuración de la experiencia del usuario
    const val MIN_TIME_TO_SKIP_AD_MS = 2000 // 3 segundos antes de poder saltar
    const val MAX_ADS_PER_SESSION = 5 // Máximo de anuncios por sesión
    const val MIN_TIME_BETWEEN_ADS_MS = 120_000 // 2 minutos entre anuncios

    // Configuración de métricas
    const val MIN_AD_VISIBILITY_PERCENT = 50 // Porcentaje mínimo visible para contar como impresión
    const val MIN_AD_VISIBILITY_TIME_MS = 1000 // Tiempo mínimo visible para contar como impresión
}
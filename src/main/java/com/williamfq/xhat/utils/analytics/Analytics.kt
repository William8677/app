package com.williamfq.xhat.utils.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.williamfq.xhat.utils.logging.LoggerInterface
import com.williamfq.xhat.utils.logging.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log

@Singleton
class Analytics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: LoggerInterface
) {
    private val firebaseAnalytics: FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    private val crashlytics: FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
    private val analyticsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isInitialized = false

    companion object {
        private const val USER_ID = "William8677"
        private const val TIMESTAMP = "2025-02-21 20:03:37"
        private const val TAG = "Analytics"
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    init {
        analyticsScope.launch {
            try {
                setupDefaultProperties()
                isInitialized = true
            } catch (e: Exception) {
                handleInitializationError(e)
            }
        }
    }

    private suspend fun setupDefaultProperties() {
        try {
            firebaseAnalytics.setUserId(USER_ID)
            crashlytics.apply {
                setUserId(USER_ID)
                setCustomKey("timestamp", TIMESTAMP)
                setCustomKey("initialization_time", System.currentTimeMillis())
            }
            logger.logEvent(TAG, "Default properties setup completed", LogLevel.DEBUG)
        } catch (e: Exception) {
            throw Exception("Failed to setup default properties", e)
        }
    }

    suspend fun logEvent(event: AnalyticsEvent) {
        if (!isInitialized) {
            logger.logEvent(TAG, "Analytics not initialized", LogLevel.WARNING)
            return
        }

        analyticsScope.launch {
            try {
                val bundle = Bundle().apply {
                    event.parameters.forEach { (key, value) ->
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is Double -> putDouble(key, value)
                            is Boolean -> putBoolean(key, value)
                            is List<*> -> putStringArrayList(key, ArrayList(value.map { it.toString() }))
                        }
                    }
                    // Añadir metadatos estándar
                    putString("event_timestamp", TIMESTAMP)
                    putString("user_id", USER_ID)
                }

                firebaseAnalytics.logEvent(event.name, bundle)
                logger.logEvent(TAG, "Analytics event logged: ${event.name}", LogLevel.DEBUG)
            } catch (e: Exception) {
                handleAnalyticsError("Error logging analytics event", e)
            }
        }
    }

    suspend fun trackEvent(eventName: String) {
        if (!isInitialized) {
            logger.logEvent(TAG, "Analytics not initialized", LogLevel.WARNING)
            return
        }

        analyticsScope.launch {
            try {
                val simpleEvent = object : AnalyticsEvent {
                    override val name = eventName
                    override val parameters: Map<String, Any> = mapOf(
                        "timestamp" to TIMESTAMP,
                        "user_id" to USER_ID
                    )
                }
                logEvent(simpleEvent)
            } catch (e: Exception) {
                handleAnalyticsError("Error tracking simple event", e)
            }
        }
    }

    suspend fun setUserProperty(property: UserProperty) {
        if (!isInitialized) {
            logger.logEvent(TAG, "Analytics not initialized", LogLevel.WARNING)
            return
        }

        analyticsScope.launch {
            try {
                firebaseAnalytics.setUserProperty(property.name, property.value)
                crashlytics.setCustomKey(property.name, property.value)
                logger.logEvent(
                    TAG,
                    "User property set: ${property.name} = ${property.value}",
                    LogLevel.DEBUG
                )
            } catch (e: Exception) {
                handleAnalyticsError("Error setting user property", e)
            }
        }
    }

    private fun handleAnalyticsError(message: String, error: Exception) {
        analyticsScope.launch {
            try {
                logger.logEvent(TAG, message, LogLevel.ERROR, error)
                crashlytics.recordException(error)
            } catch (e: Exception) {
                Log.e(TAG, "Critical error in analytics", e)
            }
        }
    }

    private fun handleInitializationError(error: Exception) {
        Log.e(TAG, "Failed to initialize Analytics", error)
        analyticsScope.launch {
            logger.logEvent(TAG, "Analytics initialization failed", LogLevel.ERROR, error)
        }
    }
}

interface AnalyticsEvent {
    val name: String
    val parameters: Map<String, Any>
}

data class UserProperty(
    val name: String,
    val value: String
)
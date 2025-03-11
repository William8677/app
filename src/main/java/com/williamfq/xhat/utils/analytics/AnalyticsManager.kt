package com.williamfq.xhat.utils.analytics

import android.content.Context
import android.os.Bundle
import com.google.android.gms.ads.initialization.AdapterStatus
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class AnalyticsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null
    private var isEnabled = false
    private var userId: String? = null
    private var isInitializing = false
    private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val USER_ID = "William8677"
        private const val TIMESTAMP = "2025-02-21 20:01:53"
        private const val DEFAULT_ERROR_TAG = "unknown"
        private const val EVENT_AD = "ad_event"
        private const val EVENT_SCREEN_VIEW = "screen_view"
        private const val TAG = "AnalyticsManager"
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    @Synchronized
    fun initialize(enabled: Boolean, userId: String?, properties: Map<String, Any>) {
        if (isInitializing) {
            Timber.w("Analytics initialization already in progress")
            return
        }

        isInitializing = true
        analyticsScope.launch {
            try {
                this@AnalyticsManager.isEnabled = enabled
                this@AnalyticsManager.userId = userId

                if (enabled) {
                    initializeFirebaseComponents()
                    setupUserAndProperties(userId, properties)
                    Timber.d("Analytics initialized successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing analytics", e)
                handleInitializationError(e)
            } finally {
                isInitializing = false
            }
        }
    }

    private fun initializeFirebaseComponents() {
        try {
            firebaseAnalytics = Firebase.analytics
            crashlytics = FirebaseCrashlytics.getInstance()

            firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
            crashlytics?.isCrashlyticsCollectionEnabled = true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error initializing Firebase components")
            throw e
        }
    }

    private fun setupUserAndProperties(userId: String?, properties: Map<String, Any>) {
        try {
            userId?.let {
                firebaseAnalytics?.setUserId(it)
                crashlytics?.setUserId(it)
            }

            properties.forEach { (key, value) ->
                when (value) {
                    is String -> {
                        firebaseAnalytics?.setUserProperty(key, value)
                        crashlytics?.setCustomKey(key, value)
                    }
                    is Number -> {
                        crashlytics?.setCustomKey(key, value.toString())
                        firebaseAnalytics?.setUserProperty(key, value.toString())
                    }
                    is Boolean -> {
                        crashlytics?.setCustomKey(key, value)
                        firebaseAnalytics?.setUserProperty(key, value.toString())
                    }
                }
            }

            // Configurar propiedades por defecto
            crashlytics?.apply {
                setCustomKey("initialization_time", TIMESTAMP)
                setCustomKey("default_user", USER_ID)
                setCustomKey("analytics_enabled", isEnabled)
            }

            Timber.d("User and properties setup completed for user: $userId")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error setting up user and properties")
            throw e
        }
    }

    // Método logEvent corregido: ahora recibe un Bundle? en lugar de Map<K, V>
    fun logEvent(eventName: String, params: Bundle? = null) {
        if (!isEnabled) return

        analyticsScope.launch {
            try {
                val enhancedParams = (params ?: Bundle()).apply {
                    putString("timestamp", TIMESTAMP)
                    putString("user_id", userId ?: USER_ID)
                }

                firebaseAnalytics?.logEvent(eventName, enhancedParams)
                Timber.d("Analytics event logged: $eventName")
            } catch (e: Exception) {
                handleAnalyticsError("Error logging analytics event", e)
            }
        }
    }

    fun logError(tag: String, throwable: Throwable?) {
        if (!isEnabled) return

        analyticsScope.launch {
            try {
                val errorTag = if (tag.isEmpty()) DEFAULT_ERROR_TAG else tag
                crashlytics?.apply {
                    setCustomKey("error_timestamp", TIMESTAMP)
                    setCustomKey("error_tag", errorTag)
                    setCustomKey("error_type", throwable?.javaClass?.simpleName ?: "Unknown")
                    log("Error in $errorTag: ${throwable?.message}")
                    throwable?.let { recordException(it) }
                }
                Timber.tag(TAG).e(throwable, "Error logged: $errorTag")
            } catch (e: Exception) {
                handleAnalyticsError("Error logging error event", e)
            }
        }
    }

    fun logAdapterStatus(adapter: String, status: AdapterStatus) {
        if (!isEnabled) return

        analyticsScope.launch {
            try {
                val params = Bundle().apply {
                    putString("adapter_name", adapter)
                    putString("adapter_status", status.description)
                    putInt("adapter_latency", status.latency)
                    putString("timestamp", TIMESTAMP)
                    putString("user_id", userId ?: USER_ID)
                }
                firebaseAnalytics?.logEvent("admob_adapter_status", params)
                Timber.d("AdMob adapter status logged: $adapter")
            } catch (e: Exception) {
                handleAnalyticsError("Error logging adapter status", e)
            }
        }
    }

    fun setUserProperty(name: String, value: String) {
        if (!isEnabled) return

        analyticsScope.launch {
            try {
                firebaseAnalytics?.setUserProperty(name, value)
                crashlytics?.setCustomKey(name, value)
                Timber.d("User property set: $name = $value")
            } catch (e: Exception) {
                handleAnalyticsError("Error setting user property", e)
            }
        }
    }

    fun logScreenView(screenName: String, screenClass: String) {
        if (!isEnabled) return

        analyticsScope.launch {
            try {
                val params = Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
                    putString("timestamp", TIMESTAMP)
                    putString("user_id", userId ?: USER_ID)
                }
                firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
                Timber.d("Screen view logged: $screenName")
            } catch (e: Exception) {
                handleAnalyticsError("Error logging screen view", e)
            }
        }
    }

    fun logAdEvent(adUnitId: String, eventType: AdEventType) {
        if (!isEnabled) return

        analyticsScope.launch {
            try {
                val params = Bundle().apply {
                    putString("ad_unit_id", adUnitId)
                    putString("event_type", eventType.name)
                    putString("timestamp", TIMESTAMP)
                    putString("user_id", userId ?: USER_ID)
                }
                firebaseAnalytics?.logEvent(EVENT_AD, params)
                Timber.d("Ad event logged: $eventType for unit $adUnitId")
            } catch (e: Exception) {
                handleAnalyticsError("Error logging ad event", e)
            }
        }
    }

    private fun handleAnalyticsError(message: String, error: Exception) {
        Timber.tag(TAG).e(error, message)
        crashlytics?.apply {
            setCustomKey("analytics_error_time", TIMESTAMP)
            setCustomKey("analytics_error_type", error.javaClass.simpleName)
            recordException(error)
        }
    }

    private fun handleInitializationError(error: Exception) {
        Timber.tag(TAG).e(error, "Analytics initialization failed")
        crashlytics?.apply {
            setCustomKey("init_error_time", TIMESTAMP)
            setCustomKey("init_error_type", error.javaClass.simpleName)
            recordException(error)
        }
    }
}

enum class AdEventType {
    LOADED,
    FAILED_TO_LOAD,
    IMPRESSION,
    CLICK,
    COMPLETION,
    INTERACTION,
    CONVERSION,
    CLOSED,
    LEFT_APPLICATION,
    SKIPPED,
    REWARDED,
    EXPANDED,
    COLLAPSED,
    VIDEO_STARTED,
    VIDEO_COMPLETED,
    VIDEO_PROGRESS,
    AUDIO_STARTED,
    AUDIO_COMPLETED,
    AUDIO_MUTED,
    AUDIO_UNMUTED
}

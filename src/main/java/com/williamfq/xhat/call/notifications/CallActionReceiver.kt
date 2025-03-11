/*
 * Updated: 2025-02-13 00:58:56
 * Author: William8677
 */

package com.williamfq.xhat.call.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.williamfq.domain.model.User
import com.williamfq.xhat.call.CallManager
import com.williamfq.xhat.utils.analytics.Analytics
import com.williamfq.xhat.utils.analytics.CallAnalyticsEvent
import com.williamfq.xhat.utils.logging.LogLevel
import com.williamfq.xhat.utils.logging.LoggerInterface
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject lateinit var callManager: CallManager
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var notificationManager: CallNotificationManager
    @Inject lateinit var logger: LoggerInterface

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_ACCEPT_CALL = "com.williamfq.xhat.action.ACCEPT_CALL"
        const val ACTION_REJECT_CALL = "com.williamfq.xhat.action.REJECT_CALL"
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_AVATAR_URL = "extra_avatar_url"
        const val EXTRA_IS_VIDEO_CALL = "extra_is_video_call"
        private const val TAG = "CallActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val userId = intent.getStringExtra(EXTRA_USER_ID) ?: return
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: return
        val avatarUrl = intent.getStringExtra(EXTRA_AVATAR_URL) ?: return
        val isVideoCall = intent.getBooleanExtra(EXTRA_IS_VIDEO_CALL, false)

        val user = User(
            id = userId,
            username = username,
            avatarUrl = avatarUrl
        )

        receiverScope.launch {
            try {
                when (intent.action) {
                    ACTION_ACCEPT_CALL -> handleAcceptCall(user, isVideoCall, callId)
                    ACTION_REJECT_CALL -> handleRejectCall()
                }
            } catch (e: Exception) {
                logger.logEvent(
                    tag = TAG,
                    message = "Error handling call action: ${e.message}",
                    level = LogLevel.ERROR,
                    throwable = e
                )
            }
        }
    }

    private suspend fun handleAcceptCall(user: User, isVideoCall: Boolean, callId: String) {
        try {
            notificationManager.cancelIncomingCallNotification()
            callManager.startCall(
                user = user,
                isVideoCall = isVideoCall
            )

            analytics.trackEvent("call_accepted")
            logger.logEvent(
                tag = TAG,
                message = "Call accepted: user=${user.username}, video=$isVideoCall",
                level = LogLevel.INFO
            )
        } catch (e: Exception) {
            logger.logEvent(
                tag = TAG,
                message = "Error accepting call",
                level = LogLevel.ERROR,
                throwable = e
            )
        }
    }

    private suspend fun handleRejectCall() {
        try {
            notificationManager.cancelIncomingCallNotification()
            callManager.endCall()

            analytics.trackEvent("call_rejected")
            logger.logEvent(
                tag = TAG,
                message = "Call rejected",
                level = LogLevel.INFO
            )
        } catch (e: Exception) {
            logger.logEvent(
                tag = TAG,
                message = "Error rejecting call",
                level = LogLevel.ERROR,
                throwable = e
            )
        }
    }
}
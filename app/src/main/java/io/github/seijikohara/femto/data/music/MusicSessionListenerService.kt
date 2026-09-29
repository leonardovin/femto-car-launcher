package io.github.seijikohara.femto.data.music

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.seijikohara.femto.data.navigation.NavigationGuidanceRepository

/**
 * The launcher's notification-listener component. Its grant is what lets
 * MediaSessionManager list other apps' media sessions (the music card), and
 * the same connection also mirrors navigation apps' turn-by-turn notification
 * into [NavigationGuidanceRepository] — one user grant covers both.
 */
internal class MusicSessionListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        NavigationGuidanceRepository.onSnapshot(
            this,
            runCatching { activeNotifications?.toList() }.getOrNull().orEmpty(),
        )
    }

    override fun onListenerDisconnected() = NavigationGuidanceRepository.onDisconnected()

    override fun onNotificationPosted(sbn: StatusBarNotification) = NavigationGuidanceRepository.onPosted(this, sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NavigationGuidanceRepository.onRemoved(sbn)
}

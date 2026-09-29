package io.github.seijikohara.femto.data.navigation

import android.app.Application
import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val MAPS = "com.google.android.apps.maps"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationGuidanceRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun reset() = NavigationGuidanceRepository.onDisconnected()

    @Test
    fun `a navigation app's ongoing guidance notification becomes the guidance`() {
        NavigationGuidanceRepository.onPosted(context, sbn(MAPS, guidanceNotification()))

        val guidance = assertNotNull(NavigationGuidanceRepository.guidance.value)
        assertEquals(MAPS, guidance.packageName)
        assertEquals("300 m", guidance.distance)
        assertEquals("Turn right onto Av. Paulista", guidance.instruction)
        assertEquals("12 min · 4.2 km", guidance.eta)
        assertNotNull(guidance.maneuver)
    }

    @Test
    fun `other apps and non-ongoing notifications are ignored`() {
        NavigationGuidanceRepository.onPosted(context, sbn("com.example.chat", guidanceNotification()))
        NavigationGuidanceRepository.onPosted(context, sbn(MAPS, guidanceNotification(ongoing = false)))
        NavigationGuidanceRepository.onPosted(context, sbn(MAPS, guidanceNotification(category = null)))

        assertNull(NavigationGuidanceRepository.guidance.value)
    }

    @Test
    fun `a navigation-named channel counts when the category is unset`() {
        NavigationGuidanceRepository.onPosted(
            context,
            sbn(MAPS, guidanceNotification(category = null, channel = "navigation_guidance")),
        )

        assertNotNull(NavigationGuidanceRepository.guidance.value)
    }

    @Test
    fun `removing the guidance notification clears the card`() {
        val posted = sbn(MAPS, guidanceNotification())
        NavigationGuidanceRepository.onPosted(context, posted)
        NavigationGuidanceRepository.onRemoved(posted)

        assertNull(NavigationGuidanceRepository.guidance.value)
    }

    @Test
    fun `a reconnect snapshot keeps the newest guidance`() {
        NavigationGuidanceRepository.onSnapshot(
            context,
            listOf(
                sbn(MAPS, guidanceNotification(title = "1 km"), postTime = 1),
                sbn("com.waze", guidanceNotification(title = "200 m"), postTime = 2),
            ),
        )

        assertEquals("200 m", NavigationGuidanceRepository.guidance.value?.distance)
    }

    @Test
    fun `blank fields map to null`() {
        val guidance = guidanceFrom(MAPS, title = " ", text = "", subText = null, maneuver = null, contentIntent = null)

        assertNull(guidance.distance)
        assertNull(guidance.instruction)
        assertNull(guidance.eta)
    }

    private fun guidanceNotification(
        title: String = "300 m",
        ongoing: Boolean = true,
        category: String? = Notification.CATEGORY_NAVIGATION,
        channel: String = "maps",
    ): Notification =
        NotificationCompat
            .Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(title)
            .setContentText("Turn right onto Av. Paulista")
            .setSubText("12 min · 4.2 km")
            .setOngoing(ongoing)
            .apply { category?.let(::setCategory) }
            .build()

    private fun sbn(
        packageName: String,
        notification: Notification,
        postTime: Long = 0,
    ): StatusBarNotification =
        StatusBarNotification(
            packageName,
            packageName,
            1,
            null,
            0,
            0,
            0,
            notification,
            Process.myUserHandle(),
            postTime,
        )
}

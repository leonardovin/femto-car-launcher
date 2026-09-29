package io.github.seijikohara.femto.data.privileged

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HeadUnitSetupTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val commands = mutableListOf<List<String>>()

    private fun setup(failing: (List<String>) -> Boolean = { false }) =
        HeadUnitSetup(context) { command ->
            commands += command.toList()
            ShellResult("", if (failing(command.toList())) 1 else 0)
        }

    @Test
    fun `grant launcher access allows the notification listener and exempts the battery`() =
        runTest {
            val report = setup().grantLauncherAccess()

            assertTrue(report.succeeded)
            assertTrue(commands.any { it.take(3) == listOf("cmd", "notification", "allow_listener") })
            assertTrue(commands.any { it == listOf("dumpsys", "deviceidle", "whitelist", "+${context.packageName}") })
            assertEquals(commands.size, report.applied)
        }

    @Test
    fun `a failing step is reported by label`() =
        runTest {
            val report = setup(failing = { it.firstOrNull() == "dumpsys" }).grantLauncherAccess()

            assertFalse(report.succeeded)
            assertEquals(listOf("battery optimisation"), report.failed)
        }

    @Test
    fun `make default home elects this package's home activity`() =
        runTest {
            val home = ComponentName(context.packageName, "io.github.seijikohara.femto.MainActivity")
            shadowOf(context.packageManager).apply {
                addActivityIfNotPresent(home)
                addIntentFilterForActivity(
                    home,
                    IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) },
                )
            }

            assertTrue(setup().makeDefaultHome())
            assertEquals(
                listOf("cmd", "package", "set-home-activity", home.flattenToString()),
                commands.single(),
            )
        }
}

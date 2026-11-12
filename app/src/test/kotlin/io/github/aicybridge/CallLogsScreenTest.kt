package io.github.aicybridge

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-440dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CallLogsScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<CallLogsActivity>
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var failedId: String
    @Before fun seedHistory() {
        app.deleteDatabase("call-logs.db")
        app.getSharedPreferences("call_logging", Context.MODE_PRIVATE).edit().clear().commit()
        CallLogStore(app).use { store ->
            val success = CallLogStoreTest.start("safe-model-vision")
            store.start(success); store.finish(CallLogStoreTest.end(success, "response", 200, ""))
            val failed = CallLogStoreTest.start("safe-model-tools")
            failed.putString("api_key", "private-key-never-exported")
            failed.putString("response", "private-response-never-exported")
            failedId = failed.getString("id")!!
            store.start(failed); store.finish(CallLogStoreTest.end(failed, "response", 401, "http"))
        }
        scenario = ActivityScenario.launch(CallLogsActivity::class.java)
    }
    @After fun close() { scenario.close() }

    @Test fun historyCanBeCopiedFilteredAndClearedWithoutExportingExtraFields() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("copy_call_logs").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("最近 2 条 · 最多保留 200 条").fetchSemanticsNodes().isNotEmpty() }
        capture("call-logs-light.png")
        compose.onNodeWithTag("copy_call_logs").performClick()
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        compose.waitUntil(10_000) { clipboard.hasPrimaryClip() }
        val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
        Assert.assertTrue(text.contains("HTTP：401"))
        Assert.assertFalse(text.contains("private-key-never-exported"))
        Assert.assertFalse(text.contains("private-response-never-exported"))
        compose.onNodeWithTag("failed_call_logs").performClick()
        compose.onNodeWithTag("call_logs_list").performScrollToNode(hasTestTag("call_log_$failedId"))
        compose.onNodeWithText("HTTP 401 · 123 ms").assertExists()
        compose.onNodeWithText("safe-model-vision").assertDoesNotExist()
        compose.onNodeWithTag("call_logs_list").performScrollToIndex(0)
        compose.onNodeWithTag("clear_call_logs").performClick()
        compose.onNodeWithTag("confirm_clear_call_logs").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("empty_call_logs").fetchSemanticsNodes().isNotEmpty() }
        CallLogStore(app).use { Assert.assertTrue(it.read().isEmpty()) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w393dp-h852dp-night-440dpi")
    fun loggingTogglePersistsAndHistorySurvivesOpeningANewDarkModeActivity() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("最近 2 条 · 最多保留 200 条").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("call_logging_enabled").assertIsOn().performClick()
        compose.waitUntil(10_000) { CallLogStore(app).use { !it.isEnabled } }
        scenario.close(); scenario = ActivityScenario.launch(CallLogsActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodesWithText("最近 2 条 · 最多保留 200 条").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("call_logging_enabled").assertIsOff()
        capture("call-logs-dark.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File("build/ui-previews", name)
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

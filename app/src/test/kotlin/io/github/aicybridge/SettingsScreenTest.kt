package io.github.aicybridge

import android.graphics.Bitmap
import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-440dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<SettingsActivity>

    @Before fun openCleanProfile() {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        org.junit.Assert.assertFalse(ConfigStore(RuntimeEnvironment.getApplication()).read().hasSavedConfig)
        scenario = ActivityScenario.launch(SettingsActivity::class.java)
    }

    @After fun closeScreen() { scenario.close() }

    @Test fun savedChannelSurvivesDisablingAndOpeningANewActivity() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("api_url").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("api_url").performScrollTo().performTextClearance()
        compose.onNodeWithTag("api_url").performTextInput("https://example.test/v1")
        compose.onNodeWithTag("model_id").performScrollTo().performTextInput("saved-test-model")
        compose.onNodeWithTag("save_profile").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("saved_profile").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("saved_model").assertTextEquals("saved-test-model")
        compose.onNodeWithTag("bridge_enabled").performScrollTo().assertIsOn().performClick()
        compose.waitUntil(20_000) { !ConfigStore(RuntimeEnvironment.getApplication()).read().enabled }
        compose.onNodeWithTag("saved_profile").assertExists()
        scenario.close()
        scenario = ActivityScenario.launch(SettingsActivity::class.java)
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("saved_profile").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("saved_url").assertTextEquals("https://example.test/v1")
        compose.onNodeWithTag("saved_model").assertTextEquals("saved-test-model")
        compose.onNodeWithTag("bridge_enabled").performScrollTo().assertIsOff()
        capture("settings-saved-light.png")
    }

    @Test fun initialScreenIsUsableAndDoesNotPretendAnEmptyChannelIsSaved() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("api_url").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("saved_profile").assertDoesNotExist()
        compose.onNodeWithTag("bridge_enabled").assertIsOff()
        compose.onNodeWithTag("save_profile").performScrollTo().assertHasClickAction()
        compose.onNodeWithTag("bridge_enabled").performScrollTo()
        capture("settings-empty-light.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w393dp-h852dp-night-440dpi")
    fun darkModeKeepsTheEditorAndSaveActionUsable() {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("api_url").fetchSemanticsNodes().isNotEmpty() }
        org.junit.Assert.assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_YES,
            RuntimeEnvironment.getApplication().resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        compose.onNodeWithTag("save_profile").performScrollTo().assertHasClickAction()
        compose.onNodeWithTag("bridge_enabled").performScrollTo()
        capture("settings-empty-dark.png")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File("build/ui-previews", name)
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

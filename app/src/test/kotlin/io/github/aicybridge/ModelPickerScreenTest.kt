package io.github.aicybridge

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-440dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelPickerScreenTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<SettingsActivity>
    private var state by mutableStateOf(SettingsUiState())

    @Before fun openCatalogue() {
        RuntimeEnvironment.getApplication().getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        state = SettingsUiState(loading = false, editing = true, model = "manual-model", modelPickerOpen = true,
            availableModels = listOf("fast-model", "tools-model", "vision-model"))
        scenario = ActivityScenario.launch(SettingsActivity::class.java)
        scenario.onActivity { activity ->
            activity.setContent {
                MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                    Scaffold {
                        ModelPicker(state, onSearch = { state = state.copy(modelQuery = it) },
                            onSelect = { state = state.copy(model = it, modelPickerOpen = false) },
                            onClose = { state = state.copy(modelPickerOpen = false) })
                    }
                }
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("model_search").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun close() { scenario.close() }

    @Test fun catalogueCanBeSearchedAndASelectionReturnsTheExactModelId() {
        capture("model-picker-light.png")
        compose.onNodeWithTag("model_search").performTextInput("VISION")
        compose.onNodeWithText("vision-model").assertExists()
        compose.onNodeWithText("tools-model").assertDoesNotExist()
        compose.onNodeWithText("vision-model").performClick()
        compose.runOnIdle {
            Assert.assertEquals("vision-model", state.model)
            Assert.assertFalse(state.modelPickerOpen)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w393dp-h852dp-night-440dpi")
    fun closingTheDarkModePickerKeepsTheManualModel() {
        capture("model-picker-dark.png")
        compose.onNodeWithTag("close_model_picker").performClick()
        compose.runOnIdle {
            Assert.assertEquals("manual-model", state.model)
            Assert.assertFalse(state.modelPickerOpen)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File("build/ui-previews", name); file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

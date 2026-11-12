package io.github.aicybridge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BridgeApp(settings: SettingsViewModel, logs: CallLogsViewModel,
                       onOpenAicy: () -> Unit, onCopyLogs: (List<CallLogStore.Entry>) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val select: (Int) -> Unit = { destination ->
        focus.clearFocus(force = true)
        keyboard?.hide()
        page = destination
    }
    BackHandler(enabled = page == 1) { select(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        containerColor = MiuixTheme.colorScheme.surface,
        bottomBar = {
            if (!keyboardVisible) Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                FloatingNavigationBar(
                    modifier = Modifier.testTag("capsule_navigation"),
                    shadowElevation = 10.dp, showDivider = false,
                ) {
                    FloatingNavigationBarItem(selected = page == 0, onClick = { select(0) }, icon = HomeIcon, label = "主页",
                        modifier = Modifier.width(104.dp).testTag("nav_home").semantics { selected = page == 0; role = Role.Tab })
                    FloatingNavigationBarItem(selected = page == 1, onClick = { select(1) }, icon = LogsIcon, label = "日志",
                        modifier = Modifier.width(104.dp).testTag("nav_logs").semantics { selected = page == 1; role = Role.Tab })
                }
            }
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()) {
            pages.SaveableStateProvider(page) {
                if (page == 0) BridgeHomeScreen(settings, onOpenAicy)
                else CallLogsScreen(logs, onCopyLogs)
            }
        }
    }
}

private val HomeIcon: ImageVector = ImageVector.Builder("BridgeHome", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(3f, 10.5f); lineTo(12f, 3f); lineTo(21f, 10.5f)
        moveTo(5.5f, 9f); lineTo(5.5f, 20f); lineTo(10f, 20f); lineTo(10f, 14f)
        lineTo(14f, 14f); lineTo(14f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9f)
    }
}.build()

private val LogsIcon: ImageVector = ImageVector.Builder("BridgeLogs", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7f, 3f); lineTo(17f, 3f); quadTo(20f, 3f, 20f, 6f); lineTo(20f, 18f)
        quadTo(20f, 21f, 17f, 21f); lineTo(7f, 21f); quadTo(4f, 21f, 4f, 18f)
        lineTo(4f, 6f); quadTo(4f, 3f, 7f, 3f); close()
        moveTo(8f, 8f); lineTo(16f, 8f)
        moveTo(8f, 12f); lineTo(16f, 12f)
        moveTo(8f, 16f); lineTo(13f, 16f)
    }
}.build()

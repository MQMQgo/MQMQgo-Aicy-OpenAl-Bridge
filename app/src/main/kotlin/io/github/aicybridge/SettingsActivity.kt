package io.github.aicybridge

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import java.net.URI
import java.text.DateFormat
import java.util.Date

class SettingsActivity : ComponentActivity() {
    private lateinit var model: SettingsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        model = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))[SettingsViewModel::class.java]
        val logs = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory(application))[CallLogsViewModel::class.java]
        setContent {
            val dark = isSystemInDarkTheme()
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                BridgeApp(model, logs, onOpenAicy = {
                    val launch = packageManager.getLaunchIntentForPackage("com.meizu.voiceassistant")
                    if (launch == null) model.showMessage("此设备尚未安装 Aicy 助手。")
                    else startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }, onCopyLogs = { entries ->
                    try {
                        getSystemService(ClipboardManager::class.java).setPrimaryClip(
                            ClipData.newPlainText("Aicy 调用日志", formatCallLogs(entries)))
                        logs.message("已复制请求概况，不含密钥和对话正文。")
                    } catch (error: RuntimeException) { logs.message("复制失败，请重试。") }
                })
            }
        }
    }
    override fun onResume() { super.onResume(); if (::model.isInitialized) model.refreshStatus() }
}

@Composable
internal fun BridgeHomeScreen(viewModel: SettingsViewModel, onOpenAicy: () -> Unit, bottomPadding: Dp = 30.dp) {
    val state = viewModel.state
    val colors = MiuixTheme.colorScheme
    val savedColor = if (isSystemInDarkTheme()) Color(0xFF76D9B4) else Color(0xFF258260)
    val focus = LocalFocusManager.current
    Column(
        Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }
            .verticalScroll(rememberScrollState())
            .widthIn(max = 640.dp).padding(horizontal = 20.dp).padding(top = 22.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(colors.primary), contentAlignment = Alignment.Center) {
                BridgeGlyph(Color.White, Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Aicy Bridge", fontSize = 29.sp, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text("让 Aicy 用上你的模型", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
            }
            Pill("API 102", colors.primary, colors.tertiaryContainer)
        }
        Spacer(Modifier.height(3.dp))
        Card(insideMargin = PaddingValues(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("第三方模型", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(if (state.enabled) "接管已开启" else "使用你自己的 API 渠道", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                }
                Switch(checked = state.enabled, onCheckedChange = viewModel::toggle,
                    enabled = state.configured && state.keyReadable && !state.busy,
                    modifier = Modifier.testTag("bridge_enabled"))
            }
            Spacer(Modifier.height(14.dp))
            Text(if (state.configured) "关闭接管也会保留已保存的渠道。" else "保存渠道后即可开启，配置只保存在此设备。",
                fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        }
        if (state.loading) {
            Card(insideMargin = PaddingValues(22.dp), modifier = Modifier.fillMaxWidth()) { Text("正在读取已保存的渠道…") }
        } else {
            if (state.message.isNotEmpty()) {
                Notice(state.message, state.isError)
                if (!state.keyReadable) TextButton(text = "重新读取密钥", onClick = viewModel::loadProfile)
            }
            if (state.configured) {
                SectionHeading("我的渠道", "已保存到本机")
                Card(insideMargin = PaddingValues(20.dp), modifier = Modifier.fillMaxWidth().testTag("saved_profile")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(colors.tertiaryContainer), contentAlignment = Alignment.Center) {
                            BridgeGlyph(colors.primary, Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(runCatching { URI(state.savedUrl).host }.getOrNull() ?: "OpenAI 兼容渠道",
                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(if (state.enabled) "正在使用" else "已停用 · 配置已保留", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                        }
                        Pill("已保存", savedColor, Color(0x15258260))
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(state.savedModel, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("saved_model"))
                    Spacer(Modifier.height(5.dp))
                    Text(state.savedUrl, fontSize = 12.sp, color = colors.onSurfaceVariantSummary, maxLines = 2,
                        modifier = Modifier.testTag("saved_url"))
                    Spacer(Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (!state.keyReadable) "密钥待恢复" else if (state.hasKey) "密钥已加密保存" else "未设置密钥",
                            fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                        if (!state.editing) TextButton("编辑渠道", onClick = viewModel::edit, enabled = !state.busy && !state.testing,
                            modifier = Modifier.testTag("edit_profile"))
                    }
                }
            }
            if (state.editing) {
                SectionHeading(if (state.configured) "编辑渠道" else "添加你的渠道", "OpenAI 兼容接口")
                Card(insideMargin = PaddingValues(16.dp), modifier = Modifier.fillMaxWidth()) {
                    TextField(value = state.url, onValueChange = viewModel::changeUrl, label = "API 地址",
                        modifier = Modifier.fillMaxWidth().testTag("api_url"), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), enabled = !state.busy)
                    Spacer(Modifier.height(8.dp))
                    Text("填写 API 前缀，例如 https://api.openai.com/v1", fontSize = 11.sp,
                        color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 8.dp))
                    Spacer(Modifier.height(14.dp))
                    var reveal by remember { mutableStateOf(false) }
                    TextField(value = state.key, onValueChange = viewModel::changeKey,
                        label = if (state.hasKey) "API Key（已保存，留空不修改）" else "API Key",
                        modifier = Modifier.fillMaxWidth().testTag("api_key"), singleLine = true,
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        enabled = !state.busy, trailingIcon = {
                            TextButton(if (reveal) "隐藏" else "显示", onClick = { reveal = !reveal },
                                insideMargin = PaddingValues(horizontal = 8.dp), minWidth = 38.dp, minHeight = 34.dp)
                        })
                    Spacer(Modifier.height(14.dp))
                    TextField(value = state.model, onValueChange = viewModel::changeModel, label = "模型 ID",
                        modifier = Modifier.fillMaxWidth().testTag("model_id"), singleLine = true, enabled = !state.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(if (state.fetchingModels) "获取中…" else "获取模型", onClick = { focus.clearFocus(); viewModel.fetchModels() },
                            enabled = !state.fetchingModels && !state.testing && !state.busy,
                            modifier = Modifier.weight(1f).testTag("fetch_models"))
                        if (state.fetchingModels) TextButton("取消获取", onClick = viewModel::cancelModels,
                            modifier = Modifier.weight(1f).testTag("cancel_fetch_models"))
                        else if (state.availableModels.isNotEmpty()) TextButton("选择模型（${state.availableModels.size}）", onClick = viewModel::openModelPicker,
                            enabled = !state.busy, modifier = Modifier.weight(1f).testTag("select_model"))
                    }
                    if (state.modelsMessage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(state.modelsMessage, fontSize = 12.sp, color = if (state.modelsError) colors.error else colors.onSurfaceVariantSummary)
                    }
                    Spacer(Modifier.height(18.dp))
                    TextButton(text = if (state.busy) "正在保存…" else if (state.configured) "保存修改" else "保存并启用",
                        onClick = { focus.clearFocus(); viewModel.save() }, colors = ButtonDefaults.textButtonColorsPrimary(),
                        enabled = !state.busy && !state.testing && !state.fetchingModels, modifier = Modifier.fillMaxWidth().testTag("save_profile"))
                    if (state.configured) {
                        Spacer(Modifier.height(12.dp))
                        TextButton("取消编辑", onClick = { focus.clearFocus(); viewModel.cancelEdit() },
                            enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            SectionHeading("连接测试", "仅发送一条测试消息")
            Card(insideMargin = PaddingValues(20.dp), modifier = Modifier.fillMaxWidth()) {
                Text("确认服务商和模型可用", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("测试可能产生少量 API 费用。Agent 需要工具调用能力，读屏任务还需要视觉能力。",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(14.dp))
                TextButton(if (state.testing) "连接中…" else "测试连接", onClick = viewModel::testConnection,
                    enabled = !state.testing && !state.busy && !state.fetchingModels, modifier = Modifier.fillMaxWidth().testTag("test_connection"))
                if (state.testing) {
                    Spacer(Modifier.height(12.dp))
                    TextButton("取消测试", onClick = viewModel::cancelTest, modifier = Modifier.fillMaxWidth())
                }
                if (state.testResult.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.testResult, fontSize = 13.sp, color = if (state.testError) colors.error else savedColor)
                }
            }
            SectionHeading("运行状态", "Aicy 13.1.1")
            Card(insideMargin = PaddingValues(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(
                        if (state.status == "bridge_enabled") Color(0xFF32A873) else colors.outline))
                    Spacer(Modifier.width(9.dp))
                    Text(StatusText.describe(state.status), fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TextButton("刷新", onClick = viewModel::refreshStatus, minWidth = 44.dp, insideMargin = PaddingValues(8.dp))
                }
                if (state.statusTime > 0) Text("上次报告 · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.statusTime)),
                    fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(10.dp))
                Text("保存或切换后，请强行停止并重新打开 Aicy。首次启用模块时，请按框架提示重启。",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(12.dp))
                TextButton("打开 Aicy", onClick = onOpenAicy, modifier = Modifier.fillMaxWidth())
            }
            Text("密钥在本机加密存储。对话会发送至你设置的服务商。", fontSize = 11.sp,
                color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 8.dp))
            Text("Aicy OpenAI Bridge  ${BuildConfig.VERSION_NAME}  ·  Miuix", fontSize = 11.sp,
                color = colors.onSurfaceVariantSummary, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
        }
    }
    ModelPicker(state, viewModel::searchModels, viewModel::chooseModel, viewModel::closeModelPicker)
}

@Composable
private fun SectionHeading(title: String, summary: String) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
        Text(summary, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun Pill(text: String, foreground: Color, background: Color) {
    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(background).padding(horizontal = 9.dp, vertical = 5.dp)) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = foreground)
    }
}

@Composable
private fun Notice(message: String, error: Boolean) {
    val colors = MiuixTheme.colorScheme
    Card(colors = CardDefaults.defaultColors(color = if (error) colors.errorContainer else colors.tertiaryContainer),
        insideMargin = PaddingValues(15.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, fontSize = 13.sp, color = if (error) colors.error else colors.primary)
    }
}

@Composable
private fun BridgeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.085f
        drawLine(color, Offset(size.width * .28f, size.height * .5f), Offset(size.width * .72f, size.height * .5f), stroke, StrokeCap.Round)
        drawCircle(color, size.width * .15f, Offset(size.width * .22f, size.height * .5f), style = Stroke(stroke))
        drawCircle(color, size.width * .15f, Offset(size.width * .78f, size.height * .5f), style = Stroke(stroke))
    }
}

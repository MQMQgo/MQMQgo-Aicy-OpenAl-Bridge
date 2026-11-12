package io.github.aicybridge

import android.app.Application
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import java.text.DateFormat
import java.util.Date

data class CallLogsState(
    val entries: List<CallLogStore.Entry> = emptyList(), val enabled: Boolean = true,
    val loading: Boolean = true, val busy: Boolean = false, val message: String = "",
)

class CallLogsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = CallLogStore(application)
    var state by mutableStateOf(CallLogsState())
        private set

    suspend fun refresh() {
        if (state.busy) return
        try {
            val result = withContext(Dispatchers.IO) { store.read() to store.isEnabled() }
            state = state.copy(entries = result.first, enabled = result.second, loading = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { state = state.copy(loading = false, message = "读取日志失败，请重试；模型请求不受影响。") }
    }
    fun refreshNow() { viewModelScope.launch { refresh() } }
    fun message(value: String) { state = state.copy(message = value) }
    fun toggle(enabled: Boolean) = change {
        if (!store.setEnabled(enabled)) throw java.io.IOException()
    }
    fun clear() = change { store.clear() }
    private fun change(action: () -> Unit) {
        if (state.busy) return
        state = state.copy(busy = true, message = "")
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { state = state.copy(message = "操作未完成，请重试。") }
            state = state.copy(busy = false)
            refresh()
        }
    }
    override fun onCleared() { store.close() }
}

@Composable
internal fun CallLogsScreen(model: CallLogsViewModel, onCopy: (List<CallLogStore.Entry>) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, model) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { model.refresh(); delay(2000) }
        }
    }
    val state = model.state
    val colors = MiuixTheme.colorScheme
    var failuresOnly by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val visible = if (failuresOnly) state.entries.filter { it.isFailure } else state.entries
    LazyColumn(Modifier.fillMaxSize().testTag("call_logs_list").semantics { testTagsAsResourceId = true },
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("调用日志", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                    Text("最近 ${state.entries.size} 条 · 最多保留 200 条", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                }
            }
        }
        item {
            Card(insideMargin = PaddingValues(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("记录调用", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text("关闭后保留已有记录", fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                    }
                    Switch(state.enabled, onCheckedChange = model::toggle, enabled = !state.busy,
                        modifier = Modifier.testTag("call_logging_enabled"))
                }
                Spacer(Modifier.height(12.dp))
                Text("仅保存时间、服务商域名、模型、状态与耗时，不记录密钥、对话或工具参数。",
                    fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("刷新", onClick = model::refreshNow, enabled = !state.busy, modifier = Modifier.weight(1f).testTag("refresh_call_logs"))
                TextButton("复制", onClick = { onCopy(visible) }, enabled = visible.isNotEmpty(), modifier = Modifier.weight(1f).testTag("copy_call_logs"))
                TextButton("清空", onClick = { confirmClear = true }, enabled = state.entries.isNotEmpty() && !state.busy,
                    modifier = Modifier.weight(1f).testTag("clear_call_logs"))
            }
        }
        if (confirmClear) item {
            Card(insideMargin = PaddingValues(18.dp), modifier = Modifier.fillMaxWidth()) {
                Text("清空全部调用日志？API 配置会保留。", fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("取消", onClick = { confirmClear = false }, modifier = Modifier.weight(1f))
                    TextButton("确认清空", onClick = { confirmClear = false; model.clear() },
                        enabled = !state.busy, modifier = Modifier.weight(1f).testTag("confirm_clear_call_logs"))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("全部", onClick = { failuresOnly = false }, modifier = Modifier.weight(1f).testTag("all_call_logs"),
                    colors = if (!failuresOnly) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
                TextButton("失败", onClick = { failuresOnly = true }, modifier = Modifier.weight(1f).testTag("failed_call_logs"),
                    colors = if (failuresOnly) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
            }
        }
        if (state.message.isNotEmpty()) item { Text(state.message, fontSize = 13.sp, color = colors.primary) }
        item {
            Text("Aicy 流式请求的耗时统计到响应头到达，后续输出和 Agent 任务可能继续运行。连接测试记录完整请求。",
                fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
        }
        if (state.loading) item { Text("正在读取日志…") }
        else if (visible.isEmpty()) item {
            Card(insideMargin = PaddingValues(24.dp), modifier = Modifier.fillMaxWidth().testTag("empty_call_logs")) {
                Text(if (failuresOnly) "暂无失败记录" else "暂无调用记录", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("使用 Aicy 发起对话，或在模块中测试连接后，可在这里查看请求概况。", fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
            }
        }
        items(visible, key = { it.id }) { entry ->
            Card(insideMargin = PaddingValues(18.dp), modifier = Modifier.fillMaxWidth().testTag("call_log_${entry.id}")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.sourceLabel() + " · " + entry.stageLabel(), fontSize = 12.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                    Text(entry.stateLabel(), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        color = if (entry.isFailure) colors.error else if (entry.state == "started") colors.primary
                        else if (isSystemInDarkTheme()) Color(0xFF76D9B4) else Color(0xFF258260))
                }
                Spacer(Modifier.height(9.dp))
                if (entry.model.isNotEmpty()) Text(entry.model, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                if (entry.host.isNotEmpty()) Text(entry.host, fontSize = 12.sp, color = colors.onSurfaceVariantSummary)
                Spacer(Modifier.height(9.dp))
                val details = listOfNotNull(if (entry.httpStatus > 0) "HTTP ${entry.httpStatus}" else null,
                    if (entry.elapsedMs >= 0) "${entry.elapsedMs} ms" else null).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, fontSize = 13.sp)
                val error = CallLogPrivacy.describeError(entry.error)
                if (error.isNotEmpty()) Text(error, fontSize = 13.sp, color = colors.error)
                Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(entry.startedAt)),
                    fontSize = 11.sp, color = colors.onSurfaceVariantSummary)
            }
        }
    }
}

internal fun formatCallLogs(entries: List<CallLogStore.Entry>): String = buildString {
    appendLine("Aicy OpenAI Bridge ${BuildConfig.VERSION_NAME} · 调用日志（仅元数据）")
    entries.forEach { entry ->
        appendLine()
        appendLine(DateFormat.getDateTimeInstance().format(Date(entry.startedAt)) + " · " + entry.sourceLabel() + " · " + entry.stageLabel())
        appendLine("模型：${entry.model}  域名：${entry.host}")
        appendLine("状态：${entry.stateLabel()}  HTTP：${entry.httpStatus.takeIf { it > 0 } ?: "—"}  耗时：${entry.elapsedMs.takeIf { it >= 0 } ?: "—"} ms")
        if (entry.error.isNotEmpty()) appendLine(CallLogPrivacy.describeError(entry.error))
        appendLine("记录：${entry.id}")
    }
}

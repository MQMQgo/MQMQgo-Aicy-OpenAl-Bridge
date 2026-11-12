package io.github.aicybridge

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.aicybridge.core.OpenAiClient
import io.github.aicybridge.core.OpenAiException
import io.github.aicybridge.core.ProtocolException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.GeneralSecurityException

data class SettingsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val configured: Boolean = false,
    val enabled: Boolean = false,
    val hasKey: Boolean = false,
    val keyReadable: Boolean = true,
    val savedUrl: String = "",
    val savedModel: String = "",
    val savedAt: Long = 0,
    val editing: Boolean = false,
    val url: String = "https://api.openai.com/v1",
    val model: String = "",
    val key: String = "",
    val message: String = "",
    val isError: Boolean = false,
    val testing: Boolean = false,
    val testResult: String = "",
    val testError: Boolean = false,
    val status: String = "waiting",
    val statusTime: Long = 0,
)

/** The editor lives in memory; only an acknowledged, committed profile is shown as saved. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConfigStore(application)
    var state by mutableStateOf(SettingsUiState())
        private set
    @Volatile private var activeCall: OpenAiClient.Call? = null

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.read() }
            state = state.copy(
                loading = false, configured = saved.hasSavedConfig, enabled = saved.enabled,
                hasKey = saved.hasApiKey, keyReadable = saved.keyReadable,
                savedUrl = saved.baseUrl, savedModel = saved.model, savedAt = saved.savedAt,
                editing = !saved.hasSavedConfig, url = saved.baseUrl, model = saved.model, key = "",
                status = store.statusCode(), statusTime = store.statusTime(),
                message = if (!saved.keyReadable) "渠道已保留，但系统暂时无法读取密钥。请解锁手机后重试，或替换密钥。" else "",
                isError = !saved.keyReadable,
            )
        }
    }

    fun edit() { state = state.copy(editing = true, url = state.savedUrl, model = state.savedModel, key = "", message = "", testResult = "") }
    fun cancelEdit() { state = state.copy(editing = false, key = "", message = "", testResult = "") }
    fun changeUrl(value: String) { state = state.copy(url = value, testResult = "", message = "") }
    fun changeModel(value: String) { state = state.copy(model = value, testResult = "", message = "") }
    fun changeKey(value: String) { state = state.copy(key = value, testResult = "", message = "") }

    fun save() {
        if (state.busy || state.loading) return
        val draft = state
        state = state.copy(busy = true, message = "")
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    store.saveProfile(if (draft.configured) draft.enabled else true,
                        draft.url.trim(), draft.key.trim(), draft.model.trim(),
                        draft.hasKey && draft.key.isBlank())
                    store.read()
                }
                state = state.copy(busy = false, configured = saved.hasSavedConfig, enabled = saved.enabled,
                    hasKey = saved.hasApiKey, keyReadable = saved.keyReadable, savedUrl = saved.baseUrl,
                    savedModel = saved.model, savedAt = saved.savedAt, url = saved.baseUrl,
                    model = saved.model, key = "", editing = false, isError = false,
                    message = "渠道已保存。请强行停止并重新打开 Aicy，让配置生效。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                state = state.copy(busy = false, isError = true, message = when (error) {
                    is ProtocolException -> "请填写有效的 HTTPS 地址和模型 ID，密钥不能包含换行。"
                    is IllegalArgumentException -> "更换服务商地址时，请明确填写新的 API Key。"
                    is GeneralSecurityException -> "密钥暂时不可读取，原渠道已保留。请重试或填写新的密钥。"
                    else -> "保存未完成，原渠道仍然保留。请重试。"
                })
            }
        }
    }

    fun toggle(enabled: Boolean) {
        if (state.busy || !state.configured) return
        state = state.copy(busy = true, message = "")
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { store.setEnabled(enabled); store.read() }
                state = state.copy(busy = false, enabled = saved.enabled, isError = false,
                    message = if (enabled) "已启用。请重新启动 Aicy 生效。" else "已停用，渠道和密钥仍已保存。请重新启动 Aicy 生效。")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                state = state.copy(busy = false, message = "切换未完成，已保存的渠道未改变。请重试。", isError = true)
            }
        }
    }

    fun refreshStatus() { state = state.copy(status = store.statusCode(), statusTime = store.statusTime()) }
    fun showMessage(message: String) { state = state.copy(message = message, isError = false) }
    fun cancelTest() { activeCall?.cancel() }

    fun testConnection() {
        if (state.testing || state.busy || state.loading) return
        val draft = state
        state = state.copy(testing = true, testResult = "", message = "")
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                var trace: CallLogReporter.Trace? = null
                try {
                    val saved = store.read()
                    val url = if (draft.editing) draft.url.trim() else draft.savedUrl
                    val model = if (draft.editing) draft.model.trim() else draft.savedModel
                    val entered = if (draft.editing) draft.key.trim() else ""
                    if (entered.isEmpty() && saved.hasApiKey) {
                        if (!saved.keyReadable) throw GeneralSecurityException()
                        val a = URI(saved.baseUrl); val b = URI(url)
                        if (a.host == null || !a.host.equals(b.host, true)
                            || (if (a.port < 0) 443 else a.port) != (if (b.port < 0) 443 else b.port)) throw IllegalArgumentException()
                    }
                    val key = entered.ifEmpty { saved.apiKey }
                    val config = OpenAiClient.Config(url, key, model, 15000, 45000)
                    trace = runCatching { CallLogReporter(getApplication<Application>(), url, model, key).begin("test", "test") }.getOrNull()
                    val call = OpenAiClient(config).newCall(
                        JSONObject().put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "请只回复 OK。"))))
                    activeCall = call
                    val choices = call.execute().optJSONArray("choices")
                    if (choices?.optJSONObject(0)?.optJSONObject("message") != null) {
                        trace?.success()
                        "连接成功，模型已返回消息。" to false
                    } else {
                        trace?.failure(ProtocolException("Missing completion message"), false)
                        "接口已连接，但没有返回有效的对话消息。" to true
                    }
                } catch (error: Exception) {
                    trace?.failure(error, activeCall?.isCancelled == true)
                    when (error) {
                        is GeneralSecurityException -> "密钥暂时不可读取，请重试或填写新的密钥。"
                        is IllegalArgumentException -> "更换服务商时请填写新的 API Key。"
                        is ProtocolException -> "请检查 HTTPS 地址、模型 ID 和接口协议。"
                        is OpenAiException -> "服务商返回 HTTP ${error.statusCode}，请检查密钥、模型和额度。"
                        is java.io.InterruptedIOException -> if (activeCall?.isCancelled == true) "连接测试已取消。" else "连接超时，请检查网络与地址。"
                        is javax.net.ssl.SSLException -> "HTTPS 证书校验失败，请检查设备时间与服务商证书。"
                        else -> "连接失败，请检查服务商地址与网络。"
                    } to true
                } finally { activeCall = null }
            }
            state = state.copy(testing = false, testResult = result.first, testError = result.second)
        }
    }

    override fun onCleared() { activeCall?.cancel(); super.onCleared() }
}

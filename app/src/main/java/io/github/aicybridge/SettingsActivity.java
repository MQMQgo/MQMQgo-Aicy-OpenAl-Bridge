package io.github.aicybridge;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aicybridge.core.OpenAiClient;
import io.github.aicybridge.core.OpenAiException;
import io.github.aicybridge.core.ProtocolException;

public final class SettingsActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ConfigStore store;
    private Switch enabled;
    private EditText baseUrl;
    private EditText apiKey;
    private EditText model;
    private TextView message;
    private TextView hookStatus;
    private Button test;
    private volatile OpenAiClient.Call activeCall;
    private boolean loading;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ConfigStore(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(24);
        form.setPadding(padding, padding, padding, padding);
        scroll.addView(form);
        setContentView(scroll);
        TextView title = text(form, "Aicy OpenAI Bridge");
        title.setTextSize(24);
        text(form, "将 Aicy 的已适配请求交给你配置的 OpenAI 兼容接口。请先在支持 API 102 的 LSPosed 中启用模块，并将作用域设为 Aicy 助手。保存后请强行停止并重新打开 Aicy，配置才会生效。");
        enabled = new Switch(this);
        enabled.setText("启用第三方模型");
        form.addView(enabled, fullWidth());
        baseUrl = field(form, "HTTPS Base URL", "https://api.openai.com/v1", false);
        text(form, "填写服务商的 API 前缀（通常包含 /v1），也可填写完整 /chat/completions 地址。").setTextSize(12);
        apiKey = field(form, "API Key", "填写你的服务商密钥", true);
        apiKey.setSaveEnabled(false);
        apiKey.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        model = field(form, "模型 ID", "填写服务商支持的模型名称", false);
        Button save = new Button(this);
        save.setText("保存配置");
        form.addView(save, fullWidth());
        test = new Button(this);
        test.setText("测试连接");
        form.addView(test, fullWidth());
        text(form, "测试会向上述地址发送一句“请只回复 OK。”，可能产生少量 API 费用。保存配置本身不会发送请求。").setTextSize(12);
        message = text(form, "");
        hookStatus = text(form, "");
        Button refresh = new Button(this);
        refresh.setText("刷新 Hook 状态");
        form.addView(refresh, fullWidth());
        text(form, "密钥保存在本模块的私有加密存储中；启用后，Aicy 进程会读取密钥并直连服务商。聊天内容将交给你填写的服务商。").setTextSize(12);
        ConfigStore.Settings values = store.read();
        loading = true;
        enabled.setChecked(values.enabled);
        baseUrl.setText(values.baseUrl);
        apiKey.setText(values.apiKey);
        model.setText(values.model);
        loading = false;
        if (!values.keyReadable) message.setText("密钥无法解密，接管已停用。请重新填写 API Key 并保存。");
        enabled.setOnCheckedChangeListener((button, checked) -> {
            if (!loading && !checked) {
                if (store.disable()) message.setText("关闭状态已保存。请强行停止并重新打开 Aicy 生效。");
                else message.setText("关闭失败，请重试。");
            }
        });
        save.setOnClickListener(v -> save());
        test.setOnClickListener(v -> testConnection());
        refresh.setOnClickListener(v -> refreshStatus());
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        if (hookStatus != null) refreshStatus();
    }

    private OpenAiClient.Config validated() throws ProtocolException {
        String address = baseUrl.getText().toString().trim();
        String key = apiKey.getText().toString().trim();
        String selectedModel = model.getText().toString().trim();
        return new OpenAiClient.Config(address, key, selectedModel, 20000, 45000);
    }

    private void save() {
        try {
            validated();
        } catch (ProtocolException e) {
            message.setText("配置无效：请填写 HTTPS API 地址与模型 ID，密钥不能包含换行。");
            return;
        }
        try {
            store.save(enabled.isChecked(), baseUrl.getText().toString().trim(),
                    apiKey.getText().toString().trim(), model.getText().toString().trim());
            message.setText(enabled.isChecked() ? "已保存并启用。请强行停止并重新打开 Aicy 生效。" : "已保存为关闭。请强行停止并重新打开 Aicy 生效。");
        } catch (Exception e) {
            message.setText("配置保存失败：加密存储不可用，请重试。");
        }
    }

    private void testConnection() {
        final OpenAiClient.Config config;
        try {
            config = validated();
        } catch (ProtocolException e) {
            message.setText("配置无效：请填写 HTTPS API 地址与模型 ID，密钥不能包含换行。");
            return;
        }
        test.setEnabled(false);
        message.setText("正在测试连接…");
        worker.execute(() -> {
            String result;
            try {
                JSONObject request = new JSONObject().put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "user").put("content", "请只回复 OK。")));
                OpenAiClient.Call call = new OpenAiClient(config).newCall(request);
                activeCall = call;
                JSONObject response = call.execute();
                JSONArray choices = response.optJSONArray("choices");
                if (choices == null || choices.length() == 0 || choices.optJSONObject(0) == null
                        || choices.optJSONObject(0).optJSONObject("message") == null) {
                    result = "接口已连接，但未返回有效的 Chat Completions 消息。";
                } else {
                    result = "连接成功：接口已返回 Chat Completions 消息。";
                }
            } catch (Exception e) {
                result = friendlyFailure(e);
            } finally {
                activeCall = null;
            }
            final String finalResult = result;
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing()) return;
                test.setEnabled(true);
                message.setText(finalResult);
            });
        });
    }

    private static String friendlyFailure(Exception failure) {
        // Provider exception messages can contain echoed request data. Never display or log them.
        if (failure instanceof OpenAiException) {
            int status = ((OpenAiException) failure).statusCode;
            if (status == 401 || status == 403) return "连接失败：服务商拒绝访问（HTTP " + status + "），请检查密钥和模型权限。";
            if (status == 404) return "连接失败：接口或模型不存在（HTTP 404），请检查 API 前缀及模型 ID。";
            if (status == 429) return "连接失败：服务商限流或额度不足（HTTP 429）。";
            if (status > 0) return "连接失败：服务商返回 HTTP " + status + "。";
            return "连接失败：服务商返回错误。";
        }
        if (failure instanceof SocketTimeoutException) return "连接超时，请检查地址与网络后重试。";
        if (failure instanceof UnknownHostException) return "无法解析服务商地址，请检查 URL 与网络。";
        if (failure instanceof SSLException) return "HTTPS 证书校验失败，请检查服务商证书和设备时间。";
        if (failure instanceof ProtocolException) return "接口响应不符合 OpenAI Chat Completions 协议。";
        return "连接失败，请检查地址、网络和模型设置。";
    }

    private void refreshStatus() {
        long time = store.statusTime();
        String updated = time == 0 ? "" : "\n最近报告：" + DateFormat.getDateTimeInstance().format(new Date(time));
        hookStatus.setText("Hook 状态：" + StatusText.describe(store.statusCode()) + updated);
    }

    @Override protected void onDestroy() {
        OpenAiClient.Call call = activeCall;
        if (call != null) call.cancel();
        worker.shutdownNow();
        super.onDestroy();
    }

    private TextView text(LinearLayout form, String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(15);
        view.setPadding(0, dp(8), 0, dp(8));
        form.addView(view, fullWidth());
        return view;
    }

    private EditText field(LinearLayout form, String label, String hint, boolean password) {
        text(form, label);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | (password
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
        form.addView(field, fullWidth());
        return field;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

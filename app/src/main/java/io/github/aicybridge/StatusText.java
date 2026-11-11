package io.github.aicybridge;

/** Only fixed diagnostic codes cross the provider boundary; prompts and keys never belong here. */
public final class StatusText {
    private StatusText() { }

    public static boolean isKnown(String code) {
        return "waiting".equals(code) || "hook_ready".equals(code)
                || "unsupported_version".equals(code) || "hook_error".equals(code)
                || "bridge_enabled".equals(code) || "bridge_disabled".equals(code)
                || "request_started".equals(code) || "request_completed".equals(code)
                || "request_failed".equals(code) || "cancelled".equals(code);
    }

    public static String describe(String code) {
        if ("hook_ready".equals(code)) return "已找到适配入口";
        if ("unsupported_version".equals(code)) return "当前 Aicy 版本不在适配范围，未接管请求";
        if ("hook_error".equals(code)) return "加载适配入口失败";
        if ("bridge_enabled".equals(code)) return "第三方模型接管已启用";
        if ("bridge_disabled".equals(code)) return "第三方模型接管已关闭";
        if ("request_started".equals(code)) return "正在请求第三方模型";
        if ("request_completed".equals(code)) return "第三方模型请求已完成";
        if ("request_failed".equals(code)) return "第三方模型请求失败";
        if ("cancelled".equals(code)) return "请求已取消";
        return "等待 Aicy 进程报告；请在 LSPosed 中启用模块并重启 Aicy";
    }
}

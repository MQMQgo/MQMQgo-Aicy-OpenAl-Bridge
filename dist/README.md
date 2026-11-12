# 安装包下载

[下载 Aicy OpenAI Bridge v0.1.0 / API102](aicy-openai-bridge-v0.1.0-api102.apk?raw=true)

该 APK 为独立签名的发布包，适配 Aicy 13.1.1（13001001）。要求支持现代 libxposed API102 的框架；保存接口配置后，强行停止并重新打开 Aicy。

- APK SHA-256：`8f4ae8fa5efca37d01323e588ef730ef49909c44cbad9615640c4edf856977fe`
- 发布证书 SHA-256：`85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf`
- 构建源码提交：`2f50b473061bcbdce01d92c10d9a9c4c418a1fec`

16 项本地协议测试通过，构建、lint、签名和对齐检查通过。尚未进行真实 Flyme 手机与 API102 框架的运行验证。完整说明见根目录 README 和 VALIDATION.md。

GitHub Release 附件上传接口在当前连接下返回 HTTP 401，因此将已验证的签名 APK 保存在仓库中；这里不包含发布签名私钥。

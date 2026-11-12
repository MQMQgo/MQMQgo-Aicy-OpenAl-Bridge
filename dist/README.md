# 安装包下载

[下载 Aicy OpenAI Bridge v0.2.0 / API102](aicy-openai-bridge-v0.2.0-api102.apk?raw=true)

该 APK 为签名发布包，适配 Aicy 13.1.1（13001001）。要求支持现代 libxposed API102 的框架；保存接口配置后，强行停止并重新打开 Aicy。v0.2.0 使用 Miuix 界面，修正配置保存与恢复，停用后保留渠道。

- APK 字节数：7,452,407
- APK SHA-256：`7f5e8cbaacf9101664dcaf9aab2a350f01d86fd40e485a4cfe3756f7f53f0c71`
- 发布证书 SHA-256：`85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf`
- 源码和发布包对应标签：`v0.2.0`

与本仓库 v0.1.0 发布包使用同一签名，可直接覆盖安装，保留应用数据；不要先卸载或清除模块数据。Key 默认不回显，编辑时留空保留原密钥。

27 项本地测试通过，包含协议、配置存储和 Compose 页面重开。构建、lint、签名、API102 打包和对齐检查通过。尚未进行真实 Flyme 手机与 API102 框架的运行验证。完整说明见根目录 README 和 VALIDATION.md。

[历史版本 v0.1.0](aicy-openai-bridge-v0.1.0-api102.apk?raw=true) 的校验值保留在 SHA256SUMS.txt。

GitHub Release 附件上传接口在当前连接下返回 HTTP 401，因此将已验证的签名 APK 保存在仓库中；这里不包含发布签名私钥。

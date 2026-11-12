# 安装包下载

[下载 Aicy OpenAI Bridge v0.2.1 / API102](aicy-openai-bridge-v0.2.1-api102.apk?raw=true)

该 APK 为签名发布包，适配 Aicy 13.1.1（13001001）。要求支持现代 libxposed API102 的框架；保存接口配置后，强行停止并重新打开 Aicy。v0.2.1 修复未知自定义模型触发媒体限额查询、导致 Aicy 启动闪退的问题，保留 Miuix 界面与配置恢复。

- APK 字节数：7,452,407
- APK SHA-256：`4ec8a14c8ff1d69f6a606ca64c2a5e784dd763da9ace777ea6ea48a5c2b19add`
- 发布证书 SHA-256：`85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf`
- 源码和发布包对应标签：`v0.2.1`

与本仓库旧版发布包使用同一签名，可直接覆盖安装，保留应用数据；不要先卸载或清除模块数据。已经无法启动 Aicy 时，可以先关闭模块，安装后重新启用，再强行停止并重新打开 Aicy。Key 默认不回显，编辑时留空保留原密钥。

32 项本地测试通过，包含启动资料保留与请求范围隔离的回归测试、协议、配置存储和 Compose 页面重开。构建、lint、签名、API102 打包和对齐检查通过。修复后的真实 Flyme 手机与 API102 框架运行仍待复测。完整说明见根目录 README 和 VALIDATION.md。

[历史版本 v0.1.0](aicy-openai-bridge-v0.1.0-api102.apk?raw=true) 的校验值保留在 SHA256SUMS.txt。

GitHub Release 附件上传接口在当前连接下返回 HTTP 401，因此将已验证的签名 APK 保存在仓库中；这里不包含发布签名私钥。

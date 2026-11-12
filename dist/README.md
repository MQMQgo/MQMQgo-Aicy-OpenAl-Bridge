# 安装包下载

[下载 Aicy OpenAI Bridge v0.3.0 / API102](aicy-openai-bridge-v0.3.0-api102.apk?raw=true)

该 APK 为签名发布包，适配 Aicy 13.1.1（13001001）。要求支持现代 libxposed API102 的框架；安装或保存接口配置后，强行停止并重新打开 Aicy。v0.3.0 新增调用日志、失败筛选、复制、清空和记录开关，保留启动闪退修复、Miuix 界面与配置恢复。

- APK 字节数：7,485,175
- APK SHA-256：`19bbbb69dbfb8ddceb8a8caac89ff00ce045a9a34f768a0ab61d9a2e51959238`
- 发布证书 SHA-256：`85e5d46dea60f76910f4b62c94333355f4aaccf84904d7659bf67a62284dc7cf`
- 源码和发布包对应标签：`v0.3.0`

与本仓库旧版发布包使用同一签名，可直接覆盖安装，保留应用数据；不要先卸载或清除模块数据。已经无法启动 Aicy 时，可以先关闭模块，安装后重新启用，再强行停止并重新打开 Aicy。Key 默认不回显，编辑时留空保留原密钥。

从模块设置点击“查看调用日志”。默认保存最近 200 条请求概况，不记录密钥、正文或工具参数；Aicy 流式请求的耗时统计到响应头到达。日志开关立即生效。

50 项 Gradle 本地测试及生成桥接类的 JVM 验证通过。构建、lint、签名、API102 打包和对齐检查通过。真实 Flyme、ART 与 API102 框架运行仍待验证。完整说明见根目录 README 和 VALIDATION.md。

[历史版本 v0.1.0](aicy-openai-bridge-v0.1.0-api102.apk?raw=true) 的校验值保留在 SHA256SUMS.txt。

GitHub Release 附件上传接口在当前连接下返回 HTTP 401，因此将已验证的签名 APK 保存在仓库中；这里不包含发布签名私钥。

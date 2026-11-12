# 玻璃底栏

外观参考 [Miuix v0.9.4 的液态玻璃导航示例](https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/example/shared/src/commonMain/kotlin/component/liquid/LiquidGlassNavigationBar.kt)，继续使用 Miuix 主题、图标与文字组件。胶囊默认宽 192dp、高 48dp，比 v0.3.1 的约 244dp 宽度更紧凑；字体放大时按文字高度增高。包含滑动选中指示、按压缩放、半透明材质、细边缘高光与柔和阴影。

底栏是页面上方的独立浮层，没有全宽背景或底部占位矩形。页面与列表一直绘制到屏幕底部；只有滚动内容末尾预留胶囊和系统手势所需的空间，确保最后一项可滚动到胶囊上方。Android 10+ 关闭系统导航栏额外的对比度底色，仍遵循安全区域和系统手势边距。

## 渲染与兼容

| 系统 | 效果 |
| --- | --- |
| Android 13 / API33 及以上 | RenderEffect 背景模糊 + AGSL 胶囊边缘折射与轻微色彩增强 |
| Android 12 / API31–32 | RenderEffect 背景模糊 |
| Android 8–11 / API26–30 | 较高不透明度的主题材质与高光，避免直接透出可读的背景文字 |

实现使用原有 Compose GraphicsLayer 与系统图形 API，没有新增运行依赖或提高 minSdk。较新框架类封装在独立、带版本要求的对象内，仅在对应 SDK 条件满足时调用。模糊与折射在胶囊的小块采样区域内执行；图标和文字保持清晰。

背景采样仅包含页面及不透明的主题背景，排除胶囊自身以避免递归反馈。使用 GPU 显示列表，不生成或导出截图，不使用屏幕录制权限。GraphicsLayer 随 Composition 释放；没有额外的常驻刷新或传感器监听。

主页/日志切换、草稿、滚动和筛选状态沿用 v0.3.1。输入法出现时收起底栏；日志页按系统返回键回主页。API 接管、请求协议、配置存储与日志记录实现均未修改。

本地界面验证使用 Robolectric 的原生图形渲染，不能替代真实 Flyme GPU、系统手势与厂商驱动下的效果检查。

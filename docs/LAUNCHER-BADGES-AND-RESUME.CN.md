# 26.11.31：图标莫奈角标、后台返回与资源命名

## 通知角标取色

ONextBox 的实现使用图标主色加 54% 白色遮罩，数字前景则通过 HSL 调整。原 Amalor 又用 RGB 混黑/白近似 primaryContainer，数字前景还有重复黑白判断。本次沿用其绘制接入方式，改用现有 Material Color Utilities：

- 从原始图标 Bitmap 采样，排除透明像素；使用最多 16 色的 Celebi 量化和 Score 选取种子色。
- 彩色图标生成 Tonal Spot / Spec 2025 的 `primaryContainer` 与 `onPrimaryContainer`；无彩色图标使用 MCU Monochrome，避免固定蓝色回退。
- 同时生成浅色和深色角色，按照真实 night-mode 位选取；数字直接使用对应前景角色。
- 覆盖普通圆点、数字角标、文件夹与大文件夹，并接入 ShortcutContainer 返回动画和动画图标绘制调用方。空文件夹来源不会借用上一次其他应用的颜色。

### 性能与刷新

绑定已有角标的图标时预热；首次实际绘制发生缓存缺失时也可补发任务。单个低优先级后台线程处理硬件 Bitmap 的软件拷贝、最多 32×32 像素采样及色板生成，绘制线程只解析来源、查询缓存和提交 tint。同一 Bitmap/generation 的未完成请求会合并；没有角标的文件夹预览和返回动画不触发量化。

Bitmap 和等待刷新的 View 使用弱引用键；图标替换或 generation 改变才重新计算。完成时校验来源代次，并通过主线程 invalidate 刷新等待的视图，避免依赖点击更新。首次尚未算好时保留原生颜色。种子色板与 ColorFilter 各有 128 项 LRU，切换深浅模式直接使用缓存的两套角色。

## 后台返回

旧包真机复现：从“控制中心”进入后台，再用标准 MAIN/LAUNCHER Intent 返回，同一进程内出现新的 MainActivity，页面回到主页。当前导航库的 `rememberNavBackStack` 已使用可保存的序列化状态，本次将 MainActivity 声明为 `singleTop`，避免重复创建单 Activity 导航入口。

修复后相同路径为 HOT 返回，PID 保持一致，仍停留在“控制中心”。`tests/NavigationResumeDeviceCheck.py` 可重复执行该检查。额外的 `--kill-background` 尝试未能让设备回收进程（PID 未变化），未计为进程回收恢复验证。

## 文本资源

统一了中文孤立的 `launcher_monet_dots_*` 与代码/其他语言的 `launcher_colorful_dots_*`，现均为 `launcher_notification_badge_colors_*`。同时修正过时的应用分组、返回手势、跨作用域重启、自动亮度裁剪、电量显示、禁用磁贴图标动画、主屏及网络类型设置等名称，合并重复的主屏标签和边距数值格式，补齐中文主屏重置描述。

示例：`config_style → config_apps_group`、`restart_back_gesture → restart_scopes`、`config_brightness_icon → config_auto_brightness_icon_clip_bypass`、`launcher_landing_title → launcher_custom_home_screen_title`。设置持久化键继续使用原值。

## 验证与交付范围

- 八组 JVM 检查通过，新增 `LauncherBadgePaletteCheck` 覆盖图标色相、前景对比度、透明像素、无彩色回退、缓存复用及有界淘汰。
- `StringResourcesCheck.py` 确认默认、中文、俄语 118 个键及格式参数一致；旧资源引用已清理。
- Release/R8、lintVitalRelease 和 APK v2 签名检查通过。
- 后台返回已进行上述真机验证。按用户“先交付修复”的选择，角标颜色、文件夹及返回动画的真机视觉验收留待后续；启用“通知角标取色”后需重启系统桌面。

```powershell
.\gradlew.bat -I "tests\qs-checks.init.gradle" :app:checkQsAppearance :app:assembleRelease --offline --console=plain --no-daemon "-PVERSION_NAME=26.11.31"
python tests/StringResourcesCheck.py
# 先停留在对应页面：
python tests/NavigationResumeDeviceCheck.py --adb "path/to/adb.exe" --title "控制中心"
```

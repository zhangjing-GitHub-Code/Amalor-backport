# 原生手电筒面板

## 移植入口

参考本地 `AOSP_Panels/src/dev/oos/nativepanels/FlashlightPanel.java` 和 `PanelMotion.java`，
由 `AospFlashlightHook.kt` 接管 ColorOS 手电筒详情入口，调用 ROM 自带的
`FlashlightDialogDelegate.createDialog()`。亮度与设备状态沿用系统的 Repository/ViewModel。

开关位于「配置 → 系统界面 → 其他 → 原生手电筒面板」。首次启用后重启系统界面。
已安装 Hook 的进程中，开关变更可实时切换原生/ColorOS 详情入口。

能力检测使用系统所选后置闪光灯的 Camera2 最大/默认亮度；锁屏、亮度限制和超级省电
限制由 `FlashlightPanelRules` 检查。普通磁贴点击继续使用 ColorOS 开关逻辑。

## 2026-11-14 构建

产物：`app/build/outputs/apk/release/Amalor_v1.2.16_55-release.apk`。

首次编译修复了两处移植问题：反射变量 `show` 改名为 `showDialog`，避免遮蔽面板打开方法；
`getInvoker(Method)` 按当前 libxposed API 移除不适用的泛型实参。

```powershell
.\gradlew.bat -I tests/qs-checks.init.gradle :app:checkQsAppearance :app:assembleRelease :app:lintVitalRelease "-PVERSION_NAME=1.2.16" --offline --console=plain
python tests/StringResourcesCheck.py
```

- Release/R8、14 组 JVM 回归通过，包含能力、入口与调光期间限制检查。
- 139 个默认/中文/俄文资源键与格式参数一致。
- `lintVitalRelease`：No issues found。
- APK v2 签名通过，1 个 signer，`CN=Acardia`，与设备已安装版本签名一致。
- 额外运行的 `lintRelease` 未通过：142 errors、41 warnings。错误涉及默认英文资源的
  `MissingTranslation` 识别、现有 `QsTileAppearanceHook` / `StandardSurfaceCorners` 的
  `SoonBlockedPrivateApi`，以及 `AmalorApp` 的 `UnusedBoxWithConstraintsScope`。
  完整报告在 `app/build/reports/lint-results-release.html`。

## 真机验证

设备：一加 Ace 6 / PLQ110，Android 17（API 37），ColorOS `V17.0.0`。
已覆盖安装 1.2.16 (55)，启用开关并重启 SystemUI，PID 从 7081 变为 20742。

| 项目 | 结果 |
| --- | --- |
| 长按手电筒磁贴 | 打开原生「手电筒亮度」，出现 `flashlight_title`、`flashlight_off`、`flashlight_done` |
| 亮度能力 | Camera2 报告最大 4、默认 2 |
| 拖动调光 | CameraService 确认相机 0 收到来自 SystemUI 的 1、2、3、4 档请求 |
| 关闭按钮 | 关灯并关闭面板，磁贴回到关闭状态 |
| 完成按钮 | 关闭面板并保持灯光当前开关状态 |
| 普通磁贴单击 | 开灯、关灯正常 |
| 反复打开/关闭 | 完成、返回、点击窗口外部均可关闭并再次打开 |
| 浅色/深色 | 原生面板文字、按钮及动态配色正常，截图已核对 |
| 禁用与重新启用 | 禁用后出现 ColorOS `flashlight_card`；重新启用后恢复原生面板 |
| 稳定性 | 测试期间 SystemUI PID 保持 20742，检查窗口内无该进程 crash 日志 |

可重复运行的真机检查（中文界面，先展开控制中心并关闭手电筒）：

```powershell
python tests/FlashlightPanelDeviceCheck.py --adb "C:\Users\30536\AppData\Local\Android\Sdk\platform-tools\adb.exe" --serial 3B166900YQ000000
```

该检查覆盖调光的 CameraService 日志、按钮语义、磁贴单击、重复打开与进程稳定性，
结束时将手电筒恢复为关闭。锁屏、温控限制及超级省电场景本轮仅完成规则回归。

截图保存在 `C:\Users\30536\AppData\Local\Temp\opencode\`，文件前缀为
`amalor-flashlight-1216-`（`first`、`mid`、`high`、`max`、`dark`、`disabled`、`restored`）。
验证结束时原生面板开关保持启用，手电筒关闭，系统恢复原来的浅色模式。

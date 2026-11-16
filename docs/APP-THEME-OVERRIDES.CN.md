# 设置 / 权限管理：不跟随系统主题色

两个独立开关默认关闭，位置分别为：

- **Amalor → 配置 → 设置 → 不跟随系统主题色**
- **Amalor → 配置 → 权限管理 → 不跟随系统主题色**

两项使用 `ic_own_colors`；权限管理在配置首页使用 `ic_security`。文案资源统一为 `app_theme_own_icon` / `app_theme_own_icon_desc`，中英俄均已同步。

在 LSPosed 勾选对应应用作用域，开启开关并重启该应用后生效。

| 应用 / 样本 | 作用域 | 方法 |
|---|---|---|
| 设置 17.0.0 | `com.android.settings` | `com.coui.appcompat.theme.COUIThemeOverlay.isRejectTheme(android.content.Context): boolean` |
| 权限管理 17.00.003 | `com.oplus.securitypermission` | `com.coui.appcompat.theme.a.o(android.content.Context): boolean` |

两个样本的方法逻辑一致。开启时拦截返回 `true`，关闭时执行原厂方法。其调用方在返回 true 后同时跳过图标 tint 和 COUI 主题样式覆盖，因此受影响的不只有图标，也包括使用该主题的控件。

Hook 在目标包的 PackageReady 阶段安装，绑定具体的 `Context → boolean` 方法签名。COUI 类内的调用方，以及设置的 `com.oplus.settings.utils.ThemeUtils.isRejectTheme`、权限管理的 `bb.i.c` 做去优化处理。两个偏好键分别为 `settings_reject_coui_theme`、`permissions_reject_coui_theme`，随现有本地/框架偏好同步。

## 验证记录

- 两个样本的目标方法、返回类型和调用链已通过 DEX 核对。
- `v1.2.4 (36)` 的现有 11 组回归、Release/R8、lintVital 和 APK v2 签名检查通过。
- 已核对产物中的新增作用域为 `com.android.settings`、`com.oplus.securitypermission`。
- 本次 ADB 没有连接设备，尚未进行这两个新开关的真机视觉复测。

```powershell
.\gradlew.bat -I "tests\qs-checks.init.gradle" :app:checkQsAppearance :app:assembleRelease "-PVERSION_NAME=1.2.4" --offline --console=plain --no-daemon
```

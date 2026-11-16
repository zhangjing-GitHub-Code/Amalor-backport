# 微信输入法：键盘跟随系统字体

## 范围与入口

- 配置 → 微信输入法（`com.tencent.wetype`），图标使用 `ic_keyboard`。
- 子页“字体”分组内的“跟随系统”开关默认关闭，说明为“键盘字体跟随系统字体”，复用 `ic_custom_font`。
- 开关保存到现有本地配置，并通过 `amalor` 远程偏好组同步 `wetype_follow_system_font`，支持离线保存后同步。
- `scope.list` 新增 `com.tencent.wetype`。模块按首包过滤，覆盖该应用的主进程和输入法进程；仅加载其代码的其他进程不进入此适配。

## 4.0.1 APK 核对

对象：`微信输入法_4.0.1.APK`，versionCode 58201，targetSdk 36。

`keyboard/selfdraw/drawmethod/{b,e,f,g,h,i}` 的构造器均通过
`Typeface.createFromAsset(AssetManager, "fonts/WE-Regular.ttf")` 创建键盘文字字体，再交给 `Paint.setTypeface`。
笔画过滤、浮窗、符号列表及自定义符号编辑也复用这一资源。其他 DIN / WeRead 字体用于关于页、文件传输、AI 和录音等界面。

因此 Hook 在开关启用且资源路径精确为 `fonts/WE-Regular.ttf` 时返回 `Typeface.DEFAULT`，其他调用继续执行原方法。它保持 Paint 的字号、字重派生、描边、对齐和触摸逻辑。注册使用 LibXposed API 102，包含真正的拦截回调；用户提供的旧 API 示例注册了空 `XC_MethodHook`，其中返回 DEFAULT 的 `a` 类没有接到该注册上。

Hook 在包初始化回调中安装，目标仅为 Android 的公共字体工厂，不提前读取微信输入法的静态对象。字体可能缓存在 Paint 或 `WxImeUtil` 中，因此更改开关后需要重启输入法。Manifest 的 `WxHldService` 实际位于 `:hld`；页面重启入口覆盖 `com.tencent.wetype` 与 `com.tencent.wetype:hld`。

## 验证

重新检查 APK 调用入口可运行：

```sql
SELECT DISTINCT method
FROM method_calls
WHERE callee = 'Landroid/graphics/Typeface;->createFromAsset(Landroid/content/res/AssetManager;Ljava/lang/String;)Landroid/graphics/Typeface;'
ORDER BY method;
```

构建及资源检查使用项目既有 `checkQsAppearance` 与 `StringResourcesCheck.py`。最终产物还需核对作用域、开关读取和 Typeface.DEFAULT 返回分支。当前 ADB 未连接设备，实际键盘效果待用户确认：在 LSPosed 启用微信输入法作用域，开启开关、重启输入法，检查字母/数字/符号/笔画键及切换系统字体；关闭开关并重启后应恢复输入法字体。

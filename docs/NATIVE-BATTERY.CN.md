# SystemUI 17.99.02 原生电池图标接口调查

分析对象：`app/系统界面_17.99.02.APK`，Manifest 包名为
`com.android.systemui`，versionName `17.99.02`、versionCode `179902`。
本文依据 APK 的 DEX 方法体、方法引用和 resources.arsc/XML，使用 DexSQL 静态检查。
未运行目标 APK，尚未实现原生电池 Hook，也未进行设备端绘制验证。

## 结论

APK 保留了 AOSP unified 横向电池的完整绘制入口和必要资源。
可优先在 SystemUI 进程内复用 `BatteryLayersDrawable`，由模块桥接 ColorOS 的状态和布局。
这条路径可以使用包内的原生外框、充电闪电、省电加号、百分比排版和状态配色。
它们是 SystemUI 内部实现，不能视为跨版本稳定的 Android SDK API。

## 创建和更新入口

以下类均位于 `com.android.systemui.battery.unified`：

| 入口 | 已检查的作用 |
| --- | --- |
| `BatteryLayersDrawable.Companion.newBatteryDrawable(Context, BatteryDrawableState)` | 创建外框、背景、进度、两种百分比文字及状态符号 Drawable |
| `BatteryDrawableState(int level, boolean showPercent, ColorProfile color, Drawable attribution)` | 统一图标状态，attribution 可为 null |
| `BatteryLayersDrawable.setBatteryState(BatteryDrawableState)` | 更新图标状态的入口 |
| `BatteryLayersDrawable.setColors(BatteryColors)` | 更新配色的入口 |
| `BatteryColors.LightThemeColors.INSTANCE` / `DarkThemeColors.INSTANCE` | APK 内置两套配色 |
| `ColorProfile.None` / `Active` / `Warning` / `Error` | 普通、接电、省电警告、低电量配色 |

外层 `com.android.systemui.battery.BatteryMeterView` 也保留了
`onBatteryLevelChanged(int, boolean)`、`onPowerSaveChanged(boolean)`、
`onDarkChanged(ArrayList, float, int)` 和 `getBatteryAttribution(boolean)`。
其中 `getCurrentColorProfile()`、`setBatteryDrawableState(...)` 为 private，
不能把方法存在直接等同于公共接口可调用。

## 充电、省电和文字排版

`BatteryMeterView.getBatteryAttribution(boolean)` 按以下顺序返回包内资源：

1. 省电开启：`drawable/battery_unified_attr_powersave`，矢量路径为加号。
2. Battery Defender 开启：`drawable/battery_unified_attr_defend`。
3. 参数为 true：`drawable/battery_unified_attr_charging`，矢量路径为闪电。
4. 其他情况返回 null。

`getCurrentColorProfile()` 的优先级与符号优先级不同：
接电为 `Active`；否则省电为 `Warning`；否则 Battery Defender 为 `None`；
否则电量 <= 20 为 `Error`；其余为 `None`。
因此复刻时应分别决定颜色和符号，不把两者合并成一个互斥状态。

`BatteryLayersDrawable.draw(Canvas)` 已包含三种前景排版：

- 同时显示百分比和状态符号：`BatterySpaceSharingPercentTextDrawable` 配合分区符号。
- 仅百分比：`BatteryPercentTextOnlyDrawable`。
- 仅状态符号：符号在可用区域居中。

`updateColorProfile(...)` 为普通前景设置 fg/bg，并按 ColorProfile 选择 fill。
APK 的颜色常量如下；名称沿用目标类，不代表应用全局主题的直接映射。

| 目标配色类 | Active | Warning | Error |
| --- | --- | --- | --- |
| `LightThemeColors` | `#34A853` | `#FBBC04` | `#EA4335` |
| `DarkThemeColors` | `#188038` | `#F29900` | `#C5221F` |

工厂使用的资源也都存在：`battery_unified_frame`、`battery_unified_frame_bg`、
`battery_unified_frame_path_string`。默认图标尺寸资源为
`status_bar_battery_unified_icon_width`（约 20.6sp）和
`status_bar_battery_unified_icon_height`（12sp）。

## ColorOS 接入位置及限制

当前厂商路径位于 `com.oplus.systemui.statusbar.pipeline.battery`：

- `ui.binder.BatteryViewBinder.bind$updateBatteryContentView(StatBatteryMeterView, StatBatteryIcon)`
  更新无障碍描述、内部百分比、充电图、真实电量、特殊颜色和三种 tint，再 invalidate。
- `ui.binder.BatteryViewBinder.bind$updateBatteryIconStyle(...)` 管理图标样式切换。
- `ui.binder.BatteryViewBinder.bind$updateChargingView(...)` 管理额外充电 ImageView。
- `data.repository.BatteryRepository.getBatteryLevel()`、`getChargeState()`、`isSavingMode()`
  均返回 StateFlow，可为桥接提供电量、充电和省电状态。
- `data.model.ChargeStateModel` 提供 `getCharging()`、`getPluggedInWireless()`、
  `getSuperVoocCharging()` 等，适合映射厂商充电状态。

`BatteryContentDrawableFactory.createContentDrawable(Context, int, int)` 的返回类型是
厂商 `BatteryContentDrawableBase`；`StatBatteryMeterView.setBatteryStyleDrawable(...)`
同样接收该类型。`BatteryLayersDrawable` 不属于这条厂商类型继承链，不能直接作为
工厂的替代返回值。已查的工厂分支仅创建厂商 Horizontal、Vertical、Circle 和基础类型。

建议在 Binder/View 的图像绑定层接入 AOSP Drawable，并桥接后续状态更新，
处理原充电标记和外置百分比，避免叠加显示。还需处理样式切换后重新绑定、
深浅色/状态栏 tint、RTL、锁屏及控制中心不同位置的尺寸和生命周期。
目前模块的 `OplusHorizontalBatteryHook` 针对厂商 Drawable；接入原生绘制时需要
明确两种设置的关系，避免将厂商填充修正应用到原生进度。

实现后的设备验证至少应覆盖：普通放电、低电量、充电、满电接电、省电、
省电同时接电、内置百分比开关、深浅色和样式切换。

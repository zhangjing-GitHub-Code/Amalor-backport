# SystemUI 17.99.02 双卡信号 Hook 分析

## 1.2.17：横向电池内电量数字居中

原生 `HorizontalBatteryContentDrawable` 已测量文字墨迹，但使用固定的主体比例，无法对应
当前资源覆盖后的真实电池外形。现在从 `outsideDrawable` 的独立副本获取实际 alpha 边界，
选择占宽最大的主体区域，分离电池头和透明留白，将这个范围交回原生文字布局。

原生的字体、字形测量、颜色、挖空/高对比度绘制继续使用同一条链路；宽字体触发的备用布局也按
实际主体中心计算偏移。结果按 Drawable 实例和尺寸弱引用缓存，测量 Bitmap 立即回收。
该修正自动作用于横向电池内的数字，与电量进度归一化开关独立。

PLQ110 当前字体下，真机截图按可见主体与数字边界测量的水平偏差（正数为向右）：

| 电量 | 修正前 | 修正后 |
| --- | --- | --- |
| 9 | +1.5px | 0px |
| 66 | +1.5px | −0.5px |
| 99 | +1.5px | −0.5px |
| 100 | +2px | +0.5px |

`BatteryTextGeometryCheck` 覆盖主体/电池头分离、透明图形、多个尺寸、镜像资源，以及不同文字
墨迹宽度和 RTL 备用布局的中心不变量。真机电量由 `cmd battery set level` 暂时模拟，检查后
执行 `cmd battery reset` 恢复真实电池服务更新。版本为 `Amalor_v1.2.17_55-release.apk`。

## 1.2.8：独立标签的光学居中与间距

根据 1.2.7 真机反馈，Pixel 的 View span 也复用 Compose 路径的 `rasterizeCentered`，按实际非透明字形的上下边界居中，再保留原始 density 转为 BitmapDrawable。原先仅居中 vector 的整个画布，透明边缘不对称时字形仍会偏下。

跟随系统和谷歌圆的独立 TextView 在首次绑定及字体更新后，左右各设置 2dp padding；与 Pixel span 使用同一个间距计算，保持垂直 padding，重复绑定不会累加。

`Amalor_v1.2.8_36-release.apk` 已通过 11 组 JVM 回归、Release/R8、lintVital 和 APK v2 签名验证（CN=Acardia）。最终 Manifest 为 1.2.8 / code 36 / SDK 37，包内核对了 BitmapDrawable density 与相对 padding 的调用。当前 ADB 未连接，这两项最新的视觉调整尚未复查真机；覆盖安装后需重启系统界面。

## 1.2.7：补齐系统独立信号样式

参考 ONextBox 的 `LargeNetworkTypeHooker` 与 `LargeNetworkArtworkHooker`，补回独立布局的外观适配：

- 在其他 UI Hook 解析宿主类之前安装 `AppFeatureProviderUtils.isFeatureSupport(ContentResolver, FeatureID, String): boolean` Hook，仅启用 `com.android.systemui.statusbar_show_big_5g`。原生工厂继续按主题选择 Binder；模块不直接替换工厂返回值。
- `OplusStatusBarMobileViewBinder$Os17Binding.initViews(int)` 与 `access$setMobileTypeTextTypeface(...)` 分别覆盖首次绑定及字体/网络 Flow 更新。跟随系统按 400/700 归一化字重，谷歌圆复用内置 Bold Typeface；3G/4G 不依赖 `isLarge()`，也能应用设置。
- Pixel 在 `bindNetworkTypeText` 原方法完成后，把完整标签放入一个 `ReplacementSpan`，保留原始文字及无障碍描述。先移除原厂前缀/后缀的尺寸和基线 spans，避免 5G-A 等标签按多个 run 重复绘制；间距由 span 承担，不累计修改 View padding。Tigo 仍使用原生 ImageView 槽位。
- 初始 Binder 调用、合并后的 `bindSubclassFlows$1$1.emit` 与原有 Compose 调用方分别反优化。原生空模型、SIM 可见性和数据卡选择不回填、不改写。

设置仍由状态栏 → 网络类型的“独立显示”及字体选项控制，修改后重启系统界面。

## 代码组织

`xposed/ModuleEntry.java` 为唯一 Xposed 入口，负责目标包分发和功能安装。
`xposed/systemui/NetworkTypeHook.java` 负责网络类型布局与字体，
`xposed/systemui/PackageUpdateHook.java` 负责应用升级等待页外观。
`xposed/systemui/OplusHorizontalBatteryHook.java` 负责 ColorOS 横向电池电量填充区域。
Java 包目录采用小写 `systemui`；`META-INF/xposed/java_init.list` 指向 `ModuleEntry`。

分析对象：`app/系统界面_17.99.02.APK`。使用 jadx 1.5.3 反编译代码，输出位于本机
`C:\Users\30536\AppData\Local\Temp\opencode\systemui-17.99.02\sources`。
全包反编译报告 104 个错误；本文结论限定为已检查的信号显示路径。Compose 方法存在反编译警告，反射名称采用 jadx 注释中的原始 JVM 名称。

## 两条显示路径

### 独立样式（早期精简方案，1.2.7 增补见上）

`OplusMobileSignalExImpl.getMobileViewBinder()` →
`OplusMobileViewBinderFactory.getMobileViewBinder(boolean)` → 对应的 View Binder。

工厂参数来自 `MobileThemeAdapterState.isUnstackedUseTheme17()`，用于区分主题版本。
旧 Hook 忽略该参数，一律返回 OS17 的 BigType Binder；随后通过
`OplusStatusBarMobileViewBinder.Os17Binding` 修改标签字体、回填旧网络模型，
并隐藏非默认数据卡的整个 ViewGroup。

本次删除该工厂替换、Os17Binding Hook、默认数据卡判断和关联缓存。
独立样式交回系统自身处理；模块不强制开启系统的合并样式设置。

### 合并样式（保留）

`OplusStackedMobileIconKt.OplusStackedMobileIcon()` →
`OplusStackedMobileIconLayoutStrategyFactory.getStrategy()` →
`BigTypeStackedMobileIconLayoutStrategy.StackedMobileIcon()` →
`NetworkTypeText-Y85pibM(...)`。

原生 BigType 使用 Row，按漫游文本、双卡信号、网络类型标签的顺序布局；
标签居中对齐、间距和尺寸均使用系统的 BigType dimensions。
无需固定 22dp 位移、额外预留占位或解除父 View 裁剪。

## UI 配置对接

入口：状态栏 → 网络类型。复用 LibXposed 的 `amalor` 远程配置组，
`status_bar_mode` 为现有“启用修改”状态，其余键为
`network_type_bold`、`network_type_font`。旧 `network_type_separate` 值不再读取，
“单独显示”完全由主开关决定，显示时固定勾选且不可操作，关闭时随设置列表收起。
设置保存后重启系统界面生效；Hook 每个进程只读取一次配置快照。

| 条件 / 字体 | 行为 |
| --- | --- |
| 关闭“启用修改” | 不安装网络类型 Hook，保持系统默认 |
| 开启“启用修改” | 强制开启“单独显示”，该行不可用 |
| 官方（0，默认） | 安装 BigType 布局 Hook，字体保持系统原始参数；粗体行不可用 |
| 跟随系统（1） | 使用系统 SansSerif 字体，粗体开启为 Bold，关闭为 Normal |
| 谷歌圆（2） | 使用内置 `assets/fonts/GoogleSansRounded-Bold.ttf`；固定 Bold，粗体行不可用 |
| 自定义（3） | 暂未实现，菜单项禁用；旧配置或非法字体值回退官方 |

粗体行仅在启用修改、选择跟随系统时可用；切换字体会保留粗体偏好，
但在不支持时不应用。配置服务不可用时禁用编辑，保存失败时提示且不更新页面状态。
字体和 OFL-1.1 原始版权声明已一起内置。Hook 从模块 APK 读取字体，构建 Typeface，
通过目标 SystemUI 的 `AndroidTypeface_androidKt.FontFamily(Typeface)` 转成 Compose 字体。

## 重构内容

- 仅保留策略工厂和网络标签字体两个 Hook；字体反射参数按 APK 完整签名校验。
- 非官方字体同时替换普通字体和 `isLarge()` 使用的大号字体，字重按上表应用。
- 同步清除字体参数对应的 Compose changed/default 位，让 Compose 重新判断字体变化，并避免默认值覆盖注入参数。其他参数位保持原值。
- 反优化实际调用 Hook 的入口、BigType/Default 布局方法及网络标签重组 lambda，替代原先对无关 SignalIcon 方法的反优化。
- 两个 Hook 分别记录安装状态，防止 package-loaded 部分成功后，在 package-ready 重复安装已成功的部分。
- 删除未启用的 Wi-Fi 监听、Compose revision、溢出裁剪、代理和自绘图标实验代码。

### 为什么删除 700ms 网络模型回填

`OplusStackedMobileIconViewModelImpl` 从移动卡 ViewModel 和活动数据卡流构建网络类型信息；
`flowIfIconIsVisible` 在不可见时明确发出 `null`，`NetworkTypeText` 对 `null` 直接结束绘制。
因此 `null` 不一定是暂态丢帧，也可能是系统要求隐藏标签。

旧逻辑在 getter 中返回上一份非空模型，既可能显示过期网络信息，又没有在
700ms 到期时触发 Compose 重组；若之后没有状态更新，残留可能超过 700ms。
现在直接使用原生响应式状态，不缓存或回填网络模型。

## 验证

### 应用升级等待页

新增 `PackageUpdateHook`，独立于网络类型设置安装。
UI 入口：系统界面 → 其他 → 软件更新 → 更换样式。
配置键为 `amalor/package_update_m3e`，默认关闭；与网络类型设置共用本地保存及
远程同步流程，未激活 LSPosed 时仍可编辑。每次创建升级 Activity 时读取开关：
关闭保持原页面，开启使用 M3E 样式。已显示的升级页不实时替换。
目标为 `com.android.wm.shell.packageupdate.PackageUpdateActivity.onCreate(Bundle)`。
17.99.02 在 `onCreate` 中选择 `oplus_package_update_activity`，并设置应用图标和窗口标题。
Hook 在原方法执行后，以目标 SystemUI 自带的 Compose `LoadingIndicator-3IgeMak`
替换加载动画。通过系统 `PlatformTheme` 建立动态浅色/深色主题；
指示器直接使用默认 `primary` 角色，根布局和窗口背景从
`MaterialTheme.colorScheme.surfaceContainer` 读取，并在组合后更新 Views。
不再反射 `R$color` 或通过资源名称查找颜色。默认形状序列和组件动画均由系统组件实现。
26.11.37 将指示器从 48dp 放大为 144dp，同时通过宿主 Compose 的
`Modifier.requiredSize` 指定实际组件尺寸，避免只放大外层 View 而内部仍绘制 48dp。
Compose 生命周期绑定到 Activity 的 ViewTreeLifecycleOwner。

当前更新应用的图标取自原生 `veil_application_icon`，缺失时读取 Activity Intent 的
`icon` Bitmap；该 Bitmap 由 PackageUpdateController 根据正在更新的 RunningTaskInfo 提供。
以 40dp ImageView 放在同一 FrameLayout 的中心，使用 CENTER_CROP、椭圆 Outline 和
clipToOutline 强制圆形，背景随 surfaceContainer 更新。原生独立图标隐藏，避免重复显示；
加载组合、父 LinearLayout 与更新文字统一居中。未提供图标数据时保留加载动画并记录日志。

26.11.38 修复手机更新任务本身没有提供图标的入口：17.99.02 的
`PackageUpdateController.getIcon` 仅对具有桌面窗口装饰的任务调用资源加载器，普通手机任务直接返回 null。
现于 `launchPlaceholderInTask` 构造 Intent 之前，在“更换样式”开启且原 Bitmap 为空时，按
RunningTaskInfo 的 userId 获取对应用户 Context，从 topActivityInfo.applicationInfo / realActivity
确认更新应用，再用该用户的 PackageManager 加载图标并生成 Bitmap。优先刷新 APK 资源路径，
不可用时保留任务捕获的信息；缺失的窗口应用名称也一并补齐。已有图标不替换，原有更新事务只继续一次。
对应静态桥接及协程调用方已反优化。图标仍由原生 Intent → createIcon → 居中圆形 ImageView 传递。

同时识别 COUI Lottie 和原生 CircularProgressIndicator；保留原控件 ID/类型但隐藏，
保留应用图标和文本。当前实际适配证据来自 17.99.02 的 ColorOS 布局；
仅识别原生控件并不代表其他 SystemUI 版本的类名、资源和 Compose 签名也兼容。

真机需重启 SystemUI 后触发应用内更新，检查浅色/深色背景、形变加载动画、
关闭系统动画时的表现，以及升级完成后的返回流程。

已通过：`./gradlew.bat :app:assembleDebug --offline --console=plain`。
编译成功不代表设备端 Hook 和实际布局已验证；以下场景需要在目标版本真机检查：

1. 启用合并样式：两张卡信号均显示，网络标签位于信号右侧；4G、5G 和大号标签字体一致。
2. 切换默认数据卡、Wi-Fi、飞行模式、SIM 启用状态：网络标签随系统状态更新，无旧标签残留。
3. 切换独立样式：两张卡仍由系统显示，不再被模块隐藏非数据卡。
4. 状态栏与锁屏、深浅色主题切换：颜色、漫游标记、活动指示保持系统行为。
5. 设置保存后退出并重开应用：各设置保持；改变设置后重启系统界面：布局与字体匹配上表。
6. 关闭启用修改后重启：不出现安装日志；官方字体仅出现策略安装日志；其余字体两个 `installed merged dual-SIM` 日志各出现一次，无安装失败或反优化失败日志。
### 电池填充

ColorOS 17.99.02 使用 `HorizontalBatteryContentDrawable` 的 `BatteryBarDrawableBase`
进度换算路径。Hook 在 `getProgressLevel()` 中按主体约占 20/24 的比例重算非满电等级，
不改变真实电量、外框、文字或充电状态。默认配置键为 `battery_body_fill`，默认开启；
关闭“规范电量显示”后直接使用系统原始进度。

这是针对 17.99.02 图标 metrics 的初始校准值，需真机检查 0%、20%、50%、80%、95%、100% 和 RTL；
若头部宽度因厂商主题改变，应只调整 `BODY_END_EXCLUSION`。

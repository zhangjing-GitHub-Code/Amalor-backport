# 设置组件与原生音量侧栏适配记录

## 1.2.13：以 Pixel 原生参照统一比例

参照设备为 Pixel 7 Pro，Android 17 / `CP31.260623.012`，1440×3120、density 3.5。已保存原始分辨率的静止、拖动、松手和铃声菜单截图，以及对应 SystemUIGoogle APK。参照中的轨道宽度在三个交互状态下均为 140px（40dp）。

现行侧栏采用原生内部布局，在根 View 统一应用 0.9 比例；按钮、菜单、轨道、指示器、图标及间距共享同一变换。删除之前分别覆盖 LayoutParams、铃声 ConstraintSet、按钮 padding 和 VolumeSliderDimensions 的尺寸方案。Expressive 弹簧、连续 alpha 与轻缩放继续由原有动效适配承载，滑出距离按缩放后的实际面板宽度加窗口边距计算。

PLQ110 真机桌面复测：静止轨道宽度约 125–126px；调节到 50/160 时，拖动中及松手后的填充段和未填充段均为 125px（颜色边缘阈值测量），未出现宽度差。顶部菜单展开、点击当前静音项收起和底部按钮打开原生音量详情面板均验证正常。媒体音量恢复为 0，铃声维持 SILENT。

`Amalor_v1.2.13_41-release.apk` 已通过 12 组 JVM 回归、Release/R8、lintVital、签名及真机交互检查，并已覆盖安装、重启系统界面。旧的独立尺寸规则测试随旧实现移除，动效测试加入统一比例及实际滑出范围检查。

## 1.2.12：桌面场景下统一侧栏比例

本轮以桌面壁纸为背景，对照收起和展开菜单的实际效果，统一为同一套尺寸：

- 外轮廓和点击栏位 48dp，顶部按钮的可见底色 32dp，上下图标均为 20dp 绘制盒。
- 原生竖向 `VolumeSliderDimensions` 从 `(icon=20, thumbHeight=4, thumbWidth=52, trackSize=40)` 换为 `(20, 4, 40, 32)`。只替换竖向模型参数并重新计算对应 Compose changed 位，使用原生轨道和触摸逻辑。
- 顶部/底部分区与主条之间的 4dp 外间距归零；底板上沿从顶部按钮中线接合，下沿与底部点击栏位齐平，去掉肩部台阶及多余尾部留白。
- 铃声菜单在原生 open/close ConstraintSet 中统一尺寸，保留隐藏选项的 1dp 折叠槽位和原有顺序。背景仍为 InsetDrawable → GradientDrawable，颜色和圆角动画可继续使用原有实例。

PLQ110 / density 3.5 桌面实测：轨道可见宽度为 **112px（32dp）**，同一列的点击宽度为 168px（48dp）。收起触控区域为 `(1062,882)-(1230,1918)`，菜单展开后为 `(1062,546)-(1230,1918)`。已复查桌面收起/展开截图和录屏，点击当前静音选项可正常收起，低音量拖动为 0→2→0，底部调节按钮可打开原生音量详情面板。铃声内外模式均保持测试前的 SILENT。

`Amalor_v1.2.12_41-release.apk` 通过 13 组 JVM 回归、Release/R8、lintVital 和签名检查；已覆盖安装并重启 SystemUI。另保留用户新增的“MYFC 版本”中文文案，将资源键对应回现有 `myfc_status` 引用，修复 ExtraTranslation 构建错误。

## 1.2.10 真机修正：资源定位与 Expressive 开合

### 尺寸未生效的原因

设备 `/system_ext/priv-app/SystemUI/SystemUI.apk` 与此前样本均为 17.99.02 / 179902，但资源表不同。例如：

| 资源 | 原样本 | 实机 APK |
| --- | --- | --- |
| `id/volume_dialog_main_slider_container` | `0x7f0a1074` | `0x7f0a1072` |
| `id/volume_dialog_slider` | `0x7f0a107a` | `0x7f0a1078` |
| `dimen/volume_dialog_slider_height` | `0x7f072208` | `0x7f072209` |

1.2.9 的固定 ID 没有命中实际滑条。现在统一按资源名解析并缓存，布局与动效登记提前到原生 `bind` 之前，覆盖 Main.immediate 在 bind 内启动可见性协程的情况。竖向滑条宽度为 48dp，高度上限进一步限制到 200dp；非标准小尺寸仍按原始资源约束。

PLQ110（1272×2800，density 3.5）实测主滑条触控区域由 `(1048,963)-(1230,1845)` 变为 `(1062,1057)-(1230,1750)`，即 **182×882px → 168×693px**。原生顶部和底部控件同步跟随约束重新布局。

### 开合动效

原生 `animateVisibility$animation$1.onAnimationUpdate` 使用 `ceil(progress)` 设置 alpha，以根 View 的整个宽度插值位移。实机根窗口宽 616px，包含较多空白，真正可见的运动区间很短。

新动效沿用原生 SpringAnimation、可见性协程与 dismiss 收尾，采用 `MotionScheme.expressive().defaultSpatialSpec()` 的刚度和阻尼。位移按卡片左边界到窗口右边界的实际距离计算，并加入 0.94→1 的轻缩放。alpha 连续变化且保持单向，空间弹簧的轻微过冲不会让已经淡出的面板重新闪现。反向重开从当前 alpha 和原生弹簧状态续接。

三轮录屏按实际编码帧/PTS 比较，明显可见的进场到稳定区间从 5–6 帧（约 65–84ms）增加到 10 帧（约 147–149ms）；退场从 4–5 帧（约 24–32ms）增加到 14–15 帧（约 108–116ms）。这是可见过渡区间的比较，不是刷新率或完整弹簧时长；录屏为可变帧率，不能把文件平均 fps 当成屏幕 fps。

对应 SystemUI gfxinfo 采样为基线 123 帧 / 1 帧超期，调整后 153 帧 / 3 帧超期（其中 2 帧来自状态栏窗口）。这组数据未显示持续大比例掉帧，也不据此宣称帧耗下降。

已通过低音量拖动（0→4→0）、快速重开、系统动画倍率 0 下显示/关闭和 13 组 JVM 回归。媒体音量、动画倍率与旋转锁定均恢复为测试前状态。设备已安装 1.2.10 并重启 SystemUI，进程与 crash buffer 检查正常。

## 设置 UI

参考 `BakaSU/manager/app/src/main/java/org/bakasu/bakasu/ui/component/settings/`：

- `material3internal/AnimatedShape.kt`：采用轮廓绘制阶段读取角半径的方式，保留原 AOSP Apache-2.0 版权头。设置行统一处理分组圆角与按压目标，避免父分组在每个动画帧重新组合整行。
- `SettingsChooseWidget.kt` / `LazySegmentedColumn.kt`：统一主题模式、配色风格、ColorSpec、日志级别和网络字体选择。对话框使用居中标题、surfaceContainerHigh、首尾 16dp / 内侧 5dp 的分段选项、primaryContainer 选中背景与单选按钮，以及取消/确定操作。
- 选项只修改对话框内的临时索引，确定时提交，取消不写设置。长列表使用 LazyColumn，单项用派生选择状态减少无关行重组。列表高度使用剩余可用空间并限制在 400dp，保留底部操作区。
- 分组 alpha 改为图层阶段读取，间距动画改为布局阶段读取。设置页原有分组外边距与标题间距继续沿用现有值。

上述机制集中接入 Amalor 的共享 BaseWidget、BaseItemContainer 和 Material 3 ListItem。

## 1.2.9 原生音量侧栏方案

目标为 SystemUI `17.99.02` 的 AOSP `VolumeDialogViewBinder.bind(CoroutineScope, Dialog, boolean)`，第三个参数是 `isVolumeDialogVertical`。仅在原生音量面板功能开启且为竖向布局时修改已膨胀的布局参数。

- 主轨道原宽 52dp，调整为 48dp；高度上限由 254dp 减少约 10%，约为 229dp。
- 背景宽度按轨道减少量同步调整，原 60dp 背景变为约 56dp，保留内侧留白。
- 浮动竖向滑条使用同一宽度。尺寸每次从原始资源计算，不从已缩小的 LayoutParams 累乘。
- 横向面板、按钮触控尺寸及原生触摸/音量回调不经过缩放矩阵。竖向窗口 Insets 回调仅更新边距，不会覆盖新宽度和高度上限。

`AospVolumeSizeRulesCheck` 检查多密度尺寸、48dp 触控下限、背景内侧留白和布局 sentinel。

## Hook 热路径

- 磁贴根类型使用 ClassValue 缓存类继承检查。View 父链仍在调用时读取，避免回收或重新挂载后沿用旧归属。
- 高频资源名分类使用字符串分支，去除每次调用时构建临时 Set 的开销。
- Pixel 字形按资源 ID 和 density 缓存栅格化/居中后的 Bitmap，View 与 Compose 共享像素缓存，各 View 仍新建自己的可着色 Drawable。缓存有容量上限，不缓存网络状态、View 或已着色图片。

以上是可由代码和调用链确认的开销减少；没有真机帧时间采样时，不据此估算帧率或耗电改善幅度。

## 1.2.9 构建验证

`Amalor_v1.2.9_41-release.apk` 通过 12 组 JVM 回归、Release/R8、lintVitalRelease、差异空白检查和 APK v2 签名检查（CN=Acardia）。Manifest 为 1.2.9 / code 41 / SDK 37，包内核对了音量侧栏及浮动滑条的绑定入口、布局高度约束与类型缓存。

```powershell
.\gradlew.bat -I "tests\qs-checks.init.gradle" :app:checkQsAppearance :app:assembleRelease "-PVERSION_NAME=1.2.9" --offline --console=plain --no-daemon
```

本轮 ADB 未连接设备，尚未进行新版对话框、音量拖动/方向切换的真机视觉复查，也没有帧时间或耗电实测。安装后需重启系统界面应用 Hook 更新。

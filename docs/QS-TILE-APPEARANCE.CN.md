# ColorOS 控制中心取色与模糊（实验）

目标：`系统界面_17.99.02.APK`。原生参考：用户提供的 `原生系统界面_17.apk`。

## 当前调整：控制中心与通知统一材质

### 1.2.17：设备电量取色与媒体切换交接

设备空间的 `BatteryCircularProgressLayout.f` 是中央资源图标。SDK 在深色分支会清除其
滤镜，原来的入口图标白名单又不包含它，因而深色卡片上仍出现深色耳机图标。
现在按实际 SDK 类型绑定此图标，使用 onSurface；不可用的设备状态保留弱化角色。
绑定、资源图标切换、配置变化与原有卡片重绘均会校正取色。

同一设备卡片内的 `BatteryCircularProgressBar` 使用 primary 进度和 secondaryContainer
底轨，覆盖 SDK 颜色 setter，保留真实电量、充电动画与几何绘制。范围限定在设备空间卡片祖先内。

媒体展开页进入「媒体切换」实际使用 `OplusMediaOutputStartController` /
`OplusMediaOutputEndController`。后者的逐帧更新只调用 `setLeftTopRightBottom`，绕过通用
Target 控制器的动画轮廓更新。现在登记实际 `detail_container`，在专用 End 控制器更新
边界后同步标准背景 bounds 和 20dp Outline，动画与静态布局交接时使用同一圆角。
真机录屏轮廓拟合为 68–72px（density 3.5，对应 20dp，包含采样/抗锯齿误差），此前中途大圆角
在结束时突然缩回的现象已消失。

媒体来源背景的抑制同时要求仍有有效动画拥有者和颜色进度。输出页收起留下的颜色进度不能
继续禁止小卡片绘制；进入输出页、reset、结束/取消均释放相应旧过渡并使来源背景重绘。

PLQ110 / SystemUI 17.99.02：深浅色各两轮「展开媒体 → 媒体切换 → 返回」，卡片几何恢复，
两处空白像素块与进入前的最大 RGB 差均为 0；SystemUI 进程稳定。普通媒体收起的既有材质检查
最大 RGB 偏差为 2.22（容差 4），取样点为 card `(1082,1210)`、panel `(1172,1210)`。

```powershell
python tests/QsMediaOutputDeviceCheck.py --adb "path/to/adb.exe" --serial <serial> --output "existing-screenshot-directory"
```

该真机检查需要中文 ColorOS 和 Pillow，按实际节点定位媒体卡片，比较复位后的真实像素，最后恢复
原来的深浅色设置。横向电池数字的同步修正见 [SystemUI hooks](SYSTEMUI-HOOKS.CN.md)。

`Amalor_v1.2.17_55-release.apk` 已通过 15 组 JVM 回归、139 个三语言资源键检查、Release/R8、
lintVitalRelease 和 APK v2 签名核验（CN=Acardia），已覆盖安装并重启 SystemUI。真机测试结束恢复
原来的深色模式；电池显示模拟已 reset，蓝牙连接和媒体播放状态保持原状。

### 1.2.7：设备空间插件卡片材质

设备空间使用内置 `com.oplus.deviceplugin.sdk.ui.view.separatecardview` 卡片，背景由 SDK 创建 `PluginDrawable` 或 `ShapeDrawable`，不经过普通磁贴的 `TileDrawableWrapper` 填充入口。此次覆盖无设备入口、矩形/方形入口、矩形/方形设备卡片。

在卡片 `g()` 刷新及基类 `h()` 圆角刷新期间，限定作用域让 SDK 选择纯色分支；保留原生 `ShapeDrawable`、尺寸和圆角，背景采用系统 SE1，并取消会在下一帧覆盖角色颜色的原厂颜色动画。入口 glyph 同时处理 ImageView 的 src 和 background，标题/说明使用 onSurface/onSurfaceVariant。设备图片、空态设备插图和电池图形继续由 SDK 负责。

绑定、内容更新、attach、layout 与主题变化复用事件合并重绘。`QsAppearanceRulesCheck` 补充入口/说明角色与设备图片、电池图形的排除检查。真机已确认原先灰色玻璃底的方形设备/入口卡片改为相同的 SE1；切换深浅色及独立/合并信号布局后，卡片颜色保持一致。测试后恢复原来的浅色、独立信号样式。

### 1.2.6：分组间距与真实媒体封面的顶部圆角

“通知与控制中心”页面恢复 `SegmentedColumn` 的默认内边距，移除外层重复的水平边距和各组的零 padding 特例。第一组位置保持一致，第二组标题上方恢复标准的 24dp；PLQ110 上通过布局节点实测为 84px（density 3.5）。

真实专辑封面的裁剪有 `applyPhotoCoverLaunchOutline`、`applyPhotoCoverLayoutClip` 两条路径，先前没有进入标准圆角处理。原厂封面端点还取 `media_panel_container_photo_radius`（竖屏 36dp、横屏 22dp），与模块大卡片的 20dp 外轮廓不同；展开、收起和静态裁剪交接时，顶部会混用两套曲线。

现在封面端点与启动初值统一为 20dp，两种封面 Outline 也生成标准 round rect。静态封面限制为 20dp；动画中的半径已由原厂换算为局部坐标，因此保留其缩放补偿，仅按真实 bounds 约束，避免在封面缩回小卡片时因局部半径被再次截断而出现变化。仍保留原厂动画矩形、进度和底部裁剪延伸。

`QsShapeRulesCheck` 补充了静态 36dp → 20dp、动画局部半径/可见半径一致性、小 bounds 和无效输入检查。`Amalor_v1.2.6_36-release.apk` 已通过 11 组 JVM 回归、Release/R8、lintVital 和 APK v2 签名验证，真机已覆盖安装并重启系统界面。播放态的两轮展开/收起录屏关键帧已复查，顶部封面与卡片边界保持标准圆角交接。

### 1.2.5：通知与控制中心 / 时钟与日期取色

页面标题资源改为 `config_notification_control_center`（通知与控制中心），内部分为 `config_notification_center`（通知中心）和 `config_control_center`（控制中心）两组。原 `config_tile_icons` 分组资源已移除。

通知中心新增 `notification_clock_date_colors` 开关，默认关闭。图标为 `ic_clock`，主文本“时钟与日期取色”，副文本“时钟与日期跟随壁纸取色”。仅莫奈样式允许修改；模糊样式中置灰，保存的选项保留。运行时同样检查实际材质模式与该选项，启用后对已有白名单中的时钟/日期 TextView 使用系统 primary。

绑定保留完整原厂 ColorStateList，监听系统颜色写入和 TextAppearance 直接替换。开关关闭时恢复系统最近设置的颜色；模块自身的颜色写入不会污染恢复值。偏好变化在主线程刷新已登记文本，复用现有绘制 Hook，不添加持续帧回调。首次升级、切换整体材质样式仍需重启系统界面加载对应 Hook。

`QsAppearanceRulesCheck` 增加了莫奈/模糊双条件、模块写入隔离、启用期间原厂颜色更新及关闭恢复检查。`v1.2.5 (36)` 的 11 组回归、Release/R8、lintVital 与 APK v2 签名验证通过；当前 ADB 未连接，尚未进行此开关的真机复测。

### 26.11.41：音乐占位封面的收起交接

本次处理媒体卡片内带音乐符号的小封面。它与展开页的默认大封面都是含半透明背景/音符的 vector；`OplusMediaPanelAnimator.doAnimation` 已分别算好小图 alpha 与大图 alphaPanel，但 `doViewAnimation` 的 TYPE_COVER_IMG 分支会把大图 alphaPanel 强制改成 1。真实不透明专辑图可以遮住下方内容，半透明占位图则会在小图出现后继续叠画：浅色下底色变暗，深色下可见大小不同的音符重影。

现在只对已登记媒体过渡中、源卡片 `defaultCover=true` 的大封面 Ghost，在原生更新之前保存计算好的 alphaPanel，原生完成几何/裁剪更新后将该值恢复到 Ghost。保留有限值检查及 0..1 限制，取消/反向动画继续使用原生状态；真实封面和其他内容不进入此分支。筛选在读取容器/源视图前完成，不增加全内容逐帧遍历。

PLQ110 / SystemUI 17.99.02 的诊断记录确认，收起 alphaPanel 从 1 连续降至 0，而原生 cover 分支每帧仍把 Ghost alpha 设为 1；修正后逐帧恢复该淡出，深浅主题关键帧中未再看到此前滞留的大占位封面。外层媒体底板继续通过 `QsMediaSurfaceVideoCheck.py` 的独立检查（浅色最大 RGB 偏差 3.0、深色 0.24）。该底板检查不用于量化移动中占位图自身的渐隐。

`Amalor_v26.11.41_22-release.apk` 通过十组 JVM 回归、Release/R8、lintVitalRelease 与 APK v2 签名验证。已移除临时占位图 alpha 日志；本次测试的系统主题按测试前设置恢复。

### 26.11.40：通知进度 primary / secondaryContainer

通知卡片内的 ProgressBar 使用系统生成的 `primary` 前景和 `secondaryContainer` 底轨。登记实际通知拥有者，复用现有 bind、RemoteViews reapply、主题与显示生命周期刷新；颜色 setter 和已登记进度条自身绘制前会校正取色，回收/脱离原通知的 View 不沿用旧归属。

普通进度条底轨保留框架的 SRC tint 方式，覆盖 drawable 原有的 disabled alpha，确保绘制完整 secondaryContainer，而不是该颜色的四分之一透明度。循环加载条的框架 AnimatedVectorDrawable 把背景轨与两个移动矩形放在同一 vector 中，整体 tint 会同时改色；现在仅对具有 `background_track`、`rect1`、`rect2` 完整目标契约的框架指示器分别设置路径颜色，底轨 fillAlpha 为 1。沿用原有矢量动画，缓存仅保留 vector 的弱引用。

参考：[framework vector_drawable_progress_indeterminate_horizontal.xml](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/drawable/vector_drawable_progress_indeterminate_horizontal.xml)。

真机测试使用临时通知应用主动提交红色应用色，确认进度条仍取系统角色；覆盖普通进度、循环加载、37% → 70% 原地刷新，以及深浅主题。截图带 Display P3 ICC profile，先转 sRGB 再核对像素，允许色域转换后的 1 级舍入误差：浅色 primary `#476739` / secondaryContainer `#D8E7CB`，深色 primary `#B2CFA2` / secondaryContainer `#323F2B`，四种进度/主题组合均命中对应前景和底轨。

同版另修复进入页面后立即预测性返回时，入场交接留下的 Activity 子层曲率，见 [Back gestures](ONEXTBOX-PORTS.md#back-gestures)。`Amalor_v26.11.40_22-release.apk` 已通过十组 JVM 检查、Release/R8、lintVitalRelease 与 APK v2 签名验证；临时曲率日志已移除。

### 26.11.39：媒体收起单次合成与输出设备音量条

收起材质从当前动画进度连续过渡到 SE1，取消原先点击收起就把进度强制置零的处理；反向动画保留已到达的材质进度。真机诊断确认，`MediaViewAnimatorController` 的背景拥有者是卡片内的独立 View，而旧登记只包含 `MediaViewProvider.getContainer()`，因此 WrappedDrawable 未能找到对应的媒体过渡，仍叠画第二层 SE1。现在同时登记容器与 `getBgView()`，通过源 View/祖先解析同一个 DetailSurface；动画副本与真实源背景统一让详情容器承载填充。保留 WrappedDrawable 的 bounds/alpha bookkeeping、媒体内容 Ghost 和 OEM 几何动画，结束或取消后恢复源背景绘制。

新增 `tests/QsMediaSurfaceVideoCheck.py`，在静止背景上比较卡片空白区与相邻面板区的实际 RGB，检查近收起阶段相对最终材质的额外变亮/变暗。修正关联前的候选录屏检出最大 8.08 级偏差；修正后深色为 2.112 级、浅色为 3.0 级，均在 4 级容差内。收起中途关闭控制中心、重新打开媒体详情后，复测最大偏差为 1.8 级。示例：

```powershell
py -V:3.13 tests/QsMediaSurfaceVideoCheck.py recording.mp4 --ffmpeg "path/to/ffmpeg.exe" --card 550 1210 --panel 640 1210
```

输出设备页的 `OplusMediaOutputSeekbar` 自绘 `seekbarProgressDrawable`，原先其 AutoBlurDrawable 回退被统一详情底色规则重涂，导致进度与面板同色。现在保留 ViewHolder 原有 ColorDrawable 及代理/手势/音量控制，仅让该进度退出面板重涂；通过 `getDefaultSeekBarProgressDrawable()` 和 `getBackgroundRes(boolean)` 分别提供进度与底轨颜色。

宽音量条继续容纳原有设备图标、名称和副标题。底轨采用系统 `surfaceContainerHighest`，进度按系统 primary 调和，取仍满足 onSurface 对比度 5:1 的最强强调色，给实际渲染留出余量；内嵌名称与副标题统一 onSurface。静音/取消静音的图标更新也重新提交同一前景色，bind、attach、layout 和主题变化沿用现有事件合并刷新。

PLQ110 / Android 17 / SystemUI 17.99.02 实测：在本机扬声器、未在播放场景，深浅主题的 0%、50%、100% 音量状态均保留可读文字和图标；实际像素测得文字对进度/底轨的最低对比度分别为深色 5.01:1、浅色 5.14:1，另核对了进度边界穿过设备名称时的显示。测试后恢复原先的深色模式和媒体音量。

`Amalor_v26.11.39_22-release.apk` 通过十组 JVM 回归、上述录屏检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia）。已覆盖安装并重启 SystemUI；Manifest 为 26.11.39 / code 22 / SDK 37，最终 DEX 已核对新增音量条入口，临时媒体诊断代码已移除。

### 26.11.35：仅补偿真实图标像素，恢复背景挤压与正常收尾

34 版把背景 ImageView 和文字一起登记为“内容”，对它们提交逆向 View 矩阵；背景缩放被抵消，2×1 子控件会受到扩张后的裁剪影响，任一子控件原生布局变化还会提前结束整个宽度脉冲。现在沿用取色适配器登记的 `QSIconViewProxy.getIconView()` 图标实例，只在 ImageView.onDraw 的 Canvas 中做局部逆缩放，并在 finally 恢复。背景、小圆圈容器、标题和原生 View 裁剪不再被内容补偿改写。仅在宽度比例变化时使已登记图标重新绘制。

恢复按槽位自身几何和矩阵归属决定动画寿命；不再因子控件布局变化中断挤压。无需更改圆角弹簧：诊断记录中圆角约 286–290ms 收尾，宽度约 575–600ms 收尾，原先“挤压先停、圆角继续”的问题来自提前取消宽度动画。

PLQ110 / Android 17 实测：34 版未点击的小磁贴邻居仅变化 1px；修正版小磁贴从 216px 压至 190px 后回到 216px，2×1 邻居指定横截面从 450px 压至 411px 后回到 451px。新增 `QsGlyphMotionVideoCheck.py` 检查像素包围盒：未点击手电筒图标宽 44–46px、高 75–76px，中心位移 18px；蓝牙图标宽 44–45px、高 66px，中心位移 12px，均复位。峰值帧核对了 2×1 圆圈与标题完整可见。测试结束恢复自动旋转和定位服务的原始关闭状态。

“优化自动亮度图标”的共享默认值改为 false，UI、本地/远程同步和 ClipBrightnessHook 均使用这个默认值；不覆盖已保存的用户选择。

### 26.11.34：挤压时保持内部图标与文字尺寸

原先的横向 RenderNode 矩阵作用于整个槽位，因此图标和文字也会随背景变宽/变窄。现在点击后一次性登记可见内容树中的 ImageView/TextView，在其自身中心施加槽位横向比例的倒数；内容中心继续随槽位移动，字形与图标尺寸不受这层挤压影响。背景仍走原有弹簧、邻居宽度守恒和单次透明度合成。

槽位与内容共用矩阵归属检查；尺寸、父容器变化、detach 或原生矩阵接管时结束脉冲，只撤销仍由模块持有的矩阵。不逐帧遍历视图树或重新测量布局。`QsClickMotionCheck` 通过组合仿射变换检查整个连续点击弹簧过程及孤立磁贴的内容宽高、中心位移，并保留时长缩放和取消检查。上述为 JVM 几何验证，实际显示仍需真机确认。

### 26.11.30：2×1 挤压中线接缝与单次透明度合成

真机在 2×1 卡片水平中线（本机 y=843）录到了横线，深色加深、浅色变亮。框架资源实测 `system_surface_effect_1_light=#8AF6FAFB`、`system_surface_effect_1_dark=#8A101415`，基础 alpha 均为 138/255。26.11.29 的临时候选仅改为 drawRoundRect，复测仍有接缝；低对比深色背景下不能仅凭高阈值检测结果判定修复。

最终在 `QsTileAppearanceHook.fill` 对 Card 形状且实际 Paint alpha 为 1..254 的背景，按 drawable bounds 创建 `saveLayerAlpha` 局部层。在层内以 alpha=255 绘制完整形状，再一次性合成已计算好的角色/Drawable 透明度，消除非等比变形时的重复叠色。`finally` 恢复 Paint alpha 和 Canvas 栈。形状继续来自原有路径与圆角动画。

新增 `tests/QsRoundRectSeamVideoCheck.py`：检查无文字/图标区域里，中心行相对上下 3px 同向偏离超过 3 个亮度级、且连续达到 12px 的细横线。双侧判定可排除正常轮廓边缘和编码块阶跃，也能覆盖低对比深色接缝。示例：

```powershell
python tests/QsRoundRectSeamVideoCheck.py recording.mp4 --ffmpeg "path/to/ffmpeg.exe" --region 555 800 75 88
```

修复前样本检出 53–58px 连续接缝；最终浅色录屏 1066 帧中最长仅为 2px，深色录屏 460 帧中最长仅为 1px，均未检出横线。浅色 2×1 未点击邻居的底部横截面为 `403 → 367 → 403px`，1×1 邻居为 `215 → 188 → 217px`（含录像采样误差），挤压和复位检查通过。已对比接缝位置的实际关键帧。

录制时每次点击前确认控制中心及目标磁贴，并确认定位/自动旋转的系统值实际翻转；不在目标界面的录屏不计入验证。测试按每轮开始时的快照恢复主题和开关，结束核对定位关闭、自动旋转为 0。宽度检查先输出测量结果再断言，便于识别无效或低对比采样。

`Amalor_v26.11.30_22-release.apk` 已通过七组 JVM 检查、上述深浅模式接缝检查、大小磁贴宽度检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia）。最终包已覆盖安装并通过 Amalor 内按钮重启 SystemUI；Manifest 为 26.11.30 / code 22 / SDK 37，最终 DEX 已核对背景填充的局部透明度合成入口。

### 26.11.28：真机确认并修复同排邻居识别

在 PLQ110 / Android 17 上复现了 26.11.27 的遗漏。临时诊断确认点击及动画帧回调均执行，动画已开启、矩阵可用；同排四个 `DeformationFrameLayout` 的 `getCurrInnerView()` 都返回有效的 1×1 磁贴，但旧扫描结果全部为 null，最终 `group=1`。

原因是每个槽位还包含隐藏的 `QsLabelView → LinearLayout → TextView`。原来深度为 2 的递归在 LinearLayout 处直接判为 ambiguous，即使该分支隐藏且没有第二块磁贴，也会排除整个槽位。现在通过原生 `getCurrInnerView()` 获取当前磁贴，检查其可见性及类型；普通磁贴根直接使用自身。删除递归猜测，标签及缓存的尺寸变体不再影响邻居识别。

新增 `tests/QsClickMotionVideoCheck.py`，用 FFmpeg 从真机录屏读取未点击邻居的固定横截面，验证初始宽度、中途至少 12px 的可见压缩和最终复位。该检查测量实际渲染像素；采样位置需按设备布局指定。例如本机第一排手电筒：

```powershell
python tests/QsClickMotionVideoCheck.py recording.mp4 --ffmpeg "path/to/ffmpeg.exe" --sample 388 1662 245 --rest-width 217
```

同一场景下，旧包的邻居保持在 215–217px（栅格化误差），没有压缩；最终包录屏测得 `216 → 189 → 216px`，压缩 27px，关键帧也确认手电筒随自动旋转磁贴的点击产生挤压并回弹。测试只点击自动旋转两次，结束时其系统值恢复为原来的 0。

`Amalor_v26.11.28_22-release.apk` 已通过七组 JVM 检查、上述录屏像素检查、Release/R8、lintVitalRelease 与 APK v2 签名验证（CN=Acardia）。最终包已覆盖安装并通过 Amalor 内按钮重启 SystemUI；Manifest 为 26.11.28 / code 22 / SDK 37，DEX 已确认使用当前磁贴入口且临时 `AmalorQsProbe` 诊断代码已移除。

### 26.11.27：通知文字刷新与同排磁贴反馈修复

用户日志中的 `bindClickListener` 是 `OplusQSResizeableTileView` 的抽象声明（DEX flags 0x401），不能提交给 LSPosed。改为按同一参数签名解析 1×1、2×1、2×2 和三段式磁贴的具体实现，过滤抽象方法并去重继承入口；同时反优化实际调用这些实现的基类 `init(QSTile)`。点击/长按 listener 仍按运行时类型登记，沿用原有同排宽度守恒、长按排除与动画收尾。

通知刷新原先会在根视图隐藏时消耗并丢弃一次性 pre-draw 请求；仅重写文字颜色、RemoteViews 原地 reapply 或通知栏重新显示也不保证触发 Row 的布局回调。共享刷新入口现在也处理已附着的隐藏根；新增 `AsyncInflationTask.onAsyncInflationFinished` 与 `NotificationContentView.onVisibilityAggregated` 刷新入口。已登记文字在颜色 setter 完成及自身绘制前校正角色色，验证所属根并防止递归重入；操作文字保留实际 drawable state 的禁用前景。继续使用事件合并的一次性全树刷新。

`COUISwitch.setBarColor(int)` 在目标 APK 中是 private（DEX flags 0x2）。八个颜色方法统一开启反射访问权限，全部调用成功后才提交该开关的色板缓存，避免失败后跳过重试。

`Amalor_v26.11.27_22-release.apk` 已通过七组 JVM 检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia，1 个 signer）。新增回归用例复现抽象基类，并验证具体/继承实现的四参数入口能绑定并调用真实 listener；既有宽度守恒、连续点击、时长缩放和取消检查通过。Manifest 为 26.11.27 / code 22 / SDK 37，最终 DEX 已确认包含新增刷新与绘制入口。ADB 当前无设备，通知首帧取色与同排挤压尚待真机视觉回归；更新后需重启 SystemUI。

### 26.11.26：连体按钮反馈、旧资源清理与磁贴点击脉冲

清理上一轮迁移后残留的旧入口标题/说明（三语言）及 `ic_deblur` 图标，并按反馈移除按钮组下方说明和对应资源。模糊/莫奈组使用 `ConnectedSpaceBetween`、首尾 connected shapes，并将每项的 MutableInteractionSource 同时传给 ToggleButton 和 `ButtonGroupScope.animateWidth`；文本限制为单行，按压/长按保持及释放回弹由组件的空间弹簧处理。模式选择继续遵循启动快照和手动重启的生效规则。

新增 `QsTileClickMotion` / `QsClickPulse`。普通磁贴从实际 performClick 回调触发，插件磁贴在 bindClickListener 时登记真实 listener ABI；不依赖生成的 Kotlin lambda 类名。长按回调有独立排除范围，嵌套点击去重，状态更新不会启动新脉冲。编辑模式、隐藏视图、系统关闭动画以及已有原生动画矩阵的视图不启动此反馈。

点击时仅查询一次同排、相邻的磁贴布局。点击项最多按 15% 目标伸展，由紧邻项提供压缩量；压缩同时受邻居宽度和 16dp 上限约束，整排总宽及间距保持。孤立磁贴使用居中的轻压回弹。脉冲采用现有 Expressive Fast Spatial Spring，短点击达到可见按压程度后自动回弹，快速连续点击从当前形状和速度续接。

磁贴使用临时 RenderNode animationMatrix 实现渲染宽度变化，不逐帧改 LayoutParams 或重新测量布局。只在有活动脉冲时申请帧；结束、detach、尺寸/父容器变化或原生转场接管矩阵时停止，并只撤销仍由本次脉冲持有的矩阵。此效果随莫奈材质模式在 SystemUI 启动时安装。

`Amalor_v26.11.26_22-release.apk` 已通过七组 JVM 检查、Release/R8、lintVitalRelease 及 APK v2 签名验证（CN=Acardia，1 个 signer）。新增检查覆盖边缘/中间磁贴宽度守恒、非法尺寸、长按排除、连续点击位置连续性、被压缩邻居的反向伸展、系统时长缩放和结束/取消收尾。Manifest 为 26.11.26 / code 22 / SDK 37；最终资源表确认旧文案、图标和下方说明均已移除，DEX 包含点击及长按排除入口。当前 ADB 无设备，新交互尚未真机视觉验收或进行实际功耗测量；更新后需重启 SystemUI。

### 26.11.25：模糊/莫奈入口与启动时生效

将“控制中心与通知材质”从控制中心子页迁移到系统界面页顶部的“模糊｜莫奈”单选 ButtonGroup。模糊对应原生材质（原开关 false），莫奈对应模块材质（true），沿用 `qs_tile_native_appearance` 的持久化值。两项互斥、重复点击已选项不写入，窄布局的溢出菜单也支持选择并关闭。系统界面页新增重启入口，以及中英俄“保存配置、重启后生效”提示。

材质开关采用进程启动快照：NativeFeatures 创建时读取一次配置，Hook 安装和所有运行期材质判断都使用同一个布尔值。ModuleEntry 在 package-loaded/package-ready 回调间复用此实例，避免频繁点选造成半热切换或重复安装。此值不进入实时偏好缓存，后续 UI 配置更新也不触发它的重复读取；下一次 SystemUI 启动才按最后保存的选择生效。现有纯色通知、描边按钮和双向清除均受同一材质模式控制。

动画对比：页面布局和状态栏边距均调用 `ExpandableConfigSurface → ExpandableContent`，展开同为 `fastSpatialSpec`，收起同为 `fastEffectsSpec`，箭头及头部圆角也使用同一套空间弹簧。状态栏边距有两个滑条区域同时展开，页面布局只有一个，总位移和可见回弹幅度不同；没有发现两页使用不同弹簧参数。

DexKit 评估：当前目标的主要类名可识别，已使用反射参数形状匹配及兼容回退；近期 NoSuchMethodException 属于运行时签名差异。当前不增加 DexKit 依赖。若后续需要跨多个 ROM/版本定位强混淆的方法，再评估定向扫描，并按 APK 版本/指纹缓存结果，避免放入 UI 交互或绘制路径。

`Amalor_v26.11.25_22-release.apk` 已通过六组现有 JVM 检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.25 / code 22 / SDK 37；已核对三语言提示和最终 DEX 的构造时配置读取、运行期快照返回。本轮 ADB 无设备，尚未进行新 UI 的真机验收。更新及切换模式后需重启 SystemUI。

### 26.11.24：通知 ABI 兼容、小横条与高频路径开销

用户日志中的首个错误是查找 `TextViewMaterialWrapper.applyMaterial(Context, TextView, ExpandableNotificationRow)` 失败，随后才触发通知组件/总开关的汇总错误。该位置原来位于小横条等 Hook 的安装之前，因此会连带中断后续通知适配。现在依据运行时公开方法的参数形状选择入口，优先使用 Context/View/Row 的通用签名，兼容仅保留 typed 实现和继承实现的情况。缺少可选 adapter 时跳过该入口；通知组件分段安装，小横条先于可选 wrapper 安装，只有核心背景/生命周期失效才上报核心不可用。新增 JVM fixture 复现“typed 方法不存在、generic 方法可用”的异常路径并验证实际调用。

小横条按真实 `com.android.systemui:id/drag_view` 独立登记，改用 onSurface，并让其自有 Drawable 保持不透明；原 View 的显示、淡出和拖动状态仍由系统驱动。

本轮审查并调整的高频路径：

| 路径 | 调整 |
| --- | --- |
| 通知 / QS 详情 / 控制按钮 | 常驻 pre-draw 全树遍历改为 bind、attach、layout、主题事件触发的 OneShotPreDrawListener；合并同一父根的请求，detach 取消待执行监听，执行后移除监听。 |
| 通知分组头部 | 圆角由 Row 的原生 roundness 更新通知驱动，不再依赖逐帧全树扫描。 |
| QS 详情 TextView | 绘制和颜色 setter 先查已登记集合，非详情文字不再逐个走祖先链。 |
| 通知侧滑菜单 | 在创建/填充菜单时禁用图标和监听，不再每次 getMenuView 都递归处理。 |
| 原生功能与桌面图标开关 | 进程内缓存布尔值，由 SharedPreferences 变更监听更新；热路径直接读取缓存，监听强引用经 Keep 保留。 |
| QS 滑条 | 尺寸/密度/缩放未变时复用几何，图标 tint 仅在颜色变化时提交；原图标绘制仅在实际状态变化时请求轨道重画。 |
| QS 面板 | 窗口半径反射方法提前解析；代理已关闭时不重复提交关闭；面板颜色/比例未变时不重复主动 invalidate，空状态清理直接返回。 |
| Pixel 网络制式图标 | 按资源 ID 和密度使用有界 LRU 复用 ImageBitmap，停止每次 Compose 重组都创建/扫描 Bitmap；模型方法按类缓存。 |
| 桌面通知圆点 | 缓存方法/构造器候选；Bitmap 主色按对象和 generationId 缓存；关闭功能时在创建绘制恢复列表之前返回。 |
| 模块关于页背景 | 动画状态移到 Canvas draw 阶段读取，避免整张卡片随每帧重组。 |

另外核查了动画/轮询入口：形状弹簧只在运行期间申请下一帧并在结束/隐藏时停止；面板刷新是原生事件触发的合并帧请求；模块的 2 秒 Binder 探活由 MainActivity.onStart/onStop 注册/注销监听，后台停止。上述审查与改动不等同于实际耗电测量；所需的窗口模糊、动画和绘制仍有正常 CPU/GPU 成本。

最终 `Amalor_v26.11.24_22-release.apk` 已通过六组 JVM 检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.24 / code 22 / SDK 37；已核对最终 DEX 的事件驱动刷新入口和保留的偏好监听字段。当前 ADB 无设备，本版未安装或进行真机视觉、帧耗时、续航前后对比，未给出功耗下降百分比。更新后需重启 SystemUI 和桌面。

### 26.11.23：通知纯色、Outlined 操作按钮与双向清除

通知中心、悬浮通知、分组头部及动画底板统一使用系统 `surfaceContainer`，删除 SE1/悬浮窗口分支。继续保留既有局部模糊关闭、动画叠层登记及原生几何/alpha 生命周期。

通知操作按钮改为透明填充、1dp `outlineVariant` 描边与 `onSurfaceVariant` 文字/图标，保留禁用前景和 ripple。实机发现 RippleDrawable/LayerDrawable 的 ConstantState 重建可使子 GradientDrawable 丢失自定义 onBoundsChange，导致初版描边呈直角；现按按钮实际尺寸同步真实内容层及 ripple mask 的圆角，并更新真实图层的填充和描边。组件取色参考 [OutlinedButtonTokens](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/tokens/OutlinedButtonTokens.kt)。

通过设备层级确认：长文本通知展开后，原 `android:id/title` 显示位置切换为 `android:id/alt_title`。将 alt_title 纳入同一个主文本角色，补充命名空间隔离断言；展开按钮内部的 time/chronometer 也纳入次要文字角色。消息模板的应用名、发送者名称和操作文字分别沿用对应角色。

新增 `NotificationSwipeHook`，随现有控制中心/通知材质开关生效。NotificationMenuRow 和 OplusCustomMenuRow 关闭 shouldShowMenu、固定菜单 alpha 为零，菜单 View 保持 INVISIBLE 以保留原生测量值，并移除两个图标的点击监听、禁用可点击状态及无障碍暴露。菜单点击/展开事件、图标平移/形变和菜单图标消失动画均被拦截。通知滑动仍走 `NotificationSwipeHelper.handleMenuRowSwipe` 的原生无菜单分支；方向判断委托给原生 `canChildBeDismissed`，继续使用系统的清除资格、距离、速度与误触判断。通知卡片本身的清除/回弹仍由原生 SwipeHelper 执行。

最终 `Amalor_v26.11.23_22-release.apk` 已通过五组 JVM 检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia，1 个 signer）；Manifest 为 26.11.23 / code 22 / SDK 37。DEX 已核对 alt_title、surfaceContainer、outlineVariant 和双向清除/菜单拦截入口。

已安装最终包并通过 Amalor 内的按钮重启 SystemUI，在 PLQ110 / Android 17 / SystemUI 17.99.02 上使用临时通知验证：短距离左滑保留通知且不露菜单，完整左滑及右滑均从通知服务中清除测试条目；截图核对了纯色卡片、展开来源文字、应用名/发送者、描边胶囊按钮及时间文字。实测使用当前浅色模式。测试通知和临时测试应用已清理，SystemUI 进程保持运行。

### 26.11.22：开合动画材质、悬浮通知与标题/收起按钮

按反馈区分两种表面：通知中心的卡片、分组头部和开合动画底板使用与未激活磁贴相同的 SE1 完整 ARGB；实际位于 HeadsUpLayout 中的悬浮通知使用 `android:color/system_surface_container_light/dark`。由 `OplusExpandableNotificationRowExImpl.isHeadsUpView` 检查真实窗口祖先，避免仅凭 pinned/headsUp 标志把通知中心里的提醒也切成 surfaceContainer；Row 重新附着和绘制时重新判断。

26.11.21 只在 NotificationBackgroundView.onDraw 内回退模糊 drawable，遗漏了分组开合的独立 GroupOverlayContainerView。已核对 `OplusNotificationChildrenContainerOverlayExImpl.flushBackground`：它会为独立容器创建 MENU 类型代理、克隆通知底色，再套 AutoBlurDrawable。在此入口执行前记录容器与来源 Row 的弱引用关联，代理创建时立即关闭局部模糊；之后的 setBlurEnabled(true)、AutoBlurDrawable/MaskBlurDrawable 取 drawable 及重建过程都使用同一通知范围。原生裁切、outline、bounds 和 alpha 仍执行，动画底板取当前所属表面颜色。请求可空 PlatformBlurDrawable 的路径继续返回 null，保留原生类型判断。

开合还会经 `OplusExpandableViewExImpl.setupBlurRadio` 写入内容 RenderEffect，现对已接管通知提交零模糊。`updateSummaryHeaderBlurRadius` 的参数同时驱动标题 alpha 和数量胶囊 scale，因此继续执行这些状态更新，只清除最后写入标题的 RenderEffect。原生调用方与背景包装 drawable 的绘制入口一并反优化。

分组及分类标题清理 TextView shadow，并在 setShadowLayer 入口按 SystemUI 的明确资源 ID 过滤原生重写。已核对分类标题的 `TextShadowUtils.setInverseColorTextShadow` 调用。收起按钮新增真实 ID、附着、配置刷新和绘制入口的识别；Framework NotificationExpandButton 同时接管默认/数量态的文字色和胶囊色，并覆盖 setExpanded/setNumber 的更新。保留其原有胶囊 LayerDrawable 与缓存的着色层，不再替换子 View 背景导致缓存脱节。箭头与数量只按 `android:id/expand_button_icon` / `expand_button_number` 更新。

Framework 组件参考：[NotificationExpandButton.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/widget/NotificationExpandButton.java)、[expand_button_pill_bg.xml](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/res/drawable/expand_button_pill_bg.xml)。OEM 动画、代理、标题和收起按钮入口均以目标 SystemUI APK 的 DEX 为依据。

`Amalor_v26.11.22_22-release.apk` 已通过 `tests/qs-checks.init.gradle` 的五组 JVM 检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia，1 个 signer）。新增表面角色/原始 alpha、标题阴影范围和收起按钮资源同名隔离断言；最终 Manifest 为 26.11.22 / code 22 / SDK 37，DEX 已核对独立动画底板、持续禁用局部模糊、RenderEffect、无阴影、四个展开按钮颜色 setter 和 surfaceContainer 角色。当前 ADB 无设备，以上为静态与构建验证，本版尚未安装或完成真机视觉回归；更新后需重启 SystemUI。

### 26.11.21：通知卡片、分组与弹出控件

新增 `NotificationAppearanceHook`，接入原有 `QS_TILE_NATIVE_APPEARANCE` 开关和系统色板，界面文案同步为“控制中心与通知材质”（中英俄三语言）。通知卡片和分组头部底板使用完整 SE1 ARGB；分组收起按钮使用 SE1，数量胶囊与操作按钮使用 SE2；标题/数量使用 onSurface，正文/时间/分类标题使用 onSurfaceVariant，操作文字使用 primary。弹出通知的 `zoomDragView` 使用 onSurfaceVariant 胶囊形填充。

背景在 `NotificationBackgroundView.onDraw` 内沿用原生纯色回退路径，只有该背景所属 `ViewBlurProxy.getBlurDrawable` 返回原始 drawable，原生 bounds、上下裁切、圆角状态、按压反馈和过渡 alpha 继续参与绘制。已核对 C17 的 `getSmoothLayerDrawable` 会把同一个 GradientDrawable 放入底色层和状态层：直接提交半透明 SE1 会重复绘制，状态颜色还可能反过来覆盖底色。现在为状态层创建独立的透明 GradientDrawable，复制圆角、state、level 和 alpha，并恢复底色层 callback，保留 LayerDrawable/GradientDrawable 类型协议。

分组标题通过 `OplusNotificationGroupExtImpl.updateHeaderColor` 更新；`ExpandGroupHeaderButtonBlurHelper.apply` 提交对应按钮的纯色材质；`GroupIconManager.updatePillBgAndNumberColor` 更新数量胶囊及文字。分组头部保留 `NotificationHeaderMaskColorDrawable` 外壳，以按当前 topCornerRadius 更新的角色底板作为内部 drawable，原生堆叠遮罩仍按原回调绘制。普通 `NotificationExpandButton` 的箭头、文字及底板和分类标题/清理按钮也接入取色。

`SmallWindowReplyController.onNotificationPosted` 将“浮窗回复”添加为标准 `Notification.Action`，由通知操作区域及 ButtonMaterialWrapper 一起覆盖；动态 SmartReplyView 使用相同按钮规则。按钮的禁用前景、compound drawable 和 ripple 同步处理。分类标题的 XML 虽然设置 enabled=false，文字仍保持正常角色色，弱化只用于真正的操作按钮。标准文本按资源命名空间识别，自定义 RemoteViews 文本使用 OEM wrapper 的背景检查结果；图像、头像不进入通用着色范围。

通知 Row 的 inflate/attach、contracted/expanded/heads-up 子视图重绑、原生 MaterialColorApplier 更新、分组头部重建、拖动条重新 inflate 及显示状态更新均会重提材质。复用已有的弱引用 pre-draw 监听、父根去重和 detach/attach 管理，主题变化通过现有色板失效/刷新入口更新。替换控件背景时继承其 drawable alpha，头部圆角跟随所属 Row 当前值；非亮屏绘制交回原生路径。

验证命令：

```powershell
.\gradlew.bat -I "tests\qs-checks.init.gradle" :app:checkQsAppearance :app:assembleRelease --offline --console=plain --no-daemon "-PVERSION_NAME=26.11.21"
```

最终 `Amalor_v26.11.21_22-release.apk` 已通过五组 JVM 检查（新增通知资源作用域、应用资源同名隔离、自定义文本与动态操作检查）、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.21 / code 22 / SDK 37；最终 DEX 已核对背景回退、独立状态层与 callback 恢复、分组、操作按钮和 heads-up 注册，三语言开关文案已核对打包。当前 ADB 无设备，本轮尚未安装或真机验收；更新后需重启 SystemUI，重点复核分组反复开合、浮窗回复、弹出拖动条和深浅模式切换。

### 26.11.20：内侧圆形恢复、快速弹簧与详情圆角交接

按反馈将 2×1 内侧底板改为未激活圆形、激活 12dp 圆角矩形，并纳入状态形变。空间动画由 Expressive 的 defaultSpatialSpec 切换为 fastSpatialSpec，继续使用实际 spring 计算，保留中断时的位置和速度、系统时长缩放及关闭动画时的即时落点。

原生动画会从 PathCornerDrawable 的圆角元数据创建起止状态，TileDrawableDelegate 的临时 block provider 又优先于静态 provider。新增 TileDrawableWrapper / TileLayerDrawable 的静态圆角取样入口：无临时 block 时读取当前可见表面及其尺寸，防止临时放大的 bounds 或旧 provider 被当成胶囊圆角。图层封套移入 Ghost 后失去 callback 时，以弱引用保留来源关联。真实磁贴状态提交也同步到形状状态，覆盖菜单打开期间发生的状态变化。

原生清除 block 后立即恢复所属表面的 provider 和 Outline；Ghost/目标控制器结束及静态重绘时释放对应临时轮廓。释放带 provider 身份校验，旧结束回调不会撤掉新过渡；临时 Outline 仅作用于实际匹配的 provider。复用详情页前清理上次的关闭状态和 provider，避免回到静态布局后短暂套用旧大圆角。

普通 QS 详情及媒体设备输出页按实际 `OplusQSDetail.mDetailFrame` 登记外容器，在动画取样前配置 20dp 标准 provider。背景使用保留 PathCornerDrawable 协议的 TileDrawableWrapper 承载纯色填充，避免旧 SmoothCornersGradientDrawable / shader 路径重新带入大圆角。TargetViewLaunchAnimatorController 的目标状态统一为 20dp，动画中仍使用真实插值半径和边界，以便未激活胶囊也能连续进入详情矩形；其裁剪输出使用标准圆角而非厂商曲率。

最终 `Amalor_v26.11.20_22-release.apk` 已通过 `tests/qs-checks.init.gradle` 的四组 JVM 检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia，1 个 signer）。新增内侧圆形/矩形及中间态断言，快速弹簧的反向续接、重复绑定和时长缩放检查通过。Manifest 为 26.11.20 / code 22 / SDK 37；最终 DEX 已核对圆角取样、block 释放、设备输出容器、20dp 目标状态与交接收尾入口。当前 ADB 无设备，本轮未安装或进行真机视觉验收；更新后需重启 SystemUI。

### 26.11.19：标准圆角与 Expressive 状态形变

滑条图标的底部留白由 8dp 增至 16dp，并同步提高最小可绘制高度，低进度时仍保持对指示器的避让。主面板和同比例放大的二级滑条使用同一套几何规则。

亮度/音量二级页的小磁贴属于 `QsDetailTileBaseView`。现在将其接入磁贴识别、真实状态快照、图标取色及主题刷新；激活使用 primary/onPrimary，未激活使用 SE1/onSurface，标题和状态说明分别使用 onSurface/onSurfaceVariant。其背景可能是普通 TileDrawableWrapper 包裹原生 shader，已在外层直接绘制角色填充，并同步背景与图标容器的裁剪轮廓。

| 区域 | 未激活形状 | 激活形状 |
| --- | --- | --- |
| 媒体卡片 | 标准 20dp 圆角 | 标准 20dp 圆角 |
| 2×1 外卡片 | 半高度半径的胶囊 | 20dp 圆角矩形 |
| 2×1 内侧图标底板 | 12dp 圆角矩形 | 12dp 圆角矩形 |
| 1×1 与二级页小磁贴 | 圆形 | 16dp 圆角矩形 |

新增 `QsShapeMotion` 与独立形状规则。保留原 Drawable 类型，为接管表面配置独立 RoundRectOutlineProvider，并以标准 `Path.addRoundRect` 绘制。已核对厂商代码：普通 provider 的 cornerWeight 必须为 null 才走标准圆角；媒体 master clip 则会把非正权重替换成 3，因此仅设 weight=0 不足以去除定制曲率。媒体在其自身 outline 调用范围内将适配器输出改为 `Outline.setRoundRect`，保留原动画裁剪矩形，并统一 20dp 的容器参数和最大圆角。

状态圆角采用现有 Material 3 `MotionScheme.expressive().defaultSpatialSpec()`，通过 TargetBasedAnimation 与 Choreographer 驱动，无新增动画依赖。快速反向切换会采样当前值和速度后续接；初次绑定、不可用/隐藏状态及移除动画时直接落到目标形状。半径始终限制在合法边界内，完成后停止帧回调。原生窗口过渡继续使用其当前圆角数组，但生成标准 Path/Outline。

新增可重复执行的检查入口：

```powershell
.\gradlew.bat -I "tests\qs-checks.init.gradle" :app:checkQsAppearance :app:assembleRelease --offline --console=plain --no-daemon "-PVERSION_NAME=26.11.19"
```

最终 `Amalor_v26.11.19_22-release.apk` 已通过上述四组 JVM 检查、Release/R8、lintVitalRelease 与 APK v2 签名验证（CN=Acardia，1 个 signer）。检查覆盖底部留白、图标避让、圆角端点与中间态、弹簧反向时的位置/速度继承、重复状态绑定、时长缩放和即时停止。Manifest 为 26.11.19 / code 22 / SDK 37；最终 DEX 已核对标准轮廓、媒体 20dp 和二级磁贴的注册入口。本轮按用户选择先交付安装包，未安装或进行 26.11.19 的真机视觉验收；更新后需重启 SystemUI。

### 26.11.18：收起即切材质、锁屏恢复与滑条二级页

媒体在 `DetailAnimationManager.hideDetailToView` 触发有效收起时，将已登记媒体根标记为 closing，并立即提交 SE1 填充。收起动画的颜色比例固定为 0；原生动画结束后也保持该材质，直到下一次展开重新接管，避免收尾清空进度时又写回详情色。展开仍支持从当前颜色连续过渡；取消回调仅清理自己持有的过渡状态。普通详情和媒体详情通过实际 MediaViewProvider 容器区分。

录屏磁贴的锁屏不可用状态保留。新增原生与插件磁贴的状态提交入口，先记录传入的 state/disabledByPolicy，再执行原生更新；角色变化后合并刷新已缓存的背景与图标。绘制优先读取该次提交的状态快照，避免颜色回调发生在字段赋值之前，或 OEM drawable flags 未改变时继续复用旧 RenderNode。Keyguard 状态变化和淡出结束也触发同一刷新路径。

实机 APK 的 `NotificationShadeDepthController.shouldApplyShadeBlur` 只允许状态 0，因此锁屏控制中心此前会退回 OEM 背景。现在已显示的 QS 在 KEYGUARD / SHADE_LOCKED 状态下也进入相同的面板材质路径，doze、解锁淡出及现有启动交接条件仍参与判断。实机分别确认 `showing=true` 与 `showing=false` 时，NotificationShade 的实际窗口模糊均为 119px，即本机密度下的 34dp；录屏磁贴从 `state=0` 恢复为 `state=1` 后，底色与图标同步恢复。

滑条绘制覆盖原有栏位的完整高度，指示器的半厚度在内部预留，触摸 padding 与此范围对应。二级页以实际来源滑条的宽高比计算宽度，轨道、指示器、圆角和图标位置随容器同比例变化，不再固定封顶 56dp。主面板实测栏位为 217×483，亮度和音量二级页均为 503×1120，比例误差小于 0.1%。主面板短按由原 SeekBar 处理，二级页仅由原生长按入口打开；图标的原有独立操作继续走其自身入口。

亮度、自动亮度及音量的低/高/静音图标改用 Google Material Icons Rounded 的官方矢量路径，资源文件内附来源与 Apache-2.0 标识。图标与轨道在同一次绘制中完成，避免原生二级页将图标交给独立 hookToggleView 时丢失内嵌位置。原图标视图继续承载状态及操作，跨父容器的图标目标通过 View 的全局/局部矩阵定位到轨道内部。

`Amalor_v26.11.18_22-release.apk` 已通过 Release/R8、lintVitalRelease、QS 状态/锁屏模糊/媒体立即切换规则检查及滑条高度、避让、同比例交接检查。APK v2 签名通过（CN=Acardia，1 个 signer）；Manifest 为 26.11.18 / code 22 / SDK 37，最终资源表已确认包含五个图库图标。

已安装最终包并通过模块按钮重启 SystemUI。真机核对了锁屏→解锁首次展开、音量短按不弹出详情、亮度与音量长按进入同比例二级页、内嵌图标位置以及深浅模式媒体开合。最后一轮音量拖动读取到 60→137→64，测试结束恢复为 60，并恢复测试前的深色模式。

### 26.11.17：媒体实际底板与宽轨道内嵌图标

用户澄清叠层会跟随深浅模式变色，且 26.11.16 的通用窗口层处理仍未解决。此次在已安装版本上录制开合动画，并临时记录媒体根的实际背景类型：它是 `LightStyleStrokeDrawable`，继承 `AutoBlurDrawable`。其 `draw` 在存在 `strokeShaderProxy` 时直接调用 `drawShader`，绕过此前的禁用模糊 fallback；原来的 `solidDetailBackground` 只会更新 GradientDrawable/ColorDrawable，因而动画进度虽然变化，实际底板仍保持原生主题色。

媒体 Holder 根现在明确标记为媒体背景，在 `provideBlurBackground` 创建入口使用可直接更新 ARGB 的普通填充 Drawable。原生容器 outline/master clip 继续计算圆角和开合形状。实机诊断确认背景已变为 GradientDrawable，展开/收起时填充分别从 SE1 连续变化到 surfaceContainerHigh、再返回 SE1，包含角色自身的透明度。已接管的媒体过渡同时抑制来源 WrappedDrawable 的重复填充，以及 `OplusMediaPanelAnimator` 单独绘制的小卡片背景 GhostView；WrappedDrawable 的 bounds/alpha 收尾仍执行。过渡范围由当前媒体颜色所有者限定。

滑条改为 MD3E L 目标宽度 56dp，根据现有栏宽收敛；48dp 窄栏仍使用至少 40dp 的内嵌图标轨道。未激活轨道直接使用磁贴同一 SE1 ARGB，激活部分使用 primary。亮度太阳/自动模式 A、音量扬声器/静音标记均由模块重新绘制。24dp 图标在激活轨道空间足够时位于下方，指示器接近底部时上移到未激活轨道，图标边缘与 handle gap 之间仍保留 4dp；端点标记也避开图标。原 toggle View 承载点击和无障碍语义，位置与新图标同步，绘制入口不再使用原生图标素材。

COUI 的绝对触摸位置读取 View padding，相对拖动还会扣除 `mPaddingVertical`；其 `setPaddingVertical(0)` 实际表示恢复默认值。因此显式将内部 padding 设为零并执行 `ensureSize`，使两条触摸路径对应完整轨道。退出接管时恢复尺寸、padding、裁剪与图标位置。关闭已接管滑条的 OEM 整体触摸位移/挤压，按下反馈由 4dp→2dp 的指示器变化承担。

最终 `Amalor_v26.11.17_22-release.apk` 已通过 Release/R8、lintVitalRelease、JDK 25 上的 QS 规则检查及宽轨道/图标避让检查。几何检查遍历 48 组尺寸/密度、每组 1201 个进度点，覆盖两侧避让、端点、边界尺寸与非法输入。APK v2 签名通过（CN=Acardia，1 个 signer），Manifest 为 26.11.17 / code 22 / SDK 37；最终 DEX 已核对 inset-icon、no-squeeze、media-source-background 注册及临时诊断代码的移除。

已在设备 SystemUI 17.99.02 / 179902 上安装最终包，并通过 Amalor 内的按钮重启 SystemUI。此次实测场景为未播放媒体卡片的深浅模式开合、控制中心图标避让与音量连续上下拖动；音量从测试前 37 上调至 123、下调至 42，结束后恢复为 37，主题也恢复为测试前的深色模式。关键帧已核对图标换边和拖动过程中的轨道宽度。

### 26.11.16：媒体通用过渡底板与 MD3E 竖向滑条

用户反馈媒体开合仍有白色叠层。继续核对发现，TransitionAnimator 还会独立绘制 `windowBackgroundLayer`，其默认 `applyStateToWindowBackgroundLayer` 在中间阶段可把 alpha 写成 255；前版的卡片背景混色不控制该层。本版仅在 MediaViewAnimatorController 与 MediaDialogLaunchAnimatorController 的 `onCustomStateToWindowBackgroundLayer` 接管此临时层，将其设为透明、alpha 0 并清除 SRC Xfermode，然后返回已处理，避免原生默认流程再覆写出额外底板。媒体卡片、内容和几何动画仍由原有控制器绘制。

新增 `QsSliderAppearanceHook` 与独立几何规则，实现原生 SeekBar 上的 MD3E 竖向绘制。采用官方 M 尺寸（40dp 轨道、52dp 横向 handle、4dp 厚度/按下 2dp、12dp 外圆角），窄槽使用 XS 轨道；包含分段轨道、6dp handle gap 与端点标记，主轨道/handle 取 primary，底轨取 secondaryContainer，标记取 onSecondaryContainer。替换旧 slider-system-roles 绘制入口，关闭该滑条的彩色 spotlight 叠加。

图标继续位于原生 toggleView 的底部区域。轨道下界按图标实际 top 或尚未布局时的 LayoutParams 计算，handle 在最低值仍至少距离图标 8dp；布局同级时使用不受按压缩放干扰的原始位置。顶部/底部 padding 同步给 COUIVerticalSeekBar，原生触摸计算读取同一范围，保留现有亮度/音量控制器、进度回调、触觉与无障碍。绘制读取原生动画中的 mScale；空间不足时恢复原尺寸参数并回退原绘制。此处为原生 View 上的 MD3E 样式实现。

`Amalor_v26.11.16_22-release.apk` 已通过 Release/R8、lintVitalRelease、新增 MD3E 几何/图标避让检查，以及现有 QS、数据卡、返回手势检查。几何检查覆盖不同密度、最小/最大进度、窄槽、未测量和无效输入。APK v2 签名验证通过（CN=Acardia，1 个 signer），Manifest 为 26.11.16 / code 22 / SDK 37。最终 DEX 已核对 md3e-slider-draw/size/touch-range/no-spotlight、media-no-window-layer 和两个 secondaryContainer 角色，旧 slider-system-roles / slider-color-refresh 注册已移除。本轮采集了更新前的控制中心滑条截图，设备随后断开；新版媒体白层与滑条实际交互尚未真机验收。

### 26.11.15：实机通知按钮背景与媒体开合颜色连续性

本轮通过 ADB 采集通知中心截图和层级，确认右上角为 `simple_qs_footer/settings_button`（“打开通知与控制中心”），底部为 `clear_all`。实际入口是 OplusQSSimpleHeader.refreshSettingsButtonBackground，而非之前调查的 OplusQSFooterBackgroundProxy：该入口直接使用通知 ViewBlurManager 与 MaskBlurDrawable；备用 `simple_qs_settings_button_background_drawable` 还是 fillAlpha=0.3 的黑色矢量圆。仅替换 TileDrawableWrapper 绘制或设置 tint 均不足以覆盖当前路径。

按 OplusQSSimpleHeader.settingsButton、ClearAllController.clearAll 的真实实例登记范围，兼容资源覆盖更换 View 子类。两处使用随 bounds 计算半径的圆形/胶囊 GradientDrawable，直接填充系统 SE1 ARGB，保留 View 的点击缩放与可见性动画，并清掉旧背景 tint，避免再叠乘备用矢量的 30% alpha。ClearAll 的构造和背景更新也登记到同一配色入口，图标使用 onSurface；这两个按钮的通知彩色 spotlight 不再叠加。其它通知内容及运营商不进入该按钮范围。

媒体使用独立的 OplusQsMediaDialogController 工厂。展开时的 MediaViewAnimatorController 接入既有 Ghost WrappedDrawable 角色传递，使小卡片背景在临时容器内仍使用 SE1 和原生 drawable alpha；媒体光效抑制也识别该动画绘制上下文。原生收起的 createEndController 返回 null，单靠 Ghost 的角色传递不能覆盖收起，因此进一步在 MediaDialogLaunchAnimatorController 的 start/progress/end/cancel 处驱动详情背景的颜色进度：以系统 SE1 与 surfaceContainerHigh 的完整 ARGB 连续混合，展开到详情色、收起到卡片色，几何、封面、时长和 View alpha 由原生动画继续驱动。已有详情 pre-draw 使用同一当前颜色，不再每帧写回最终纯色。中断时记录当前颜色比例，并以动画实例作为所有者，旧回调不会清理新过渡。

`Amalor_v26.11.15_22-release.apk` 已通过 Release/R8、lintVitalRelease、扩展后的通知按钮范围及媒体双向/中断/非法进度规则检查、既有 QS/数据卡/返回手势检查，以及 APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.15 / code 22 / SDK 37；实际 DEX 已核对 notification-settings-background、notification-clear-created/background、media-animation-source、media-color-start/progress/end 和原有 Activity 清理。此次实机采集为旧版现象，本版未安装或做更新后的视觉验收。

### 26.11.14：通知快捷按钮包装层、编辑图标滤镜与媒体弹出大卡片

通知中心按钮的 `OplusQSFooterBackgroundProxy.refreshViewBackground` 给 bgView 设置的是普通 `TileDrawableWrapper`，内部再装入 QSDrawableBuilder 的结果，可能不经过 MixColor/Gradient/StateList 的覆写 draw。本版在继承的 `DrawableWrapper.draw` 中严格匹配“实际类型为 TileDrawableWrapper + 已登记控制按钮 + 原生 RoundRectOutlineProvider”，按同一个原生 Path 和 userAlpha 绘制 SE1/primary。`RoundRectOutlineProvider.updatePath` 与 `ensureOplusPathAdapter` 的实现已核对：普通圆角直接填 Path，平滑圆角使用绑定到该 Path 的 OplusPathAdapter；头像及其它包装 Drawable 不进入此分支。

编辑页 `QSColorUtil.adaptiveColor(ImageView, Drawable)` 会把白色 SRC_IN ColorFilter 直接写进 Drawable。ImageView 自身的 getColorFilter 返回 null 时，上一版未清掉这个优先级更高的滤镜。本版共享 `tintImage` 同时清理 View 与 Drawable 的显式滤镜，再提交角色 ImageTint；补上 adaptiveColor 完成后的入口，并反优化 onEnterEditMode 调用方，使“+”和“控制中心”换图后立即得到当前前景色。

点击/长按弹出的媒体大卡片属于 `OplusQsMediaDetailPanelDialog → OplusMediaViewPagerAdapter$OplusMediaViewHolder`，与之前已接入的面板大尺寸媒体 View 是两套实现。本版在 Holder 构造开始前登记其已膨胀的 itemView，让原生 QsDetailBackgroundUtils 创建背景时就走详情纯色分支；缓存行重建也使用同一入口。构造完成、bind、updateViewColor、updateSeekBarColor 以及已附着内容的 pre-draw 覆盖标题/说明/时间/输出文字、播放与附加操作按钮、音量图标、加载点、COUIIntentSeekBar 和音量 SeekBar。标题使用 onSurface，说明/时间/输出文字使用 onSurfaceVariant，进度 primary、底轨 SE2，输出按钮 SE1。控件颜色在原生更新后失效重提，避免每帧重复设置滑条。

大卡片背景使用详情 surfaceContainerHigh，彩色阴影/描边的 OplusMediaItemBackgroundDrawable 在已识别详情内不再绘制；真实封面、原生裁剪圆角、布局和播放交互继续沿用其原路径。

`Amalor_v26.11.14_22-release.apk` 已通过 Release/R8、lintVitalRelease、扩展后的媒体文本角色与既有 QS/数据卡/返回手势检查、APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.14 / code 22 / SDK 37；实际 DEX 已核对 control-adaptive-icon、control-wrapper-fill、media-detail-created/update/no-light，`sn1.C(ImageView, int)` 确认同时读取及清理 Drawable 显式滤镜，已生效的 Activity 清理入口继续存在。ADB 本轮无设备，未安装、重启或进行视觉验收。

### 26.11.13：远程二级菜单主题、顶部按钮与媒体卡片

本轮曾通过 ADB 抓到移动数据二级菜单截图和真实层级。内容路径为 `view_dialog_container → detail_container → detail_content → qs_hostview → sysui_inline_surfaceview`，列表绘制在远程 SurfaceView 中；本地仅有标题、开关及“更多设置”。此前把动态内容视为 SystemUI 本地 OplusQSDetailItems 的判断不适用于这台设备，26.11.9–26.11.12 的 TextView/Adapter/pre-draw Hook 无法越过此进程边界。SystemUI 的 `SystemUIInlineViewHelper` 指向 `com.android.phone/com.android.simsettings.inlineview.SimSettingsInlineViewService`（移动数据）及 `com.oplus.wirelesssettings/com.oplus.wirelesssettings.OplusWirelessInlineViewService`（Wi-Fi、蓝牙）。

新增 `QsInlineAppearanceHook`，在已确认属于 QS 详情的 SysUiInlineHostView 附着、创建、布局、运行状态和配置通知前同步其 controller 的 baseExtras。`key_is_dark_theme_color` 与旧 `isDarkThemeColor` 均取详情背景实际 night 配置；`key_is_third_theme`、旧 `notification_scrim_bg_mask_color` 和 `isSupportLight` 设为 false，避免纯色浅底仍请求适配深色磨砂的白字主题。发送 Configuration 时只对齐 night 位，其余尺寸/方向参数沿用原输入。主题刷新也通知已附着的远程 host，首次实际通知记录 `QS remote detail theme applied: night=...`。这些键均已从目标 APK 的写入入口核对；本版通过原生远程协议修正主题，远程服务最终文字的具体 ARGB 仍待真机核对。

撤除 `contains("carrier")` 等模糊文本匹配。通知/控制中心已知时钟与日期 ID 采用 primary，运营商不进入文字规则。快捷按钮只按真实 ID 定位并成对处理背景与图标，清除旧 ImageView ColorFilter 后采用 SRC_IN 与磁贴前景角色。控制中心设置按钮命中实际带背景的 `quicksettings_settings_button_icon`，编辑入口命中 `qs_edit_button`；编辑页通过 EditTopComponent.startComponent 注册 `addBtn`、`saveBtnContainer`、`qsSettingBtn`，覆盖添加、完成和设置三个按钮。

媒体卡片通过 OplusQsBaseMediaPanelView 的附着与颜色更新入口接入：背景使用 SE1，标题 onSurface、说明/时间 onSurfaceVariant，媒体输出及播放控制图标与磁贴前景规则一致，禁用状态保留弱化。播放动画通过 OplusLottieButton / OplusQsPlayPauseButton 的原生颜色回调更新。大媒体卡片的播放进度取 primary、底轨 SE2。只为默认封面占位图标着色，真实专辑封面由原生处理；OplusQsMediaBackgroundDrawable 的绘制临时抑制专辑彩色光效并在 finally 恢复字段，底层原生形状和过渡 alpha 继续由原绘制路径负责。

`Amalor_v26.11.13_22-release.apk` 已通过 Release/R8、lintVitalRelease、扩展后的运营商排除/时钟日期/顶部按钮/远程新旧协议参数规则检查、数据卡与返回手势检查，以及 APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.13 / code 22 / SDK 37；实际 DEX 已核对 inline-host / inline-theme、edit-top-buttons、media-attached / media-colors / media-lottie / media-surface 及真实按钮 ID。采集到的是修复前现象；设备随后断开，多次重试后仍未在 ADB 中出现，本版未安装或进行修复后视觉验收。

### 26.11.12：内容根绘制校验、真实磁贴状态与其它控件

上一版菜单内容仍未得到用户确认。本版在 `OplusQSDetail` / `OplusQSDetailItems` 的 XML 构造入口登记附着监听，并在内容根的 ViewTreeObserver pre-draw 中遍历已生成的文本和控件。分离/重新附着时移除旧 observer 并登记新 observer；已由外层详情根覆盖的内层列表不重复遍历。文字仅在颜色不同的情况下提交，并关闭详情文字的自动深色转换；具体 Content/RoundContent/Title/TitleCheck/TitleMediaCheck/TitleWithRefresh/Tint/Hotspot/Screenshot Holder 的 bind 也补齐。保留已有 setter、attach 与 TextView draw 校验。INFO 首次记录 `QS detail content pre-draw active`，DEBUG 记录两个实际目标角色色，未记录 Wi-Fi 或数据卡名称。

磁贴背景和最终图标取色优先读取插件 `tileState` 或原生 `mTempState` 的真实 state/disabledByPolicy，缺失时才读取图标代理、最后回退 Drawable flags。录屏原生 Boolean.value 常为 true，但 state 仍可为 1（空闲）或 2（录制）；本版不把 Boolean.value 当成激活状态。未擅自把受策略限制的 state 0 改成可用。C17 TileDrawableWrapper 的 alpha 是独立 userAlpha，本版继续使用系统角色 alpha × userAlpha，未强制改成 255。录屏的具体设备状态与透明度差异尚未采集到，真实状态优先修正不等同该现象已被真机验证。

补齐 2×2 及其它大尺寸的卡片/图标区域、三段式外背景和选中指示器、1×1/2×1/2×2 插件标题与说明。大卡片使用 SE1，未激活图标区域使用 SE2，激活/选中区域使用 primary；说明为 onSurfaceVariant。设置/更多/编辑按钮使用 SE1 与 onSurface，已识别的电源按钮使用 primary/onPrimary，头像不作为通用按钮背景目标。详情 action_btn、图标与开关补齐；亮度/音量 OplusQsVerticalSeekBar 使用 SE1/primary/onPrimary，其一次绘制临时走原生 COUI 纯色路径并在 finally 恢复原生混色/描边标志。主题事件清空控件颜色缓存，对话框尺寸和布局未增加修改。

`Amalor_v26.11.12_22-release.apk` 已通过 Release/R8、lintVitalRelease、扩展后的生产类状态优先级/尺寸/按钮/ARGB 检查、数据卡与返回手势检查及 APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.12 / code 22 / SDK 37。实际 DEX 已核对内容根/Holder/控件根/各尺寸标题注册、三段式、slider-system-roles / slider-color-refresh，以及保留的 qs-activity-covered。最后 ADB 设备列表仍为空，Windows 也未找到在场 Android/ADB 设备，未安装、重启或做本版视觉验收。

### 26.11.11：撤回共享弹窗扩张，核对详情文字的最终绘制

用户确认 26.11.10 的跳转闪烁已解决，但浅色详情内容仍然发白；上一版的列表绑定后取色尚未完成视觉修复。本版保留已确认的 Activity 来源窗口清理。

撤回共享 `RestartSystemUiDialog` 标题上的 `fillMaxWidth / TextAlign.Center`，还原普通 Text 标题；多作用域重启正文也移除 `fillMaxWidth`，仅保留自身的 `TextAlign.Center`。不增加对话框宽高、内边距或窗口尺寸参数。此前修改的是所有复用该 helper 的重启弹窗，范围确实超过了单个正文居中的请求。

C17 的详情列表会经 `QsColorUtil.createDarkColorContext` 包装 COUI 主题，初始颜色来自 XML/主题和原生绑定，不能仅依据 Hook 注册字符串或绑定后提交判断最终显示。本版新增 `TextView.onDraw(Canvas)` 的详情范围校验：当前 View 必须仍属于详情层级，实际绘制前比较 currentTextColor 与系统角色色，仅颜色不符时修正。注册置于可选 Adapter 探测之前，避免后续探测失败使绘制校验未安装；移除全局 RecyclerView bindViewHolder 的重复入口，保留共享 QS Adapter 的绑定入口。

详情文字读取已登记背景 View 的 Context；旧版使用 `detail_container` / 详情根，备用使用外层详情 Context 或窗口根。setter、首次着色、最终绘制和主题刷新统一经过该入口，使 onSurface / onSurfaceVariant 与详情背景的深浅配置一致。磁贴颜色读取规则与对话框布局独立。

`Amalor_v26.11.11_22-release.apk` 已通过 Release/R8、lintVitalRelease、生产类 QS/数据卡/返回手势检查及 APK v2 签名验证（CN=Acardia，1 个 signer），Manifest 为 26.11.11 / code 22 / SDK 37。实际 DEX 已核对 `detail-text-draw`、保留的 `detail-row-bound` / `qs-activity-covered` 及移除的 `detail-recycler-bound`；`in1.g(TextView, boolean)` 优先使用背景 Context，`xm1.l` 在实际详情范围内先校验/修正颜色再 proceed。源码已核对共享标题和多作用域正文没有强制满宽。ADB 重试后服务正常，但设备列表为空；此前抓到的仅为桌面，不能作为浅色详情视觉验证，本版未安装或重启设备。

### 26.11.10：长按 Activity 跳转、动态列表文本与应用内对齐

设备反馈 26.11.9 的二级菜单“更多设置”跳转已恢复正常，但直接长按仍会闪现。已核对 C17 调用链：`QSTileImpl.handleLongClick` → `QSTileExImpl.postStartActivityDismissingKeyguard` → `OplusQSActivityStarter.getTileAnimationController`，普通磁贴与详情设置最终共用 `OplusQSTransitionAnimatorController$createController$1.onTransitionAnimationEnd`。此前的 `isMoreSettingAnimator()` 只判断外部 controller 是否为 `OplusQSDialogTransitionAnimatorController`，因此漏掉长按路径。本版共用 `qsActivityCovered(started, cancelled, sourceAlpha)`，在已开始、未取消且来源 alpha 为 0 时，于原生 finish/reset 恢复来源容器之前清零该窗口的模糊并撤掉 tint；失败回调撤销清理。原有焦点返回和 QS 新一轮展开恢复规则保留。

Wi-Fi 名称、数据卡名称及蓝牙设备等详情内容也由 SystemUI 的列表行绘制，数据由对应系统服务提供。`OplusQSDetailItems$ContentHolder` 使用 `inflate(..., false)` 创建尚未挂载的 `qs_detail_item`，初始文字颜色来自 XML 的主题属性，随后由 Adapter 绑定内容；仅靠祖先链和创建详情根时遍历会漏掉这些动态行。本版在共享 `OplusQSDetailItems$Adapter.onBindViewHolder(RecyclerView$ViewHolder, int)` 完成后遍历行并登记文本角色，同时覆盖已位于详情范围的 RecyclerView 重新绑定。名称/标题使用 onSurface，summary、summary2、提示及 Wi-Fi 的 `frequency_tag` 使用 onSurfaceVariant；已有文本 setter 和主题刷新继续生效。

应用内 `ConfigMasterSwitch` 的起始内边距采用 28dp、末尾 20dp；网络类型与返回手势页复用同一组件。多作用域重启确认正文使用 `fillMaxWidth + TextAlign.Center`，共享带图标对话框的标题也显式居中，包含换行后的文字。

`Amalor_v26.11.10_22-release.apk` 已通过 `:app:assembleRelease --offline --console=plain --no-daemon -PVERSION_NAME=26.11.10`、Release/R8、lintVitalRelease、生产类 QS/数据卡/返回手势检查及 APK v2 签名验证（CN=Acardia，1 个 signer）。Manifest 为 26.11.10 / code 22 / SDK 37；实际 DEX 已核对 `qs-activity-covered`、`detail-row-bound`、`detail-recycler-bound`、`frequency_tag` 和两个文本角色，旧 `more-settings-covered` 注册与 `isMoreSettingAnimator` 字符串已移除。ADB 已连接并只读确认设备仍安装 26.11.9；本版尚未安装或进行真机视觉回归，更新后需重启 SystemUI。

### 26.11.9：撤除磁贴局部模糊，二级文本使用系统角色

按用户反馈改为磁贴纯取色：MixColor/Gradient/StateList 的 draw 只按原生 Path 绘制角色 Paint，保留完整系统 ARGB 与 Drawable 的过渡 alpha。移除模块的 AutoBlurDrawable/Builder 补建、8dp 参数提交、后台缓存和磁贴页面/详情/焦点模糊门控；磁贴及详情范围的原生代理不再启用局部模糊。原有激活 primary、未激活 SE1/SE2、不可用弱化角色及图标取色继续由真实状态决定，过渡中的背景来源仅用于角色颜色和原生圆角。

二级菜单主要文本使用 `android:color/system_on_surface_light/dark`，次要文本使用 `system_on_surface_variant_light/dark`。已核对 C17 的 `android:id/title` / `android:id/summary`、`detail_items_title` / `detail_items_summary` / `detail_items_summary2`、`detail_hint_textview` 与 `qs_hint_textview`；按资源语义区分角色。覆盖 TextView 的两种 setTextColor 输入、attach 后的首次着色与主题刷新，包含现代详情根、旧版 `detail_container` 和 OplusQSDetailItems 动态列表。使用控件自身的 night Context，防止原生列表绑定或深浅模式切换写回旧颜色。

整面板的 34dp 窗口模糊与取色合成继续工作。分离 QS 使用原生 `qsPanelScrimParams.getFraction()`，普通 QS 为 0 时也能展开；更多设置来源窗口清理在 QS 新一轮 `0 → 正数` 展开时释放，不要求旧详情容器重新显示或等待一次焦点回调。磁贴纯取色已不依赖这条窗口模糊生命周期。

`Amalor_v26.11.9_22-release.apk` 已通过 Release/R8、lintVitalRelease、生产类 QS 面板/ARGB/文本分类检查及数据卡/返回手势检查，APK v2 签名验证通过（CN=Acardia，1 个 signer），Manifest 为 26.11.9 / code 22 / SDK 37。mapping 为 QsTileAppearanceHook → in1、QsPanelBlurHook → tm1。实际 DEX 已核对 `tile-role-fill`、`detail-text`、`detail-text-attached` 与两个文本角色；旧局部模糊/Builder tag/磁贴焦点门控注册字符串均不存在。三语言开关文案已核对打包。设备当前未通过 ADB 连接，以上核验不等同本版的真机视觉验收。

### 26.11.6：独立 QS 页面、后台状态与更多设置来源窗口

磁贴页面可见性使用 ScrimController 的 rawPanelExpansionFraction、QsBottomVisible 和来源窗口的 windowVisibility/attach，避免分离控制中心下沿用普通 QS expansion 值造成错误清理。创建独立动画后台前临时清空 `QSBlurConfigProvider.getBlurDrawableTag(View)`，在 finally 中通过 `setBlurDrawableTag(View, Drawable)` 恢复原标签，防止原生 Builder 借用并修改来源 View 的 OEM Drawable。已核对这两个 C17 方法均为 public static final，tag 的类型是通用 Drawable。模糊量和启用状态同时核对原生 `getBlurAmount` / `getBlurEnabled`，而非只相信模块的上一帧缓存。

更多设置清理限定于 `OplusQSTransitionAnimatorController$createController$1.onTransitionAnimationEnd`：确认 isMoreSettingAnimator、已开始、未取消且来源 container 的 alpha 为 0 后，才标记该来源窗口、关闭磁贴磨砂门控并将其窗口半径清零。该窗口的 `ViewBlurProxy.applyWindowBgBlur` 不得随后重新写入旧半径；来源窗口重新获得焦点时释放标记，失败的结束回调也撤销标记。普通启动、取消及尚未隐藏来源窗口的过渡不进入这条清理路径。

`Amalor_v26.11.6_22-release.apk` 已通过 Release/R8、`lintVitalRelease`、QS 页面/焦点规则检查及 APK v2 签名验证（1 个 signer，CN=Acardia）；Manifest 为 26.11.6 / code 22 / SDK 37。当前 mapping：QsTileAppearanceHook → rn1，QsPanelBlurHook → ym1。实际 APK 已核对新的 blur-tag/getter 与 more-settings-covered 注册字符串。构建和规则检查不代表 Activity 内闪现及二级菜单连续性的真机视觉验收。

### 26.11.5：回退设置页背景抑制

按用户要求回退 26.11.4 的设置页启动背景抑制：移除 keepPanelForAppLaunch、面板代理 setBlurEnabled 拦截、app-launch setter Hook、额外 detach 监听和 onDraw 的背景持有/原 Drawable alpha 判断。clear 恢复此前立即恢复 OEM 代理、清理来源 View 的路径；窗口模糊未接管时由原 onDraw 处理。原有系统启动禁用检查和详情开关过渡逻辑保留。

SE1/SE2 完整 ARGB 与 `roleAlpha × drawableAlpha / 255` 修正保留。用户澄清原现象发生在从二级菜单的更多设置进入对应功能 Activity 后，26.11.4 的交接推断不构成该问题的已验证根因，相关修复已撤回。

### 26.11.4：保留角色透明度与设置页启动交接（交接修改已回退）

通过当前设备的 `cmd overlay lookup --verbose com.android.systemui android:color/system_surface_effect_2_dark` 核对：night 配置下 SE2 为 `#26D6EAB4`（alpha 38，约 15%），primary 为 `#FFBACD9A`；`customColorSurfaceEffect2` 的 night 别名解析到同一 SE2。壁纸/系统配色变化后 RGB 可以改变，这些是本次设备读数，不是生产代码常量。原生 TileDefaults 的未激活双目标内圆圈读取完整 surfaceEffect2；激活内圆圈才读取 primary。前版仅保留了 RGB，却用 Drawable alpha 或固定 224 覆盖角色 alpha，导致深色未激活内圆圈过实。本版统一以 `roleAlpha × drawableAlpha / 255` 绘制，去除固定 224，保留 SE1/SE2、primary 及不可用角色的原始 ARGB。

更多设置入口为 `QSDetailFooterViewModel.onSettingsBtnClick` → `OplusQSActivityStarter.onAnimationButtonClicked(..., DIALOG)`。原生 DepthController 在启动动画约 98% 且 shade 半径仍大于 0 时设置 app-launch blur inhibition，并保持到 shade 收起。26.11.4 曾据此将窗口半径撤除与背景所有权释放分开，但未验证用户所指的 Activity 进入后现象；用户反馈带来 bug 后，此修改在 26.11.5 回退。

该旧版曾增加面板范围 setBlurEnabled Hook、ThreadLocal 写入范围、原生 setBlursDisabledForAppLaunch 监听及 mDrawable alpha 绘制判断；这些代码和启动交接持有规则检查一并回退。资源 alpha 和磁贴过渡检查继续保留。

### 26.11.3：动画窗口交接、收起准备与深色配置

临时动画容器负责原生模糊的绘制、bounds、Path 和 alpha，模糊可用性及角色颜色则仍由原磁贴的 View/窗口持有。详情窗口和控制中心窗口不同，也不会因临时容器 root 不匹配而撤掉磨砂。AOSP OverlayViewGroup 不接入普通 parent 层级，实际绘制时 isShown 也可能为 false；已确认的动画背景在 draw Hook 中检查 attach，普通磁贴仍检查 isShown。来源/动画容器已 detach、主页面收起或平台模糊不可用时仍停止绘制。

在有效的详情收起入口设置 closing 后，立即为已经记录的背景 surface 提交 8dp/None 配置、启用原生代理并请求后台准备，而非等首次收起绘制或 onDismiss 才创建后台。原生 Target controller 的 targetBlurBackgroundView 用于核对实际关闭背景及圆角 provider，内容子区域保持纯色。被拒绝的重复显示/收起请求不改写当前过渡状态。

调色板按控件 Context 的 night 状态分别缓存，不再使用 Application 或全局 Context 的单份缓存覆盖控件自身的 uiMode。配置/主题事件清空两份缓存并刷新。2×1 插件和原生 highlight 的内圆圈均读取 QSIconViewProxy 的真实 state/disabledByPolicy：激活 primary、未激活 SE2。SDK 37 的 customColorSurfaceEffect2 已核对有独立 night 别名；保留设备资源解析，不写死参考 RGB。调色板 DEBUG 日志补充 SE2 值，INFO 只保留安装摘要。

### 26.11.2：日志等级、菜单过渡与内圆圈状态

模糊半径/参数与调色板值改为 DEBUG 日志，INFO 保留组件安装等摘要。磁贴局部模糊的页面可用性与整面板 AOSP 窗口模糊的 activeRoot 分开管理，避免详情动画暂时撤掉窗口半径时同时撤掉磁贴磨砂；完全收起、真正失焦、应用启动/解锁、亮度镜像及平台能力变化继续约束磁贴可用性。

详情在 `dismiss(false)` 和 `DetailAnimationManager.hideDetailToView` 发起收起时就将背景切回模糊，不等待 `onDismiss`/View detach。显示中的详情和关闭中的内容子区域仍为纯色；关闭背景根据原磁贴角色恢复。系统背景工厂保留原生代理并提供纯色 defaultDrawable，关闭时能立即恢复已有后台。

`GhostedViewTransitionAnimatorController` 将背景移到临时 backgroundView，而 WrappedDrawable 绘制后可能将源 Drawable alpha 归零。模块在详情动画的 start/end controller 记录背景来源，在临时 WrappedDrawable 绘制中使用实际动画容器及 blockRadiusProvider，保留原有几何/alpha 过渡，并为临时容器使用独立的原生代理，避免隐藏的原视图及源 alpha 归零撤销模糊。每个 View 最多保留四个 Drawable 独立 surface 槽位，过渡双层不再每帧相互隐藏/替换代理；超出槽位和 View detach 时释放相应资源。

2×1 内圆圈的背景状态改为 `OplusQSIconView.iconViewProxy` 的真实 QSTile state 和 disabledByPolicy。state 2 对应 primary、state 1 对应 SE2；不再让过渡 Drawable 的旧 inactive/unavailable flags 覆盖当前状态。原磁贴祖先的识别优先于外层详情容器，避免移入动画层后被误当成纯色详情。系统角色值和深浅配置的读取规则保持一致。

磁贴使用 ColorOS 的 `AutoBlurDrawable / ViewBlurProxy`，不自行创建 AOSP 局部 `BackgroundBlurDrawable`。已有 MixColor 背景复用系统代理；Gradient/StateList 背景委托运行时 `MixColorTileDrawable.Builder.build` 创建原生 AutoBlurDrawable，保留外层 Drawable 的实际类型。

普通 1×1 磁贴和 2×1 内圆圈仅未激活、可用时磨砂；2×1 外卡片在激活和未激活时都磨砂并保持完整 SE1 ARGB。激活的 1×1/内圆圈仍是纯色 primary，不可用状态使用弱化填充。原生代理最大半径设为 8dp，去除旧平台/运动混色，前景使用系统角色原有透明度并乘 Drawable 的过渡 alpha；系统无法提供模糊、软件绘制时仍使用角色 ARGB 兜底。

### 模糊延迟、残留与更新开销

前版独立 AOSP 区域需要额外生命周期清理，随后加入“完整展开才启用”的限制虽然消除了残留，却造成展开末尾磨砂突变。本版撤销完整展开比例以及 View alpha 必须等于 1 的限制，使用系统现有代理的模糊量、形状、后台选择和可见性处理。

主页面从展开比例大于 0 时就可以绘制磨砂；收起过程中也跟随原生 alpha/blurAmount 连续变化，完全收起和 `instantCollapse` 时清理。`MixColorTileDrawable.setAlpha` 原本就会调用 `ViewBlurProxy.setBlurAmount(alpha / 255)`；模块补建的代理沿用同一规则，不再等完整展开。整面板的 34dp AOSP 窗口模糊仍按原有动画计算。

二级页覆盖 `OplusQSDetailInteractor.show/onShow/onDismiss/onIntercept`，以及旧 `OplusQSDetailDialogProvider.showDetail/onStop`。打开/关闭详情不再主动隐藏磁贴的 AutoBlurDrawable；控制中心仍可见时保持磨砂，详情窗口造成的失焦由自身生命周期持有，关闭后等待主窗口焦点交接，避免过渡期间短暂撤掉模糊。拒绝/失败请求撤销记录；完全收起、`instantCollapse`、无详情窗口的真正失焦及 View detach 仍清理。旧入口跨线程请求由系统转发到 UI 线程后处理；通知及重新核对限定在主线程。补建代理在 View detach 时禁用并释放模块 callback，已有系统代理保留自己的 callback。

系统角色颜色按控件的深浅配置缓存，在 ConfigurationController 配置/主题事件中失效。每个背景 View 复用原生代理，只在配置、bounds、alpha 或可见性变化时提交模块更新；原生 AutoBlurDrawable 自身的 draw/后台缓存逻辑继续执行。模块不再每帧更新独立 AOSP 区域。

这些是代码层面的开销减少，尚未采集真机帧时间/GPU 数据，不能据此宣称性能提升幅度。

### 背景构造兼容与图标取色修正

用户日志中的 `backgrounds fallback / Array contains no element matching the predicate` 来自前版按 11 个参数查找 BlurConfig 构造器；运行时 Common 库没有该构造器，背景组件在注册绘制 Hook 前就中断。当前不再反射创建 BlurConfig/ViewBlurProxy，不依赖其参数数量、默认参数掩码或内部构造 ABI，而让设备自己的 MixColor Builder 完成构造；备用 Builder 延迟初始化。

Builder 使用单个默认 `DrawableState(intArrayOf())`，配置为 `kotlin.Triple(None, 0, null)`；第三项是 nullable StrokeParamsTemplate，不能传整数。模糊创建/绘制异常现在只关闭磨砂部分，后续系统角色 Paint 染色仍执行，避免因可选后台失败而退回无染色背景。

图标角色色直接接入 `QSIconViewProxy.setTintList` 的状态列表输入，覆盖原生主题/焕彩列表；列表禁用状态优先，激活为 onPrimary，未激活为 onSurface。最终 ImageView 与 Lottie JSON 着色读取代理的 QSTile state（0/1/2）和 disabledByPolicy，避免状态更新期间用旧 View.drawableState 写回错误角色；备用 TileIconColorState getter 按各条 state spec 解析，不写死颜色数组下标。

2×1 外卡片的标题和描述采用 onSurface，与圆圈内的 onPrimary 分开。Hook 限定到原生 `OplusQSHighlightTileView` 和插件 `OplusQSResizeableTileViewTwoXOne` 的 TextSwitcher/QsLabelView 着色入口，并随主题刷新已有文字，其他尺寸不进入此标题策略。

模型无法直接查看用户提供的 `ColorOS.jpg` / `原生.png`，本轮未进行图片视觉比对；面板调整依据原生 APK 的调用和叠层证据，实际白底效果仍需设备验证。

## 原生参考身份与发现

参考 APK 为 Google Android 17 SystemUI，包名 `com.android.systemui`，versionName 17、versionCode 37、SDK 37。它的 QS 使用 Compose infinitegrid，并有普通磁贴和双目标大磁贴。

已解码的关键入口：

- `TileDefaults.getColorForState(TileUiState, boolean, Composer, int)`。
- `TileColors` 的 `background/iconBackground/label/secondaryLabel/icon` 五个字段。
- `AndroidColorScheme.Companion.invoke(Context)` 的 surfaceEffect1/2 资源读取。
- `BlurPanelColors.panelColor(Context)`。
- `ShadeColors.shadePanel(Context, boolean, boolean)`。

原生颜色分配：

| 状态/区域 | 原生使用的角色 | 模块对应 |
| --- | --- | --- |
| 普通磁贴激活背景 | primary | 系统 primary，保留角色 alpha |
| 激活图标 | onPrimary | 系统 onPrimary，保留角色 alpha |
| 普通磁贴未激活背景 | surfaceEffect1 | customColorSurfaceEffect1 |
| 2×1 双目标外背景，激活或未激活 | surfaceEffect1 | customColorSurfaceEffect1 |
| 2×1 双目标未激活图标圆圈 | surfaceEffect2 | customColorSurfaceEffect2 |
| 2×1 双目标激活图标圆圈 | primary | 系统 primary |
| 未激活图标/文字 | onSurface | 图标使用系统 onSurface，文字保持原生处理 |
| 不可用背景 | surface × 0.18 alpha | 系统 surface，alpha 46 |
| 不可用图标 | onSurfaceVariant × 0.38 alpha | 系统 onSurfaceVariant，alpha 97 |

读取 `customColorSurfaceEffect1/2` 时由当前配置和 Theme 解析；别名不存在时读取对应 `system_surface_effect_1/2_light/dark`。参考 APK 和不同 ROM 的内部资源数字 ID 可能不同，模块按语义名称解析，不把参考 APK 的内部 ID 直接写入 ColorOS Hook。

上一版自行叠加的 6dp/12dp tonal elevation，以及激活 alpha 184、未激活 alpha 102，已移除。模块不从 accent swatch 重建色种，不自行选择 Spec 2021/2025，不写死某张壁纸的 RGB。实际系统值写入 `QS native 17 colors` 日志。

## 原生面板合成

`BlurPanelColors.panelColor` 在参考 APK 中执行：

1. 读取面板 scrim。
2. 读取面板背景颜色。
3. `ColorUtils.compositeColors(scrim, background)`。
4. 原生 `ShadePanelScrim` 调用 `shadePanel(context, blurSupported, true)`，启用 SE0 底层；将第 3 步面板色叠在 SE0 上。

已通过 SDK 37 的资源表和 XML 核对名称：`shade_panel_scrim` / `shade_panel_scrim_color`、`shade_panel_bg_color`。背景 selector 使用系统 neutral1_500，alpha 0.32，浅色 lStar 98、深色 lStar 4；scrim selector 是 alpha 0.4 的 #404040。实际颜色由设备 framework/RRO 解析，模块复用解析值及合成顺序。

模块已接入此前遗漏的 SE0 底层，采用 `compositeColors(basePanel, surfaceEffect0)`；遮罩只将最终 alpha 乘展开比例，不再加入 surfaceTint/elevation 染色。角色解析值仍来自当前系统，未根据无法查看的图片猜测或写入 RGB。

整面板模糊最大半径按参考的 `max_shade_window_blur_radius = 34dp` 换算，不再使用 ColorOS BlurUtils 自带的最大半径配置；展开及亮度镜像等生命周期继续使用现有系统路径。

## ColorOS 布局与背景区域

保留 ColorOS View/Drawable 类型和布局，使用原生形状及状态：

- 普通 1×1：`OplusQSTileBaseView.mBg` 或插件 1×1 的 `getBg()`。
- 插件 2×1 外卡片：`OplusQSResizeableTileView.getBg()`，始终使用 SE1，不随激活切换成整块 primary。
- 插件 2×1 内圆圈：`OplusQSIconView.iconBgView`，并核对 `showIconBg` 和 `spanSize`。
- 原生 highlight：区分 `mBg` 和 `mIconFrame`。
- 未知层级、其他尺寸和三段式外容器不进入这套替换绘制。

原生 Builder 返回类型保持不变，已撤回曾导致类型契约错误的 Gradient → MixColor 返回值替换。状态表和点击逻辑继续由系统处理。

激活普通磁贴和激活内圆圈绘制 primary；图标绘制 onPrimary。未激活普通磁贴与 2×1 外卡片为 SE1，未激活圆圈为 SE2；2×1 外卡片激活时同样保持 SE1 和上述 8dp 系统磨砂，角色 alpha 不改写。

磁贴绘制继续裁剪/填充原生 Path、同步 Drawable alpha，不替换 Builder 返回类型。每个目标 View 复用系统原生 AutoBlurDrawable，主页面隐藏/脱离时执行相应可见性及清理逻辑。

## 图标、主题与高光

保留 `ConfigurationControllerImpl.onConfigurationChanged` / `notifyThemeChanged` 的配置提交刷新。颜色直接从当前系统资源读取，原生 Lazy/旧 shader 色表不决定最终填充。

两种系统样式都使用统一角色，不将每磁贴的红/蓝独立色作为新色种。“全新焕彩” Lottie 的 startFill/endFill/startStroke/endStroke 及全路径 COLOR_FILTER 回调统一到当前角色；ImageView 同样使用 SRC_IN。系统样式设置和动画时间保持原生处理。

继续停用磁贴范围的彩色 spotlight，保留触摸、点击及缩放反馈；继续清除白色渐变描边。磁贴的 OEM 模糊代理恢复使用，但其旧平台/运动混色配置清为 None/null，前景使用统一系统角色染色；其他控件保留原路径。

## 配置与验证

### 过渡 Drawable 接收者与二级菜单纯色

`TileTransitionDrawable` 继承 `TileLayerDrawable / LayerDrawable`，不是 `TileDrawableWrapper`。它分别绘制 backgroundDrawable/foregroundDrawable，子层才是可接收 Wrapper 路径方法的对象。前版全局 `ViewBlurProxy.applyBlurConfig` Hook 将 View 的外层过渡背景直接传给 `getPathProvider`，触发 Expected receiver 类型异常。本版在共享配置入口先核对 Wrapper 类型；过渡层的实际子 Drawable 在各自 draw Hook 中配置路径，保留原生过渡类型及状态动画。

二级菜单改用不透明的系统 `surfaceContainerHigh` 色，遵循菜单自身的深浅色 Context；资源不可用时使用系统 surface 角色兜底。新详情页从 `prepareDetailView` 记录内容根，旧详情页和手电筒详情同时按原生 View 层级识别。`OplusQsDialogBlurBackgroundExImp` 及 `QsDetailBackgroundUtils` 的带默认背景入口复用系统传入的圆角 GradientDrawable 并保留原生代理；显示详情时 `getBlurDrawable` 返回纯色默认背景，关闭背景从收起入口开始恢复模糊。MixColor/Gradient/StateList 的显示详情绘制使用原路径填充纯色，最大半径为 0；关闭内容子区域不切换。识别以详情内容的祖先链为准，不以整个 Window root 为准，因此不会误停同一窗口下主磁贴的磨砂。

`tests/QsAppearanceRulesCheck.java` 覆盖显示详情、关闭详情等待焦点、真正失焦、完全收起、动画来源窗口/attach 所有权、过渡 Drawable 旧状态和系统角色 alpha 等组合；使用编译后的生产 Kotlin 类和 Kotlin stdlib 运行。

修复包 `Amalor_v26.11.1_22-release.apk` 已通过 Release 构建、`lintVitalRelease`、页面/焦点规则检查及 APK v2 签名验证。已在实际 Release DEX 的 `en1.h` 核对：配置函数先执行 Wrapper `Class.isInstance` 检查，非 Wrapper 在调用任何路径方法前返回；详情种类进入半径 0 分支。二级页纯色 Hook 及 surfaceContainerHigh/surface 资源名称也已核对。

本轮最终包 `Amalor_v26.11.3_22-release.apk` 已通过 `:app:assembleRelease --offline --console=plain --no-daemon -PVERSION_NAME=26.11.3`、`lintVitalRelease`、扩展后的规则检查和 APK v2 签名验证（1 个 signer）。Manifest 核对 versionName 26.11.3、versionCode 22、SDK 37。对应 mapping 为 QsTileAppearanceHook → kn1、QsPanelBlurHook → rm1：

- `kn1.a(Context)` 从传入 Context 读取 night 状态并用 Boolean key 缓存；调色板日志调用的等级常量为 3（DEBUG），包含 SE2。
- `jm1.doFrame` 的模糊半径日志同样为 DEBUG；INFO 没有逐帧模糊/圆角参数输出。
- `ym1.l` 的绘制分支已核对：动画使用 attach，普通磁贴使用 isShown；颜色和 `rm1.e` 模糊归属使用 BackgroundTarget.blurOwner，而非临时 overlay root。
- `kn1.r` 在 invalidate 前调用 closing 准备；`vm1.l` 核对为先配置原生 surface，再启用代理并请求 getBlurDrawable。`targetBlurBackgroundView`、详情关闭入口及两种内圆圈的 iconViewProxy 均已打包。

这些核验覆盖代码与 APK，菜单过渡连续性及设备实际深色角色仍需真机验证。

`Amalor_v26.11.4_22-release.apk` 已通过 Release/R8、lintVitalRelease、扩展规则检查及 APK v2 签名验证；Manifest 为 26.11.4 / code 22 / SDK 37。对应 mapping 为 QsTileAppearanceHook → mn1、QsPanelBlurHook → tm1。实际 DEX `an1.l` 已核对为读取角色 alpha（无符号右移 24 位）、乘 Drawable alpha 后除 255，Paint 不再使用固定 224；旧版的 keepPanel、代理所有权及 app-launch Hook 曾进行静态核验，但用户反馈后已回退。静态核验不代表该 Activity 进入后问题得到修复。

回退包 `Amalor_v26.11.5_22-release.apk` 已通过 Release/R8、lintVitalRelease、保留的页面/状态/ARGB 规则检查及 APK v2 签名验证（1 个 signer）。最新 mapping 为 QsPanelBlurHook → rm1、QsTileAppearanceHook → kn1；实际 APK 的 Hook 字符串查询已确认 `qs-appearance:panel-owned-proxy`、`qs-appearance:panel-app-launch` 均不存在。多作用域重启确认弹窗的三语言标题、包名括号及每行一个的换行字符串已核对打包。

开关默认关闭，使用现有 `amalor` 本地/远程同步。更新后重启 SystemUI；关闭并重启恢复原生路径。

静态检查已覆盖参考 APK 的颜色分支、角色字段、面板合成顺序，以及 ColorOS 的目标 View/方法/返回类型。Release 构建与 APK 检查不等同真机视觉验证。

### 本轮构建与 APK 核验

- `cmd.exe /c gradlew.bat :app:assembleRelease --offline --console=plain` 通过，包括 Release Kotlin 编译、R8 和 `lintVitalRelease`。
- 完整 `:app:lintRelease` 已执行，但被工作区已有的 118 个错误阻塞：1 个 `AmalorApp.kt` 的 `UnusedBoxWithConstraintsScope`，其余为资源 `MissingTranslation`。本次两个磁贴 Hook 文件没有 lint 错误，仅有已有内部 API 反射警告；未禁用检查或生成 baseline 来隐藏错误。
- APK：`app/build/outputs/apk/release/Amalor_v26.10_22-release.apk`；包名 `me.acardia.amalor`，版本 `26.10` / code `22`，min/target SDK `37`。APK v2 签名验证通过，签名者 `CN=Acardia`。
- 系统 Builder/DrawableState/Triple 入口、`getBlurDrawable/applyBlurConfig/setBlurAmount`、AutoBlurDrawable 可见性及 OEM BlendDrawable 的 None 纯模糊分支已核对；实际 Release DEX 用对应 mapping 核验主页面条件与外卡片激活分支。
- SDK 37 的 `View.dispatchWindowFocusChanged(boolean)` 及目标 OEM 的详情显示/关闭/取消入口签名已核对。
- 主页面条件仍为比例 `> 0`；HighlightCard 直接进入 8dp 分支，不受激活 attr 排除。系统 AutoBlurDrawable/代理入口及配置 Hook 字符串需与对应 Release mapping 一起核验；前版的 R8 名称不能用于定位新版方法。
- 构造/图标修复版已通过 Release 构建、`lintVitalRelease`、APK v2 签名核验。对应 DEX 的 `QsTileAppearanceHook` 为 `im1`，新增 `qs-appearance:icon-tint-source`、OEM Builder/DrawableState/Triple 与独立 native-blur 兜底字符串已核对；已解码 `im1.m`，确认最终图标按代理 policy、state 0/2 分配 disabledIcon/onActive/onInactive。设备冷启动安装与实际颜色仍需真机验证。

真机重点检查：

1. 激活普通磁贴和激活 2×1 圆圈保持纯色 primary/onPrimary，局部模糊已移除。
2. 未激活普通磁贴为 SE1 RGB；2×1 外背景激活/未激活都是 SE1 磨砂，未激活圆圈为 SE2 磨砂，激活圆圈为 primary 纯色；标题为 onSurface。
3. 白色背景下整面板的 SE0 合成、34dp 模糊，以及展开/收起、亮度镜像、省电、锁屏/解锁。
4. 全新焕彩/跟随系统切换、连续点击、长按取消、描边和原本红色的图标。
5. 核对 `QS native 17 colors` 日志与设备 framework 资源；不能仅从另一套调色板显示值推定设备的实际角色 RGB。
6. 半下拉到完整展开、缓慢上滑收起与取消过程中磨砂连续变化，展开末尾不突现；打开/关闭 Wi-Fi 等二级页时主磁贴的磨砂不消失，详情卡片保持纯色圆角，完全收起及切入其他应用后不残留。
7. 冷启动日志出现 `QS appearance installed: backgrounds`，没有构造器数组 predicate 异常；未激活背景保留 SE1/SE2 染色，激活小磁贴/2×1 内圆圈图标为 onPrimary，未激活图标为 onSurface。
8. 日志出现 `QS appearance installed: detail backgrounds`；状态切换时没有将 TileTransitionDrawable 作为 TileDrawableWrapper 接收者的异常。检查普通二级菜单、手电筒详情、连续打开/关闭、过渡取消和详情页期间的主题变更。

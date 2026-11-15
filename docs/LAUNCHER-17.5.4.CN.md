# 26.11.32：Launcher 17.5.4 兼容修复

依据本地 `系统桌面_17.3.12.APK` 与 `系统桌面_17.5.4.APK` 的 DEX、资源表及实际调用链适配。17.5.4 的 versionCode 为 170050004，target SDK 37。

## 自定义主屏

17.5.4 将 `Workspace.initWorkspace()` 内联到构造器，并将基类 `moveToDefaultScreen()` 的实现内联到 `OplusWorkspace.moveToDefaultScreen()`。该子类入口还会自行重写 `Workspace.DEFAULT_PAGE`，在方法开始前临时赋值不足以覆盖实际目标。

现在在 Workspace 构造完成时设置初始页；在 OplusWorkspace 主屏跳转的作用域内重定向 `PagedView.snapToPage(int)`。原生因“已位于系统默认页”而跳过 snap 时，补充对所选页的跳转。跳转沿用原生 NORMAL 状态判断并按实际页面数量收敛。0 或非法设置保留原生行为。

## 显示小组件入口

- `WorkspaceItemInfo.isPromise()` 已移除，其逻辑保留为 `status & 0x40000003 != 0`。检查包含 OEM 高位，不使用语义不同的 `hasPromiseIconUi()` 代替。
- `WidgetCell/OplusWidgetCell.applyFromCellItem` 从四参数缩减为 `(WidgetItem, Consumer)`；按签名选择，保留旧版的 `scale=1f` 与空预览参数，并通过虚调用到达 OplusWidgetCell 的具体实现。
- 新版删除了相关 `R` 字段，改从 Launcher Resources 按 `widgets_table`、`widget_cell_oplus`/`widget_cell`、`activity_not_available` 查找资源。
- `CONTAINER_BOTTOM_WIDGETS_TRAY=-112` 已分别从旧版常量和新版原生绑定代码核对。菜单工厂、面板、预览回调及点击/长按链路也已核对。

## 原生预测性返回动画

通过稳定的 `QuickstepTransitionManager.mBackAnimationController` 字段类型解析控制器。新版控制器虽被混淆为 `com.android.quickstep.d`，运行期不依赖这个短类名。

| 入口/状态 | 17.3.12 | 17.5.4 |
| --- | --- | --- |
| 开始回调 | `m4.b(BackMotionEvent, u3)` | `jk5.b(BackMotionEvent, rj5)` |
| 进度回调 | `u3.a(BackEvent)` | 已内联到 `jk5.d(float)` |
| 起始边界 / 触点 | `c` / `z` | `r` / `F` |
| 当前矩形 / 裁剪 | `s` / `o` | `y` / `u` |
| 会话进度 | `q.d` | `w.d` |
| 初始 / 当前圆角 | `y` / `d0` | `E` / `j0` |
| Launcher 弱引用 | `w` | `C` |
| 目标圆角 / 结束 | `v()` / `s()` | `k()` / `h()` |
| 提交变换 | `l(RectF,float,Rect,float,float,boolean)` | `a(同一签名)` |

开始入口、具体回调及变换方法按参数形状解析，字段布局有类型校验。新版在构造 BackEvent 前将动画值除以 100，因此适配器取相同的原始进度，再进入既有 AOSP 0.85 比例规则。两版共享开关快照、侧边判定、取消/结束清理及原生图标交接流程。

## 验证

新增 `LauncherCompatibilityCheck`，覆盖两版字段布局、混淆类解析、回调内联、进度单位、两版小组件绑定与实际预览回调、promise 高位、主屏默认/空页/越界规则。既有 `BackGestureRulesCheck` 纳入统一检查入口。

`Amalor_v26.11.32_22-release.apk` 已通过 10 组 JVM 检查、118 个三语言资源键检查、Release/R8、lintVitalRelease 和 APK v2 签名验证（CN=Acardia）。最终 Manifest/DEX 已核对。此次为两版 APK 调用链与构建验证，尚未做 17.5.4 真机交互验收。

覆盖安装后重启系统桌面；可从“返回手势”页使用双作用域重启入口刷新系统界面和桌面。

# 双排信号无 Smali 方案调查

## 结论

可以不依赖 Smali，但不能直接把 AOSP 的 `BatteryCanvas` 或 AOSP 双排信号 Composable
复制到 ColorOS。AOSP 源码使用自己的 `SignalIcon`/`MobileIconViewModel`、Compose
状态和绘制模型；当前 ColorOS 已经把信号入口换成了
`OplusStackedMobileIconLayoutStrategy` 和 `DualSim`，两者的 ABI、资源和模型不完全相同。

可行的无 Smali 方案是：保留 ColorOS 的原生 Compose 容器和 `DualSim` 状态，只替换
`SignalIcon-*` 的绘制 lambda，使用 Java/Kotlin 通过反射调用目标 SystemUI 自己的
Compose Canvas API，或使用 Android `Canvas` 生成 `ImageBitmap` 后交给原生布局。

## 当前方案

Amalor 当前加载 `assets/stacked-signal.dex`，由 Smali donor 提供 C16 的
`FourBarsDimensions`、`FiveBarsDimensions` 和双卡图形绘制函数，然后注入到 ColorOS
的 Compose `SignalIcon-*` 方法。这种方案的优点是绘制代码与 ONextBox 已知实现一致；缺点是
donor 依赖 Kotlin/Compose 编译产物，版本耦合高，且需要维护额外 DEX。

## 无 Smali 方案的三个层级

### 1. 最推荐：复用 ColorOS 原生信号绘制，改布局

不重新绘制信号图标，只 Hook `OplusStackedMobileIconLayoutStrategy` 的布局组合，
让原生两个 SIM 的 signal composable 按 Column/Row 排列。优点是完全保留 ColorOS
图标、颜色、漫游、无服务、5G/Tigo 和动画；只需要处理两个原生 signal slot 的
`Modifier`、尺寸和排列。

这要求先确认当前方法是否已经接收两个可组合内容或两个图标模型。如果 ColorOS 的
策略只在一个 `SignalIcon-*` 方法内直接创建双卡图形，则无法只改 Modifier，需要进入第 2 层。

### 2. 可行：Kotlin/Java Canvas 重画，但不生成 DEX

在 Hook 中直接创建一个 `android.graphics.drawable.Drawable` 或 `ImageBitmap`，根据
`DualSim` 的两个 cell 状态绘制两组信号格，再把它作为原生 Compose Canvas 的内容。
数据可直接使用现有方法参数：

- 主卡/副卡的 signal level。
- 每张卡的最大格数。
- warning/no-service 等状态。
- ColorOS 传入的颜色和 `Modifier`。

这种方案不需要 donor DEX，但仍需要在运行时调用目标 Compose 方法，或者替换整个
`SignalIcon-*` 返回的 UI。手写 `Canvas` 时必须自己维护 ColorOS 的 4/5 格形状、
颜色、RTL、动画和无服务状态，视觉一致性不如复用 ColorOS 原生绘制。

### 3. 复用 AOSP SignalDrawable

可以尝试使用 `com.android.settingslib.graph.SignalDrawable` 或 SystemUI 自己的
signal Drawable，但它通常表示一个单独的蜂窝信号状态，不能直接表达 ColorOS 的
双 SIM 堆叠布局，也不会自动理解 `DualSim`、漫游、Tigo 或厂商状态字段。
它适合作为兼容回退或验证绘制方向，不适合作为 ColorOS 17 的最终视觉实现。

## 对 AOSP 源码的判断

AOSP 的 Composable 源码值得借鉴“布局和状态分离”的结构，但不应直接移植：

- AOSP 的 `BatteryCanvas` 是电池专用，不包含双卡信号逻辑。
- AOSP 的 Compose Canvas 通过 `BatteryGlyph` 列表表达状态符号；信号图标也采用类似
  的模型化思路，但 ColorOS 的模型和资源不同。
- RTL 方向不能依赖 Drawable 默认镜像，应像 AOSP 电池填充一样显式改变绘制区域。
- 最稳妥的做法是让 ColorOS 继续产生状态和颜色，Amalor 只改变布局位置或绘制层。

## 下一步建议

先对 `OplusStackedMobileIconLayoutStrategy` 的 `SignalIcon-*` 方法做一次无修改的
参数和返回值记录，重点确认七个参数中是否存在两个可组合内容、`DualSim` 模型和
当前 `Modifier`。如果两个内容都存在，优先实现第 1 层；如果不存在，再实现第 2 层。

在这之前不建议删除现有 donor：它仍是已知可工作的视觉基线。可以新增一个实验开关或
实验类，和 donor 实现并行比较，确认无 Smali 方案的状态覆盖后再替换默认路径。

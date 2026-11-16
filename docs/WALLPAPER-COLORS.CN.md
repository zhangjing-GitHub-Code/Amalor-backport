# 系统应用静态壁纸主题色：ColorSpec 2025

## 使用

1. 在 LSPosed 的 Amalor 作用域中勾选 **壁纸与个性化**（`com.oplus.uxdesign`）。
2. 打开 **Amalor → 配置 → 壁纸与个性化 → 优化取色方案**。
3. 使用右上角按钮重启壁纸与个性化。
4. 在系统设置的 **桌面、锁屏与个性化 → 更多 → 颜色 → 静态壁纸取色** 中重新应用配色。若当前选项没有触发写入，可先选择另一项，再切回。

支持的原厂选项：

| 选项 | 原厂主题索引 | 风格 | 规格 |
|---|---:|---|---|
| 格调 | 21 | TONAL_SPOT | 2025 |
| 清新 | 22 | SPRITZ / Neutral | 2025 |
| 活力 | 23 | VIBRANT | 2025 |
| 饱满 | 24 | EXPRESSIVE | 2025 |

单色以及精选、自定义颜色继续按对应原厂规则处理。配置默认关闭。

## 厂商资源通道

样本：`壁纸与个性化_17.0.15.APK`、`设置_17.0.0.APK` 和真机 `oplus-framework.jar`；设备 PLQ110 / API 37。

正常的 `android:color/system_*` Monet 资源与 ColorOS 的厂商主题资源是两条通道。已在设置进程资源转储中确认：原厂 `couiBlueTintControlNormal` 为 `#FF779A67`，同时正常 `system_primary_light` 为 `#FF476739`。

厂商通道由 `material_color_value` / `OplusExtraConfiguration.mMaterialColor` 控制。`0x40000` 标志启用壁纸主题，`OplusMaterialFile` 读取以下文件，把 COUI/NX 命名颜色合入应用资源：

- `/data/oplus/uxres/uxcolor/coui_theme_color_wallpaper.xml`
- `/data/oplus/uxres/uxcolor/coui_theme_color_wallpaper_night.xml`

非主用户使用对应 userId 子目录。原厂写出器是样本中的 `ua.f`，颜色名契约在 `ua.e`。适配保留其文件路径、权限和 IO 生命周期，替换 XML group 中的角色值。

配置首页图标使用 `ic_uxdesign`，内部列表复用颜色规格的 `ic_color_spec`。

v1.2.1 对预览和 Monet 种子的修改已撤回。当前 Hook 只读原厂 `srcColors[3]`（正常 Monet 所用的 A1 输入），在列表经过厂商 HSL/Lab 转换和重排后，将对应的 2025 角色写入厂商 XML。

## 五个颜色组

原始五色列表是 `[N1,A2,A3,A1,N2]`。原厂先交换 `(4,2)`，再交换 `(2,0)`，实际 XML 顺序为 `[N2,A2,N1,A1,A3]`。颜色组名是协议标识，不能把 Red 当作 error，或把所有组归并为 primary。

v1.2.3 修复了 v1.2.2 的分组归并错误：

| XML 组 | 来源 | Normal | On normal | Container | On container |
|---|---|---|---|---|---|
| Green | N2 | onSurfaceVariant | surfaceVariant | surfaceVariant | onSurfaceVariant |
| Red | A2 | secondary | onSecondary | secondaryContainer | onSecondaryContainer |
| Yellow | N1 | onSurface | surface | surfaceContainer | onSurface |
| Blue | A1 | primary | onPrimary | primaryContainer | onPrimaryContainer |
| Orange | A3 | tertiary | onTertiary | tertiaryContainer | onTertiaryContainer |

每组保留原厂七个资源槽位：普通色、按压色、浅容器色、容器按压色、选中高亮、禁用背景、禁用前景。按压使用各自配对前景的 10% 状态层，高亮使用本组普通色的 40% 透明层。禁用背景/前景统一使用 onSurface 的 12%/38%，符合中性禁用状态的语义。

原厂追加的共享 Green 别名继续对应 N2。COUI 只有一个通用 `coui_color_label_on_color`，因此该共享前景采用反色用途的中性 surface，而非绑定到某一强调色的 onPrimary；对五组普通背景均检查对比度。每组自己的按压/容器状态仍使用其专属 on-role。

## 实现要点

- 复用 MaterialKolor 6.0.0，显式选择 `SPEC_2025` 并跟随系统 contrast。
- `WallpaperColorFamily` 的顺序、XML 名称与原厂契约校验一致，每个组单独查找其角色集。
- 通过最多 64 个列表 identity 关联转换前的输入；不使用可变 ArrayList 的内容 hash。
- XML 写入范围由线程局部状态限定；未知索引、缺少关联、关闭开关时使用原厂输出，嵌套与异常路径恢复原范围。
- UI 选项与壁纸 provider 更新路径共用转换器、写出器，均由同一处适配。
- COUI 与 NX 原厂别名由命名器读取并保留，适配不写标准 `system_*` 资源。

## 验证

构建版本：`Amalor_v1.2.3_36-release.apk`。

```powershell
.\gradlew.bat -I "tests\qs-checks.init.gradle" :app:checkQsAppearance :app:assembleRelease "-PVERSION_NAME=1.2.3" --offline --console=plain --no-daemon
```

- 11 组 JVM 检查通过。壁纸检查覆盖 6 个种子、4 个风格、2 个模式、3 档 contrast，共 720 个分组案例；分别检查 Normal/on-role、容器、状态层、共享前景对比度，以及 identity/写入范围恢复。
- Release/R8、lintVital、APK v2 签名验证通过。
- 真机已安装并重新应用格调；从设置进程的 **Material value** 读取实际厂商覆盖值，对照标准角色。当前种子 `FF739B61`、Tonal Spot、contrast 0.0，深浅色各 11 个分组/容器/共享前景值全部吻合。

| 组的普通色 | 浅色实测 | 深色实测 |
|---|---|---|
| Blue / primary | `#FF476739` | `#FFB2CFA2` |
| Red / secondary | `#FF55634D` | `#FFBCCBB0` |
| Orange / tertiary | `#FF64612A` | `#FFFFFADC` |
| Yellow / onSurface | `#FF2E342A` | `#FFE2E8D9` |
| Green / onSurfaceVariant | `#FF5B6156` | `#FFA7ADA0` |

资源验证使用 `adb shell cmd resources dump com.android.settings`，解析 `key = <id> Material value = <argb>`。仅检查 `cmd overlay lookup` 无法证明厂商 XML 覆盖是否生效；此次同时核对了实际 Material 表与标准资源。其他风格的分组和对比度由矩阵回归覆盖，真机逐值对照覆盖上述格调方案。

验证后恢复设备原来的浅色模式与格调选项，日志设置未改变。

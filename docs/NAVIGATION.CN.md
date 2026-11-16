# 1.2.14：快速连点导航去重

现有 `AmalorRoute` 为单例设置页。miuix-nav 的 `NavController.push()` 直接追加条目，默认 contentKey 又等于路由，因此连续回调会让 WeType 等相同 key 重复进入返回栈。

所有页面入口统一调用 `NavigationActions.navigateTo`：

- 点击时读取当前真实返回栈；目标已在栈顶时直接结束。
- 目标已在栈内时，通过原生 `popUntil` 返回已有条目。
- 目标不存在时才调用 `push`。返回后可以立即再次进入。

`NavigationActionsCheck` 使用实际 NavController 和 SnapshotStateList，覆盖 100 次同页跳转、交替目标、复用已有页面、根栈保护以及返回后重进。测试运行时加入 Android SDK bootClasspath，以满足 Android SnapshotStateList 的 Parcelable 类型依赖。

真机脚本 `tests/NavigationRapidTapDeviceCheck.py` 在微信输入法入口执行 5 轮、系统界面入口执行 3 轮双击/返回/再进入；每轮检查只需返回一次、页面正确、进程没有重启。1.2.14 / code 41 已通过上述真机检查、13 组 JVM 回归、Release/R8、lintVital 和 APK 签名验证，并已覆盖安装。

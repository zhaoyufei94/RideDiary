# 测试与 OPPO 验收

测试日期：2026-10-22。本机 macOS arm64，Temurin JDK 17.0.20.1 / Gradle 8.11.1 / Kotlin 2.1.20 / SDK 35。以下区分实际执行结果与尚未执行的设备用例；可机读记录见 [verification.json](verification.json)。

## 验证分层

| 层次 | 范围 | 当前证据 |
|---|---|---|
| 资源 / 静态检查 | 中英文资源完整、源码引用、Git 空白错误 | 已通过：125 条双语资源，所有 Kotlin 引用有效 |
| JVM 单元测试 | 分段、定位质量、路线投影、历史与月度统计 | 已通过：28 个测试，0 失败 / 0 错误 / 0 跳过（分析 11、路线 9、统计 8） |
| Android 构建 / Lint | Compose、Room/KSP、Manifest、资源、APK | 已通过：应用 APK、测试 APK、Lint（0 错误；7 条 GradleDependency 版本更新提示） |
| Android 设备测试 | Room 重开、改归类、中断恢复、四页回放、真实服务生命周期 | OPPO PKJ110 上 4 项通过：原有 3 项及新增服务测试；未清除日常行程数据 |
| OPPO 实路 | 锁屏、主辅路、到达和耗电 | 未执行，必须真机验证 |

应用包：`app/build/outputs/apk/debug/app-debug.apk`（11,314,623 字节），测试包：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。`apksigner verify --verbose` 通过 v2 签名验证；AAPT 确认包名 `com.ridediary`、版本 0.1.0、最低 API 26 / target 35，权限清单不含联网或全天后台位置。校验和记录在 JSON 中。

Lint 的 7 条提示仅为依赖存在更新版本。初版保留已实际构建验证的固定组合，没有通过屏蔽错误或建立 Lint baseline 来跳过检查。

## OPPO 设备执行记录

2026-10-22，OPPO PKJ110，手机报告 Android 17 / API 37、Oplus ROM V17.0.0。应用已安装并可启动。原有数据库两项与四页/回放一项测试通过，用时 13.647 秒；新增 `ServiceLifecycleTest` 通过，用时 7.151 秒。原始输出分别见 [3 项设备测试](validation/oppo-instrumentation.txt)与[真实服务测试](validation/oppo-service-test.txt)。本次通过 ADB 直接执行，未生成 Gradle connected HTML 报告。

首次 UI 测试因旧 Espresso 反射调用已不存在的 `InputManager.getInstance` 失败。依据 [AndroidX 官方修复记录](https://developer.android.com/jetpack/androidx/releases/test#espresso-3.7.0)，将测试依赖固定为 Espresso 3.7.0、Runner 1.7.0、JUnit 扩展 1.3.0 后通过；生产运行依赖未变更。流式安装失败后，改用 `adb install --no-streaming -r` 成功。USB 数据连接多次中断，恢复并授权后完成测试。

服务测试通过标准仪器测试权限接口，仅给途记授予定位/通知权限；采样写入独立临时数据库，结束后停止服务、删除测试库，并断言日常行程库未变化。验证了重复开始只产生一趟、Activity 重建不重开记录、短时后台仍增量保存、通知结束动作完成记录并移除通知。没有修改电池策略，也没有验证 GPS 点的道路精度或 30–60 分钟锁屏存活。

## 自动化命令

本机最终执行命令：

```sh
./scripts/gradle-local.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
```

设备兼容修复与新增服务测试后，另外实际执行了 `./scripts/gradle-local.sh :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain`，构建成功。生产代码与 JVM 算法测试未改动，因此没有为测试依赖更新重复运行算法测试。

通用环境复现与待设备执行命令：

```sh
python3 scripts/check-resources.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
adb devices -l
# 本次 OPPO 使用直接安装/执行，避免 Gradle connected 的测试后卸载：
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r com.ridediary.test/androidx.test.runner.AndroidJUnitRunner
# 专用测试设备上也可运行下列命令；可能在结束后卸载应用：
./gradlew :app:connectedDebugAndroidTest
```

JVM 报告：`app/build/reports/tests/testDebugUnitTest/index.html`。Lint 报告：`app/build/reports/lint-results-debug.html`。本次设备证据为上文两份 ADB 原始输出；只有使用 Gradle connected 流程时才生成 `app/build/reports/androidTests/connected/debug/index.html`。

算法测试包含：漂移静止确认回溯、初始缓行不计停车、速度缺失保持未知/可推算位移、并发 GNSS/网络来源选择与回退、长缺口不连接、跳点隔离、精度差/过期点、无定位持续计时、静止区间合并、确定性回放、出发后识别与起步降级、平行路线歧义、偏离、掉头、自交局部投影、终点短停/真实停留/无新定位、历史不足、当前/未来/异常/中断/其他路线方向排除、同日往返和本地日期、修改分类后汇总变化、跨午夜平均、零等待/单样本、独立分项中位数示例、历史剩余时间 ETA。

`PersistenceTest` 在真实 Android Room 数据库中保存原始点/分段/路线，关闭再重开验证保留；修改归类与排除、修正边界后验证有效集合和耗时变化，原始点数不变；仓库重复开始返回同 ID，中断恢复添加未知缺口。上述用例已在此手机通过。真实服务重复 Intent、Activity 重建、短时后台和通知动作也已由 `ServiceLifecycleTest` 验证；系统杀进程、长时间锁屏和通知权限拒绝等仍待实测。

`UiSmokeTest` 验证初始空状态入口、独立演示、四页导航、统计内容和回放到达小结。图表筛选、英文、字体、TalkBack 和权限流程需补充人工设备检查。

额外尝试 ADB 截图时未取得 App 可用画面，界面树仅有系统界面，故未将像素级视觉验收标为通过；本次界面结果依据 Compose 测试的可见性和交互断言。

## OPPO 补充与实路验收

记录手机型号、Android/ColorOS 版本、APK 版本、算法版本 1.0、时间、天气/卫星可见性、系统电池设置。停车后操作，不让测试妨碍骑行。

| 场景 | 操作与预期 | 需要记录 |
|---|---|---|
| 权限 | 首启可浏览；拒绝/仅近似不启动；精确位置可开始；通知拒绝时有说明；长期拒绝可去设置 | 权限弹窗、前台服务是否成功、界面提示 |
| 生命周期（部分通过） | 重复开始、Activity 重建、短时后台和通知停止已自动验证；手动旋转、长会话返回仍待测 | 行程 ID/数量、通知状态、原始点连续性 |
| 锁屏 | 正常电池设置下屏幕关闭 30–60 分钟、切后台，回到 App 后检查轨迹和未知占比 | 缺口时长、定位频率、是否被系统终止 |
| 定位恢复 | 出入地库/隧道，途中关闭再打开定位；缺口为未知，恢复不跨缺口补距离 | 示意图、未知区间、总耗时和里程 |
| 权限撤回 / 进程终止 | 在系统设置撤回位置或强停、终止进程、重启手机，重新进入 | 不自动重录；遗留记录明确中断，已存原始点仍在 |
| 主辅路 / 多走法 | 同起点多路线，平行道路、互通自交、掉头/绕路 | 是否保持匹配中或提示不确定，是否跳到后段 |
| 到达 | 终点外短停、在终点范围短暂停留后离开、真正停留≥30秒 | 不过早结束；确认时间、手动结束兜底 |
| 静止准确性 | 上下班各数次，人工记总耗时与各停留，不用瞬时速度作目标 | 移动/静止/未知误差，缓行误判和起始等待 |
| ETA | 同路线至少三次有效历史后，在停车时查看多个进度位置 | 实际样本数、区间覆盖、偏离时降级 |
| 修改与统计 | 改路线/方向、排除、修正边界、跨午夜、同日往返，重启再看月份与图表 | 原始点保留、图表和表格一起变化、一天两次 |
| 电池 | 记录 30/60 分钟前后电量并与相近日常使用比较 | 耗电、发热、ColorOS 提示；不默认关闭全部省电 |
| 适配与隐私 | 中文/英文、最大字体、横竖屏、手势/三键导航、TalkBack、OEM 迁移 | 裁切/触摸/读屏；锁屏通知可见性、备份排除是否遵守 |

## 结果模板

| 构建/设备 | 场景 | 预期 | 实际 | 证据 | 结论 |
|---|---|---|---|---|---|
| 型号 / ColorOS / Android / APK | 测试场景与路线版本 | 预期结果 | 时间/未知比例/耗电等 | 脱敏截图或手记；不向公开日志输出坐标 | 通过 / 待修 / 无法判定 |

实路准确性是后续迭代指标，不承诺精确到秒。未测内容保持“待测”，模拟轨迹通过不代表 OPPO 后台可靠。

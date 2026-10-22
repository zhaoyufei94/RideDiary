# 测试与 OPPO 验收

测试日期：2026-10-22。本机 macOS arm64，Temurin JDK 17.0.20.1 / Gradle 8.11.1 / Kotlin 2.1.20 / SDK 35。以下区分实际执行结果与尚未执行的设备用例；可机读记录见 [verification.json](verification.json)。

## 验证分层

| 层次 | 范围 | 当前证据 |
|---|---|---|
| 资源 / 静态检查 | 中英文资源完整、源码引用、Git 空白错误 | 已通过：125 条双语资源，所有 Kotlin 引用有效 |
| JVM 单元测试 | 分段、定位质量、路线投影、历史与月度统计 | 已通过：28 个测试，0 失败 / 0 错误 / 0 跳过（分析 11、路线 9、统计 8） |
| Android 构建 / Lint | Compose、Room/KSP、Manifest、资源、APK | 已通过：应用 APK、测试 APK、Lint（0 错误；9 条 GradleDependency 版本更新提示） |
| Android 设备测试 | Room 重开、改归类、重复开始、中断恢复、四页回放 | 3 个仪器测试编译成功，未运行；交付前 ADB 再检查仍无设备/运行中的模拟器 |
| OPPO 实路 | 锁屏、主辅路、到达和耗电 | 未执行，必须真机验证 |

应用包：`app/build/outputs/apk/debug/app-debug.apk`（11,314,623 字节），测试包：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。`apksigner verify --verbose` 通过 v2 签名验证；AAPT 确认包名 `com.ridediary`、版本 0.1.0、最低 API 26 / target 35，权限清单不含联网或全天后台位置。校验和记录在 JSON 中。

Lint 的 9 条提示仅为依赖存在更新版本。初版保留已实际构建验证的固定组合，没有通过屏蔽错误或建立 Lint baseline 来跳过检查。

## 自动化命令

本机最终执行命令：

```sh
./scripts/gradle-local.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
```

通用环境复现与待设备执行命令：

```sh
python3 scripts/check-resources.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
./gradlew :app:assembleDebugAndroidTest
adb devices -l
./gradlew :app:connectedDebugAndroidTest
```

JVM 报告：`app/build/reports/tests/testDebugUnitTest/index.html`。Lint 报告：`app/build/reports/lint-results-debug.html`。仪器测试报告：`app/build/reports/androidTests/connected/debug/index.html`。报告只在相应命令实际运行后生成；未连接设备不能把仪器测试标为通过。

算法测试包含：漂移静止确认回溯、初始缓行不计停车、速度缺失保持未知/可推算位移、并发 GNSS/网络来源选择与回退、长缺口不连接、跳点隔离、精度差/过期点、无定位持续计时、静止区间合并、确定性回放、出发后识别与起步降级、平行路线歧义、偏离、掉头、自交局部投影、终点短停/真实停留/无新定位、历史不足、当前/未来/异常/中断/其他路线方向排除、同日往返和本地日期、修改分类后汇总变化、跨午夜平均、零等待/单样本、独立分项中位数示例、历史剩余时间 ETA。

`PersistenceTest` 在真实 Android Room 数据库中保存原始点/分段/路线，关闭再重开验证保留；修改归类与排除、修正边界后验证有效集合和耗时变化，原始点数不变；仓库重复开始返回同 ID，中断恢复添加未知缺口。服务本身的重复 Intent、系统杀进程和通知动作仍须设备测试，仓库单例测试不能代替服务生命周期测试。

`UiSmokeTest` 验证初始空状态入口、独立演示、四页导航、统计内容和回放到达小结。图表筛选、英文、字体、TalkBack 和权限流程需补充人工设备检查。

## OPPO 真机验收表（全项待测）

记录手机型号、Android/ColorOS 版本、APK 版本、算法版本 1.0、时间、天气/卫星可见性、系统电池设置。停车后操作，不让测试妨碍骑行。

| 场景 | 操作与预期 | 需要记录 |
|---|---|---|
| 权限 | 首启可浏览；拒绝/仅近似不启动；精确位置可开始；通知拒绝时有说明；长期拒绝可去设置 | 权限弹窗、前台服务是否成功、界面提示 |
| 生命周期 | 连点开始、旋转、返回/重进不产生重复行程；通知停止只结束当前趟 | 行程 ID/数量、通知状态、原始点连续性 |
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

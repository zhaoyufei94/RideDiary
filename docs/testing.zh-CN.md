# 测试与 Android 设备验收

本清单适用于不同品牌的 Android 设备。下文的具体型号仅标识历史测试环境，不是产品限制。产品设计与交互规范见[产品设计](../PRODUCT_DESIGN.zh-CN.md)。

## 0.2.2 记录页去重与定位指示（2026-10-23）

移除主卡片中重复的匹配、待区分和未匹配说明；定位状态与“记录中”同行，使用绿/红圆点及原有次要文字风格。三个定向真机界面用例通过（5.931秒），涵盖无重复提示、候选列表、标题与定位同行、正常/弱信号/过期定位及恢复指示；检查了合成截图。编译通过，Lint 0错误/15警告（移除展示后多出一项未使用文字提示），222条双语资源通过。已覆盖更新手机，数据库全部行与更新前一致。算法未改，算法回归证据沿用前次构建。

证据：[构建](validation/recording-header-build-0.2.2.txt)、[界面](validation/recording-header-ui-0.2.2.txt)、[数据保留](validation/data-retention-recording-header-0.2.2.json)。

## 0.2.2 候选展示精简（2026-10-23）

候选信息移至主记录卡片下方，最多两条小字路线及重合度；去掉独立卡片、进度条、状态说明和更新时间。无候选或定位不可用时隐藏。两个定向真机界面用例通过（3.730秒），检查主卡片在前、默认字号区域不超过80dp及候选/匹配显示。编译与Lint通过（0错误、14警告），223条双语资源通过；覆盖更新并检查合成截图。数据库全部行保持一致。此次仅调整展示，算法测试沿用前次证据。

证据：[构建](validation/compact-candidates-build-0.2.2.txt)、[界面](validation/compact-candidates-ui-0.2.2.txt)、[数据保留](validation/data-retention-compact-candidates-0.2.2.json)。

## 0.2.2 路线重捕获与实时候选（2026-10-23）

- 41 项 JVM 测试通过，新增起步回折、短暂偏离后重捕获、长定位缺口不跳路段、重合度与进度区分、平行道路歧义五项。原有掉头、交叉路段、到达判定和投影等价性测试继续通过。
- 18 项定向真机用例通过：13 项候选计算降频、原始点落库、结束强制复核、保存/丢弃、路线与持久化回归；5 项候选名称/百分比/待区分、重捕获后已匹配、定位恢复以及到达保存/取消界面。13 项运行的总耗时为168.537秒，包含锁屏等待，不是算法耗时；解锁后通过。5项UI运行10.154秒。
- 对此前已备份的真实1766点轨迹做只读 JVM 重放，最终候选已确认，重合度98.64%，进度99%（算法有意保留到达确认空间）。没有把这个重放结果写回历史记录；这不替代真实路测。
- 231条双语资源通过，Lint 0错误、14条警告，APK使用同一开发证书，版本保持0.2.2/code12，数据库仍为v4。已覆盖更新手机、检查合成数据候选卡片截图并冷启动成功。
- 更新和测试前后每一行均相同：3条行程、4978个点、75个分段、2条路线。此前按用户要求单独修正的结束边界3537900ms保持不变。
- 完整通知/重建UI、真实桥下/隧道、更多相近道路、长期功耗等继续待测，不将定向用例视为全功能验收。

证据：[构建](validation/route-candidates-delivery-build-0.2.2.txt)、[核心真机](validation/route-candidates-core-0.2.2.txt)、[界面真机](validation/route-candidates-ui-0.2.2.txt)、[数据保留](validation/data-retention-route-candidates-0.2.2.json)、[当前记录](verification.json)。

## 0.2.2 停止延迟与定位状态修复（2026-10-23）

- 应用、测试包、36 项 JVM 测试全部通过，221 条双语资源通过；Lint 0 错误、21 条警告。修正了远端已有的四处 Compose Configuration 读取错误。
- 15 项定向真机用例通过：12 项停止/保存、长轨迹、持久化及路线回归（8.770 秒）；1 项定位恢复但数据库快照未变的界面用例（1.972 秒）；2 项到达保存、取消/丢弃界面用例（5.004 秒）。均使用临时数据库。
- 人为阻塞数据库 1.6 秒并重复发出停止命令，结束 UTC 仍位于首次命令发出区间，没有延后；1800 点/一小时合成轨迹全部保留。索引投影的 1200 组随机查询与原始穷举实现完全一致。
- 一个附加四用例 UI 运行在原有通知重建用例等待，已终止；其余三项相关 UI 用例单独重跑通过，不能据此宣称完整通知/重建界面回归已通过。
- 已沿用同一签名覆盖安装 0.2.2，Room 版本仍为 4。更新和测试前后逐行比较：3 条行程、4978 个点、75 个分段、2 条路线及所有字段完全相同，未修正旧行程时长。SQLite quick_check 均为 ok，冷启动成功。
- 真实桥下/隧道恢复、真实一小时骑行、功耗及原有天气/计划待测项继续保留。

证据：[构建](validation/local-build-0.2.2.txt)、[停止与数据回归](validation/stop-save-device-0.2.2.txt)、[定位恢复界面](validation/location-ui-0.2.2.txt)、[到达界面](validation/arrival-ui-0.2.2.txt)、[旧数据未改动](validation/data-retention-0.2.2.json)、[verification.json](verification.json)。

## 0.2.1 启动与数据库升级修复（2026-10-23）

- 已从手机读取本应用崩溃堆栈：Room v3 结构 identity 不匹配。只读确认旧库缺少 planMode；先在本机忽略目录创建私有备份，未公开数据库、路线名称或坐标。
- 0.2.1 / versionCode 11、Room v4；应用及测试 APK 编译通过，使用共享 debug.keystore；导出 v4 schema，Git 空白检查通过。未运行 JVM、Lint 或其他功能回归。
- OPPO PKJ110 上四项隔离迁移测试通过（0.209 秒）：v1→4、v2→4、缺字段 v3→4、完整 v3→4。测试仅操作合成临时数据库，没有运行亮屏或真实记录测试。
- 已覆盖安装主应用及测试包；实际旧 v3 升至 v4，冷启动成功，复开后进程持续运行且未出现当前进程 FATAL EXCEPTION。
- 修复前后均为 2 条已完成行程、2 条路线、3212 个点、48 个分段；逐行摘要确认行程/点/分段及全部原路线字段未变，SQLite integrity_check 为 ok。早期星期计划保留为 CUSTOM；未卸载或清除主应用数据。

证据：[原崩溃](validation/startup-crash-0.2.0.txt)、[构建](validation/local-build-0.2.1.txt)、[四项迁移测试](validation/migration-0.2.1.txt)、[真实数据保留](validation/data-retention-0.2.1.json)、[当前记录](verification.json)。0.2.0 其他待测项仍保留，不能将此定向修复当成全功能验收。

## 0.2.0 出行计划与天气整理（2026-10-23）

- 0.2.0 / versionCode 10，Room v3；仅执行应用 assembleDebug，使用共享 debug.keystore，Git 空白检查通过。
- 未运行 JVM、仪器测试、Lint、测试包编译或设备安装/交互；用户反馈 0.1.8 天气可用，不代表本版新流程已验收。
- 已在资源准备阶段核对 2026 国务院安排并打包 JSON/许可；2027 未查到正式安排，源文件仍为空，不打包。运行时日历只有 assets 读取路径，无请求实现。
- 待验：2→3 与 1→3 迁移、原行程/轨迹保留、计划持久化及同组同方向同步；补班周末、平日假期、普通工作日、缺少年份和自定义星期；计划过去/临界时刻、跨午夜/时区和重建后的返回。
- 待验：记录页顶部下拉刷新、短页面及其他页面滚动、不同端点去重和渐进呈现、关闭天气、空计划、服务切换、来源仅页尾一处；当前天气未来一小时概率与唯一更新时间、路线体感缺值及区间覆盖；失败时只 Toast、旧数据时间不变。
- 待验：小启动图标穿过权限后绑定方向、保存/删除/取消、偏离排除与恢复；大字体、TalkBack、时间输入和输入法布局。亮屏不重复测试。

证据：[本版构建](validation/local-build-0.2.0.txt)、[当前记录](verification.json)、[本版快照](validation/verification-0.2.0.json)、[年度数据](work-calendar.zh-CN.md)。

## 0.1.8 天气与推荐路线（2026-10-23）

- 版本 0.1.8 / versionCode 9，Room v2，无数据库迁移；新增 Open-Meteo、可配置和风天气、当前/今日天气、出发时段预览、推荐及绑定路线启动。
- 仅执行 `./scripts/gradle-local.sh :app:assembleDebug --no-daemon --console=plain`，最终构建通过并沿用共享 debug.keystore；Git 空白检查通过。
- 未执行 JVM、仪器测试或 Lint，未连接、安装或测试设备，未调用线上天气接口或使用真实天气凭据；历史结果不作为本版通过证据。亮屏不重复测试。
- 待验证：两家服务联网和实际账号权限/额度；Gzip、鉴权失败、429、超时及离线缓存；关闭天气后无新查询；定位拒绝/近似授权只影响天气且不自动开始记录。
- 待验证：15:00 下班推荐、多路线频率、工作日/周末、跨午夜和跨时区、样本不足；起终点预报覆盖与缓存标注、QWeather 分钟精度时间和本地日期、来源链接。
- 待验证：选择路线→首次权限→开始→取消/保存/删除→重建/中断，确保绑定方向不变；偏离/未完成自动排除且原始点保留，过期路线选择不会留下前台服务。
- 待验证：Key 加密及重新打开后的读取、服务/语言切换、设置关闭/重建时保存完成、窄屏/大字体/TalkBack、记录结束按钮可达性；不输出 Key 或精确坐标。

证据：[构建日志](validation/local-build-0.1.8.txt)、[当前记录](verification.json)、[本版快照](validation/verification-0.1.8.json)。

## 0.1.7 文案、路线导航与设置（2026-10-23）

- 0.1.7 / versionCode 8，数据库 v2；文案、路线关联行程、解除关联和设置入口代码已完成。
- 仅执行 `./scripts/gradle-local.sh :app:assembleDebug --no-daemon --console=plain`，最终文案修正后编译成功，使用既有共用 Debug 签名。
- 按用户要求，未运行 JVM、仪器测试或 Lint，未连接设备、未安装应用；下方旧版测试结果不代表 0.1.7 已通过。
- 待复验：路线到行程详情及返回、解除关联保留数据、记录中保护、过期菜单不影响新关联、设置版本号、语言切换后的页面保持及大字体布局。

证据：[构建日志](validation/local-build-0.1.7.txt)、[当前记录](verification.json)、[0.1.7 快照](validation/verification-0.1.7.json)；旧版结果见 [0.1.6 快照](validation/verification-0.1.6.json)。

## 0.1.6 路线归组、统计与结束确认（2026-10-23）

- 版本 0.1.6 / versionCode 7，Room v2。显式 v1→v2 迁移为同名旧参考建立分组，保留原路线 ID、关联行程、原始点与分段；上班/下班分别保存实际轨迹。
- 32 项 JVM 测试通过（分析 11、路线投影 9、路线身份/歧义 3、统计 9）；164 条双语资源、6 个品牌资源检查通过。应用/测试 APK 编译和 Lint 通过：0 错误，7 条既有依赖更新提示。两个 APK 均用共用 debug.keystore，实际签名与固定证书一致。
- 20 项设备用例跨批次通过：Room/迁移 9 项、UI 10 项、真实通知服务 1 项。通知已验证跳转且不立即停止，显式保存/删除通过；额外的“通知取消后重建页面再确认”用例未取得完整结果，仍待复验，未计为通过。最终整批回归受到会话中断影响，不能声称 21 项全套通过。
- 界面新增覆盖：路线先选、方向标签与图表同范围；其他路线空状态；修正列表首项新建；补充同名下班方向；取消后继续；保存后自动结果可修正；通知 Intent 和重建后的结束确认（该项待复验）。仓库新增覆盖：到达只提示、显式保存、删除记录级联、反向识别保存实际轨迹、同组改名/删除保留历史及真实旧库迁移。
- 已用 `adb install --no-streaming -r` 覆盖安装最终应用，保留现有数据。UI 和服务测试均使用临时库，结束后检查日常行程不变；不采集公开坐标日志。通知路由、界面确认按钮、服务保存/删除分别验证。
- **亮屏功能已由用户验证，本轮没有运行 ServiceLifecycleTest 或重复亮屏验收。** 历史记录中的亮屏待测状态保留为当时快照，以本条说明为当前状态。
- 仍待实路验证：不同道路/平行道路、只有单向参考时的首次返程识别、终点误判与确认等待期间轨迹、长时锁屏连续性和耗电。合成轨迹测试不等同于道路精度验收。

最新布局调整（路线双向总览前移、详情时间与停车卡片合并）仅执行 assembleDebug，编译通过；按用户要求未重跑测试，上述测试结果来自该布局调整之前。

复现本轮设备范围（先结束正在进行的真实记录）：

```sh
./scripts/gradle-local.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-daemon --console=plain
.tooling/android-sdk/platform-tools/adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
.tooling/android-sdk/platform-tools/adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
.tooling/android-sdk/platform-tools/adb shell am instrument -w -r -e class com.ridediary.PersistenceTest,com.ridediary.RouteWorkflowTest,com.ridediary.UiSmokeTest,com.ridediary.RecordingReviewTest com.ridediary.test/androidx.test.runner.AndroidJUnitRunner
```

证据：[当前验证记录](verification.json)、[构建](validation/local-build-0.1.6.txt)、[最终构建](validation/final-build-0.1.6.txt)、[设备结果](validation/android-routes-0.1.6.txt)。USB 中断和测试框架等待的未完成批次单独归档，不计为通过。以下旧版记录只描述各自版本。

## 0.1.5 品牌更新与签名集成（2026-10-23）

- 本地品牌与图标提交已 rebase 到 origin/main 的 `aa08a14`（0.1.4）之上，保留远程行程/路线管理、统计与记录亮屏行为；新版本为 0.1.5 / versionCode 6。
- 根目录提供的 `debug.keystore` 已校验并导入 `.signing/debug.keystore`。应用与测试 APK 实际证书均与固定指纹一致，签名验证通过；私钥未提交。
- 28 项 JVM 测试通过，157 条双语资源及 6 个品牌资源检查通过，应用/测试 APK 编译和 Lint 通过；Lint 为 0 错误、7 条既有依赖更新提示。
- 本次未安装手机、未执行 13 项设备测试。最新的亮屏服务测试源码已成功编译，亮屏真机验收仍为待测，未将历史失败标为通过。

证据：[0.1.5 验证记录](validation/verification-0.1.5.json)、[当前记录](verification.json)；原始 0.1.4 记录已归档为 [verification-0.1.4.json](validation/verification-0.1.4.json)。

## 0.1.4 记录期间亮屏（2026-10-22）

- 应用与测试 APK 编译通过；156 条双语资源通过；Lint 0 错误、7 条既有依赖更新提示。共用签名校验通过，已覆盖安装手机，未卸载主应用。
- 初次 13 项真机回归中，7 项 UI 和 5 项持久化用例通过；服务测试重复授予已有权限被设备拒绝，已改为仅补授缺少的权限。
- 服务重测的亮屏断言超时，前后台恢复的测试已改为恢复同一个 ActivityScenario 并补充分阶段诊断。后续 UI 用例期间 ADB 中断；更新后的测试源码尚未重新编译及验证，亮屏功能不能视为真机验收通过。
- 用户明确允许继续编译与验证后，自动审批仍连续超时；沙箱内 Gradle/ADB 因本地端口限制也无法运行。待工具恢复后，需重新编译并完整执行 13 项测试。算法未修改，本次未重跑 JVM 测试。
- 验收还应确认：记录中跨页、重建及前台返回保持亮屏；结束、到达及服务销毁释放；离开应用后不阻止系统休眠；手动电源键锁屏后记录继续。

证据：[初次构建](validation/local-build-0.1.4.txt)、[权限测试修正后构建](validation/local-build-0.1.4-final.txt)、[初次设备输出](validation/oppo-screen-awake-preliminary-0.1.4.txt)、[未完成重测](validation/oppo-screen-awake-incomplete-0.1.4.txt)、[verification.json](verification.json)。

## 0.1.3 统计与详情美化验证（2026-10-22）

- 最终应用 APK 和测试 APK 编译通过，155 条双语资源与 Git 空白检查通过；Lint 0 错误、7 条依赖更新提示。两个 APK 均沿用共用开发证书。
- 已覆盖更新 OPPO PKJ110 至 0.1.3 / versionCode 4，未卸载主应用；正常应用启动成功。
- 12 项真机测试全部通过，用时 21.272 秒：7 项 UI 用例覆盖长按操作面板、编辑删除、系统返回、月份/路线/方向筛选、图表选择和英文 1.65 倍字体；5 项持久化用例覆盖数据保留、级联、关联及记录中保护。
- 已检查合成数据下的详情总览、时间分布、停车指标、轨迹、历史对比，以及中英文操作面板。截图仅复制本应用窗口。测试使用临时数据库，每项 UI 用例结束后断言原有日常行程未改变。
- 前期对话框语言继承问题已修正；测试窗口保持亮屏，避免自动锁屏干扰。算法未变，本次沿用 0.1.1 的 28 项 JVM 测试结果。

证据：[应用构建](validation/local-build-0.1.3.txt)、[最终测试包构建](validation/test-build-0.1.3.txt)、[真机输出](validation/oppo-refined-ui-0.1.3.txt)、[verification.json](verification.json)。

## 0.1.2 长按菜单验证（2026-10-22）

- 应用和测试 APK 编译成功，133 条双语资源及 Git 空白检查通过；Lint 0 错误、7 条依赖更新提示。
- 沿用共用开发签名，已通过 `adb install --no-streaming -r` 覆盖更新 OPPO PKJ110 至 0.1.2，未卸载主应用。
- 10 项真机仪器测试全部通过，用时 11.282 秒：5 项 UI 用例验证默认不显示管理按钮、长按菜单、关闭菜单、点击详情、删除确认、路线编辑和重建后返回；5 项 Room 用例验证级联删除、关联更新、重开持久化和记录中保护。
- UI 用例使用内存数据库，并在每项结束后断言日常行程未改变。算法未修改，本次沿用 0.1.1 的 28 项 JVM 测试结果。

本次结果见 [verification.json](verification.json)、[真机原始输出](validation/oppo-long-press-0.1.2.txt)和[编译记录](validation/local-build-0.1.2.txt)。

## 0.1.1 本次修改验证（2026-10-22）

此电脑已配置项目专用 Temurin JDK 17.0.20.1、Gradle 8.11.1 和 Android SDK 35，位于被 Git 忽略的 `.tooling/`。`local.properties` 指向本地 SDK；`./scripts/gradle-local.sh` 自动选择这些工具。ADB 为 `.tooling/android-sdk/platform-tools/adb`。

- 128 条双语资源引用及 `git diff --check` 通过。
- 应用 APK、Android 测试 APK 编译通过；28 个 JVM 算法测试全部通过。
- Lint：0 错误，7 条现有依赖版本更新提示。
- 版本为 0.1.1 / versionCode 2；数据库结构未改变。应用包不含 Replay 合成轨迹类或演示运行状态。
- 新增 3 个持久化用例：行程删除级联与路线保留、路线编辑/删除及重开后的关联一致性、记录中保护与无效编辑拒绝。UI 测试改为 4 个独立临时数据库用例：四页及重建后的系统返回、列表删除/取消、详情删除后回列表、路线编辑/删除/取消。
- **本次 Android 仪器测试尚未运行，等待用户连接手机安装后执行。** 下方 OPPO 设备结果来自 0.1.0，不能作为新增管理功能已在真机通过的证据。

构建与校验信息见 [verification.json](verification.json)，旧版记录见 [verification-0.1.0.json](validation/verification-0.1.0.json)。本次编译日志见 [local-build-0.1.1.txt](validation/local-build-0.1.1.txt)。连接手机后的命令：

```sh
.tooling/android-sdk/platform-tools/adb devices -l
.tooling/android-sdk/platform-tools/adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
# 使用临时测试数据库，不清除日常行程库；请先结束正在进行的记录。
.tooling/android-sdk/platform-tools/adb install --no-streaming -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
.tooling/android-sdk/platform-tools/adb shell am instrument -w -r com.ridediary.test/androidx.test.runner.AndroidJUnitRunner
```

## 双 Mac 开发签名验证（2026-10-22）

固定 `.signing/debug.keystore`，证书沿用手机当前 0.1.1 的签名。应用与测试 APK 重新编译、签名报告及 `apksigner verify --print-certs` 校验通过，两个 APK 均匹配公开证书指纹。独立临时工程验证：缺少共用密钥时拒绝构建；有效但错误的密钥也被拒绝。导入脚本对错误证书拒绝导入并保留原密钥，重复导入正确文件成功，密钥文件权限为 600 且被 Git 忽略。本次未操作另一台 Mac；其一次性传输和导入仍需用户完成，见[双 Mac 签名配置](signing.zh-CN.md)。这次签名配置不需要重新卸载手机应用。

## 0.1.0 历史验证记录

测试日期：2026-10-22。本机 macOS arm64，Temurin JDK 17.0.20.1 / Gradle 8.11.1 / Kotlin 2.1.20 / SDK 35。以下区分实际执行结果与尚未执行的设备用例；可机读记录见 [verification.json](verification.json)。

## 验证分层

下表与 `verification.json` 的 APK 大小、校验和和设备测试为初版执行快照。后续品牌更新的构建验证见下一节，不代表重新执行了道路或设备测试。

| 层次 | 范围 | 当前证据 |
|---|---|---|
| 资源 / 静态检查 | 中英文资源完整、源码引用、Git 空白错误 | 已通过：125 条双语资源，所有 Kotlin 引用有效 |
| JVM 单元测试 | 分段、定位质量、路线投影、历史与月度统计 | 已通过：28 个测试，0 失败 / 0 错误 / 0 跳过（分析 11、路线 9、统计 8） |
| Android 构建 / Lint | Compose、Room/KSP、Manifest、资源、APK | 已通过：应用 APK、测试 APK、Lint（0 错误；7 条 GradleDependency 版本更新提示） |
| Android 设备测试 | Room 重开、改归类、中断恢复、四页回放、真实服务生命周期 | OPPO PKJ110 上 4 项通过：原有 3 项及新增服务测试；未清除日常行程数据 |
| 真实道路 | 锁屏、主辅路、到达和耗电 | 未执行，必须真机验证 |

应用包：`app/build/outputs/apk/debug/app-debug.apk`（11,314,623 字节），测试包：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`。`apksigner verify --verbose` 通过 v2 签名验证；AAPT 确认包名 `com.ridediary`、版本 0.1.0、最低 API 26 / target 35，权限清单不含联网或全天后台位置。校验和记录在 JSON 中。

Lint 的 7 条提示仅为依赖存在更新版本。初版保留已实际构建验证的固定组合，没有通过屏蔽错误或建立 Lint baseline 来跳过检查。

## 品牌更新验证（2026-10-22）

品牌命名与方格纸 Logo 更新后，`python3 scripts/check-resources.py` 通过：126 条双语资源，源码引用有效；`python3 scripts/generate-brand-assets.py --check` 确认 6 个 Android 品牌资源与 SVG 一致。`./scripts/gradle-local.sh :app:assembleDebug :app:lintDebug --no-daemon --console=plain` 构建成功。已检查本地链接、XML、圆形/圆角/主题预览与 48/32/24 像素效果；自适应前景的两端完整位于安全区。预览见[图标检查图](brand/icon-preview.png)。

本次未检测到 ADB 设备，未更新手机安装，也未重跑设备或实路测试。本地原型因浏览器安全策略不能自动读取 `file://`，完成了静态引用检查，未声称浏览器视觉验收通过。

## 历史设备执行记录

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
# 直接安装/执行可避免 Gradle connected 的测试后卸载：
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

## 通用设备与实路验收

记录手机型号、Android/厂商系统版本、APK 版本、算法版本 1.0、时间、天气/卫星可见性、系统电池设置。停车后操作，不让测试妨碍骑行。

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
| 电池 | 记录 30/60 分钟前后电量并与相近日常使用比较 | 耗电、发热、系统提示；不默认关闭全部省电 |
| 适配与隐私 | 中文/英文、最大字体、横竖屏、手势/三键导航、TalkBack、OEM 迁移 | 裁切/触摸/读屏；锁屏通知可见性、备份排除是否遵守 |

## 结果模板

| 构建/设备 | 场景 | 预期 | 实际 | 证据 | 结论 |
|---|---|---|---|---|---|
| 型号 / 厂商系统 / Android / APK | 测试场景与路线版本 | 预期结果 | 时间/未知比例/耗电等 | 脱敏截图或手记；不向公开日志输出坐标 | 通过 / 待修 / 无法判定 |

实路准确性是后续迭代指标，不承诺精确到秒。未测内容保持“待测”，模拟轨迹通过不代表各设备的后台记录可靠。

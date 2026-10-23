# RideDiary brand assets / 品牌资源

Approved direction: **cream graph paper + a forest-green continuous route + lime and sand endpoints**. Flat, without a notebook, fold, shadow, extra waypoint or motorcycle. The separate motorcycle progress indicator remains in the app.

已选定：**米白方格纸＋深绿连续轨迹＋浅绿和沙色起终点**。采用平面设计，不加入本子、折角、阴影、途经点或摩托车。App 内的摩托车进度条作为功能元素保留。

## Source and usage / 来源与使用

| Asset | Purpose / 用途 |
|---|---|
| [ridediary-logo.svg](ridediary-logo.svg) | Editable production master for README, prototype, favicon and Android resource generation / 可编辑主文件，用于文档、原型、网站图标和 Android 资源生成 |
| [approved-concept.png](approved-concept.png) | Original user-approved generated concept; visual reference, not a runtime resource / 用户确认的原始设计图，仅作参考，不加载到 App |
| [icon-preview.png](icon-preview.png) | Rendered checks of launcher masks, themed icon and notification silhouette / 图标裁切、主题图标和通知轮廓的渲染检查图 |

The concept was generated using the built-in `image_gen` tool. Prompt direction: “Completely flat Android icon, full-bleed warm cream graph paper with faint sage grid lines, a forest-green winding route directly joining two round endpoints, lime departure and sand arrival; no notebook, folded corner, shadow, bevel, words, motorcycle or extra symbols.” The SVG regularizes the grid, colors and curve for crisp production rendering; it preserves the selected concept rather than the later waypoint/motorcycle alternatives.

原始概念使用内置 `image_gen` 生成。提示词方向：纯平面 Android 图标，整幅米白方格纸，淡鼠尾草绿格线，深绿曲线直接连接两个圆形端点，浅绿起点、沙色终点；不要本子、折角、阴影、浮雕、文字、摩托车或额外符号。生产 SVG 将格线、颜色和曲线整理为清晰矢量，沿用已选方案，不采用后来的途经点/摩托车方案。

## Android variants / Android 资源

Run from the repository root after editing the SVG:

修改 SVG 后在项目根目录运行：

```sh
python3 scripts/generate-brand-assets.py
python3 scripts/generate-brand-assets.py --check
```

- `drawable/ic_ridediary_logo.xml`: full-color in-app logo, without tint; decorative beside the visible app name so TalkBack does not announce it twice. / App 内彩色 Logo，不着色；旁边已有品牌文字，避免读屏重复朗读。
- `drawable/ic_launcher_background.xml` + `ic_launcher_foreground.xml`: graph paper and route on separate 108 dp layers. Essential foreground is scaled to fit the centered 66 dp safe circle, with no baked-in launcher mask. / 方格纸与轨迹分层，重要图形保留在安全范围内，不预先裁圆角。
- `mipmap-anydpi/ic_launcher.xml`: adaptive launcher icon for the app's minimum Android 8+, with a monochrome layer used by supported Android 13+ launchers. Older systems ignore that layer. Both normal and round launcher entries use this resource. / 最低 Android 8+ 共用自适应图标，支持的 Android 13+ 启动器使用单色主题层，旧系统忽略该层；普通和圆形启动器共用。
- `drawable/ic_launcher_monochrome.xml`: route and transparent endpoint holes only; the system applies the user's theme. / 只保留轨迹与镂空端点，颜色由系统主题决定。
- `drawable/ic_ridediary_notification.xml`: white alpha silhouette on a transparent 24 dp canvas; no paper grid or solid square background. / 通知栏使用透明底白色轮廓，不放入方格纸或实心背景。

Android layering, safe areas and themed icons follow the [official adaptive icon guide](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive). Preview masks check geometry; launcher rendering and user-selected theme colors can vary by device. / 分层、安全区和主题图标参考 Android 官方指南；预览检查几何裁切，实际启动器效果与主题颜色可能因设备而异。

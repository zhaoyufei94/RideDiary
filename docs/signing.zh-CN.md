# 两台 Mac 共用开发签名

Android 更新同一个包名的应用时要求签名一致。每台电脑默认生成自己的 `~/.android/debug.keystore`，所以过去两台 Mac 的 Debug APK 无法互相覆盖。依据：[Android 官方签名文档](https://developer.android.com/studio/publish/app-signing#debug-mode)。

## 本项目的固定配置

- 包名仍为 `com.ridediary`。
- 两台电脑均使用项目根目录的 `.signing/debug.keystore`，不再自动使用各自的全局 Debug 密钥。
- 沿用当前手机已安装的 0.1.1 所用密钥，因此这次调整不需要卸载或迁移手机数据。
- `signing/debug-certificate.sha256` 保存公开的证书指纹，用于防止导入错误密钥。应用和 Android 测试 APK 共用该开发签名。
- `verifyDebugSigning` 在 Debug APK 签名验证前执行。密钥缺失、私钥不可用或指纹不符时构建失败，不会悄悄生成新密钥。
- 私钥目录 `.signing/` 已被 Git 忽略；可提交公开指纹、脚本与构建配置。

固定证书 SHA-256：

```text
24107b0f1a354d165eb7bae7be2b235752428dae1d4622754e0ebdb84fe110d3
```

## 另一台 Mac 的一次性配置

1. 同步本次代码修改，包括 Gradle 配置、`signing/debug-certificate.sha256` 和导入脚本。
2. 将本机项目的 `.signing/debug.keystore` 通过 AirDrop 或受控的加密存储传到另一台 Mac。只需传这一个密钥文件，不需要 APK、行程数据库或整个 `.tooling/`。私钥不要提交 Git，也不要重新生成。妥善保留一份私钥备份。
3. 另一台电脑配置好 JDK 17 和 Android SDK 后，在项目根目录导入收到的文件：

```sh
python3 scripts/import-debug-signing.py "$HOME/Downloads/debug.keystore"
```

脚本校验证书后，将密钥安装到当前项目的 `.signing/debug.keystore`，目录权限为 700、文件权限为 600。文件名可以不同，参数填写实际路径。错误文件不会覆盖已配置的密钥；不必替换另一台电脑的 `~/.android/debug.keystore`。

4. 编译和查看签名：

```sh
./scripts/gradle-local.sh :app:assembleDebug :app:signingReport
```

也可正常使用 Android Studio 的 Run 或 `./gradlew :app:assembleDebug`，相同的 Gradle 配置都会生效。两台电脑的 `signingReport` 中 Debug 和 DebugAndroidTest 应显示相同的 SHA-256。

5. 覆盖安装：

```sh
adb install --no-streaming -r app/build/outputs/apk/debug/app-debug.apk
```

如果 ADB 未加入 PATH，本机可使用 `.tooling/android-sdk/platform-tools/adb`。以后两台电脑交替编译安装，不需要卸载，原有数据可保留。开发新版本时正常递增 `versionCode`；同签名的旧版本仍可能因版本号降低被系统拒绝，不应把版本降级问题误认为签名冲突。

## 边界与排错

这是开发用 Debug 签名，沿用标准别名 `androiddebugkey` 和标准 Debug 密码；正式分发的 Release 签名需另行配置，当前没有自动把 Release 改为开发签名。

- **Shared Debug key missing**：导入从已配置电脑传来的密钥，不能在当前电脑再生成一份。
- **Wrong Debug signing certificate**：拿到的是另一台电脑自动生成的密钥，或别的项目密钥。重新取得上述固定指纹对应的文件。
- **INSTALL_FAILED_UPDATE_INCOMPATIBLE**：手机仍装着其他签名的旧包。先确认 APK 的证书指纹；如确实需要迁移，先备份数据再处理，不直接卸载日常使用的应用。

密钥文件未通过 Git 同步是有意设计：两台电脑各导入一次即可。后续重新克隆项目或换电脑时，仍需导入这同一份文件。

# GitHub 发布与更新渠道

公开源码与稳定版分发仓库：https://github.com/L-use/WenYanTong 。应用匿名读取 Releases，不包含 GitHub Token。APK 安装包、update.json 和 SHA256SUMS 存放在每个稳定版本的附件中。

## 日常迭代

1. 修改代码，在 `version.properties` 同时增加 versionCode 和 versionName。versionCode 必须严格递增。
2. 添加 `docs/releases/版本号.md` 更新说明。提交代码并等待 Android CI 通过。
3. 运行本机发布脚本：Windows 为 `./tools/release/publish.ps1`，其他系统为 `python tools/release/publish.py`。
4. 脚本本机测试、构建并校验签名，生成 update.json 和文件哈希，创建并推送版本标签，再等待 GitHub 的 Build release candidate 检查通过。
5. 检查通过后，脚本上传签名 APK 与完整元数据到草稿版本，再公开为 latest。现有发布不覆盖；修正应递增版本号。

示例：

```sh
git add app docs version.properties
git commit -m "feat: describe the next improvement"
git push origin main
./tools/release/publish.ps1
```

不要跳过 version.properties 或更新说明，也不要将旧 versionCode 用于新版本。应用以 versionCode 判断升级，不按字符串大小比较版本。

## 签名身份

为兼容已分发的 0.1.0 初版，本项目 GitHub 渠道保留其签名身份，将私钥导入独立 PKCS12 发布密钥库并使用随机强口令。正式 release 构建关闭 debuggable。不要更换该身份，否则已安装用户不能正常覆盖升级。

本机私有签名材料在 `.signing/`，已被 Git 排除。请由维护者妥善保留整份私有备份；不得提交到源码仓库、发布附件或用户数据备份。

签名密钥和口令只留在维护者本机，不上传 GitHub。GitHub Actions 校验资源、运行测试与 lint 并生成不带签名的候选构建；本机发布命令等待检查通过后，仅上传签名后的 APK、update.json 和 SHA256SUMS。仓库不需要签名 Secrets。

其他维护电脑的本地 release 构建可设置 ANDROID_KEYSTORE_PATH、ANDROID_KEYSTORE_PASSWORD、ANDROID_KEY_ALIAS、ANDROID_KEY_PASSWORD，或提供被忽略的 `.signing/release.properties`。未配置密钥时本机发布命令会停止。需要 Python 3、Java、Android SDK、Git 和已登录的 GitHub CLI；Windows 脚本也可识别当前 Codex 自带 Python。私有签名备份需由维护者通过自己的安全方式保留，不属于用户数据备份。

## 应用内更新行为

设置中的“版本更新”提供手动检查、说明、下载进度、取消下载和安装。允许联网的模式下启动时最多每天自动检查一次，失败也计入周期；可关闭自动检查。发现更新时只提示，不自动下载。

离线模式不自动发请求。用户手动检查时先确认本次 GitHub 连接；该确认不改变翻译运行模式。下载仅接受 HTTPS GitHub 地址与受限官方重定向；限制描述文件及 APK 大小，核对 SHA-256、包名、版本与当前应用签名。失败或取消的临时文件不提供给安装器。

安装通过受限 FileProvider 交给安卓系统。首次使用可能需要在系统中允许此应用安装软件，随后返回点“安装更新”。不进行静默安装。正常同签名覆盖升级保留 personal.db 与偏好设置。

资源包在线更新是另一项后续功能，不与 APK 更新混同。

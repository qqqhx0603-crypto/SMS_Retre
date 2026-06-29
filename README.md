# SMS-Retre

## 简介

SMS-Retre 是一个自用 Android 小工具，用来把备用机新收到的短信转发到邮箱。它适合放在长期插卡待机的备用 Android 手机上，例如用于接收境外手机号验证码，再通过邮件同步到主手机。

主要功能：

- 只监听新收到的短信，不读取历史短信。
- 不使用备用机 SIM 卡发短信，只通过邮箱转发。
- 使用 QQ 邮箱 SMTP SSL 发信，默认服务器是 `smtp.qq.com:465`。
- 收到短信后先写入本地队列，发送失败会在约 5 分钟内重试。
- 5 分钟后仍失败则保留失败记录，可在 app 内查看并手动重试。
- 邮件中会标注配置的 SIM1/SIM2 接收号码或备注。
- 检测到备用机电量不高于 5% 时，会发送一次低电量提醒邮件。
- 包名固定为 `com.smsretre.app`，方便在 Clash Meta for Android 里配置单独直连。

项目不内置任何邮箱账号、授权码或签名私钥。

## 用法

### 1. 安装 APK

普通使用者建议从 GitHub Releases 下载 APK，不建议自己从源码构建：

[SMS-Retre Releases](https://github.com/qqqhx0603-crypto/SMS_Retre/releases)

安装后打开 app，授予短信权限和通知权限。

### 2. 获取 QQ 邮箱 SMTP 授权码

SMS-Retre 需要用 QQ 邮箱的 SMTP 授权码发邮件。注意：这里填的不是 QQ 密码，而是 QQ 邮箱生成的第三方客户端专用授权码。

获取步骤：

1. 用电脑浏览器打开 [QQ 邮箱](https://mail.qq.com/) 并登录。
2. 进入 `设置`。
3. 找到 `账号` 或 `账号与安全` 相关页面。
4. 找到 `POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV 服务`。
5. 开启 `POP3/SMTP` 或 `IMAP/SMTP` 服务。
6. 按页面提示完成手机验证、扫码验证或短信验证。
7. 生成授权码，并复制保存。
8. 在 SMS-Retre 的 `SMTP 授权码` 输入框里填这个授权码。

QQ 邮箱官方说明：[如何生成授权码，在第三方客户端使用 POP/IMAP/SMTP 等协议登录](https://help.mail.qq.com/detail/106/985)。

### 3. 配置 SMS-Retre

在 app 里填写：

- `发件 QQ 邮箱`：用于发邮件的 QQ 邮箱地址。
- `SMTP 授权码`：上一步生成的授权码，留空保存表示不修改旧授权码。
- `收件邮箱`：主手机常看的邮箱。
- `SIM1 接收号码/备注`：例如 giffgaff 号码 `+447...`。
- `SIM2 接收号码/备注`：有第二张卡时填写，没有可留空。

然后：

1. 打开 `启用短信转发`。
2. 点击 `保存配置`。
3. 点击 `测试发送`。
4. 主手机邮箱收到测试邮件后，再测试真实短信。

### 4. Clash Meta for Android 设置

如果备用机长期使用 Clash Meta for Android 全局代理，建议让 SMS-Retre 单独直连：

1. 打开 Clash Meta for Android。
2. 进入 `Access Control` / `访问控制`。
3. 选择“选中应用不走代理”或类似模式。
4. 勾选 `SMS-Retre`，包名是 `com.smsretre.app`。

这样其他 app 仍走代理，SMS-Retre 发 QQ 邮箱 SMTP 时走直连。

### 5. vivo / OriginOS 后台设置

为了让备用机息屏后仍能工作，建议给 SMS-Retre 开：

- 自启动。
- 后台运行。
- 关闭电池优化，或允许高耗电后台。
- 允许通知。

## 其他内容

### 更新和覆盖安装

Android 只允许同包名、同签名的 APK 覆盖安装。SMS-Retre 的包名是：

```text
com.smsretre.app
```

如果你安装的是项目维护者发布的 APK，以后继续安装同一维护者发布的新 APK，就可以覆盖安装并保留 app 配置和数据。

如果你自己从源码构建 APK，Android 会把它视为你本地签名的 APK。你自己构建的 APK 可以覆盖你自己构建的后续版本，但不能覆盖别人签名的 APK。

如果安装时提示 `developer signature`、`package conflicts` 或类似签名冲突，说明手机上已有同包名但不同签名的旧版本。此时需要先记录配置，卸载旧版，再安装新版。

### 本地构建

本项目当前使用 Android SDK 命令行工具手动构建，不依赖全局 Gradle。默认 Android SDK 路径：

```text
D:\env\android-sdk
```

在项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
```

构建产物：

```text
build\outputs\apk\debug\app-debug.apk
SMS_Retre.apk
```

其中 `SMS_Retre.apk` 会复制到项目第一级目录，方便直接传到手机安装。

### 签名 key

本机用于覆盖安装的 debug 签名 key 位于：

```text
signing\sms-retre-debug.keystore
```

这个文件不会上传到 GitHub，也不应该公开。删除或更换这个 key 后，再构建的新 APK 将无法覆盖安装旧 APK，除非卸载旧 app。

### 数据和隐私

- 邮箱授权码保存在手机本地，并通过 Android Keystore 加密。
- 短信转发队列保存在 app 私有数据库中。
- 短信内容会通过你配置的邮箱服务器发送到收件邮箱。
- 本项目不适合上架应用商店，主要用于个人自用侧载。

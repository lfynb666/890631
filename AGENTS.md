项目概览
仓库地址: https://github.com/LuFengyuan666/opencode-apk.git

一个极简的 Android APK（Kotlin + Jetpack Compose），用 WebView 内嵌 opencode 的网页界面，
让你在安卓手机上通过局域网连接并操作 opencode。

对 agent 的约定
- 回答使用中文
- 只在用户明确要求时 commit/push
- 不准在本地执行任何构建、测试、lint、打包或安装 APK 的命令
- 代码修改后推送到 GitHub，由 GitHub Actions 自动构建验证

工作方式
┌──────────────────┐         局域网 WiFi         ┌─────────────────────┐
│   Android 手机    │ ◄──────────────────────────► │   Linux PC (Arch)   │
│                   │                              │                     │
│  ┌─────────────┐  │   HTTP(s) + Basic Auth       │  opencode web \     │
│  │   WebView   │  │ ◄────────────────────────── │  --hostname 0.0.0.0 │
│  │ (opencode   │  │                              │  --port 4096        │
│  │  web UI)    │  │                              └─────────────────────┘
│  └─────────────┘  │
└──────────────────┘
1. 在 Linux PC 上启动 opencode 服务：opencode web --hostname 0.0.0.0 --port 4096
2. 在安卓手机上打开本 APK（需与 PC 在同一 WiFi/局域网）
3. 输入 PC 的局域网 IP + 端口，如有密码则输入密码
4. WebView 加载 opencode 网页界面，即可在手机上使用
为什么用 WebView 而不是原生开发
opencode 自带的网页界面（opencode web）功能已经非常完善，包含：
- 会话管理（新建、切换、分享）
- 完整的聊天界面
- 文件浏览与代码查看
- 模型切换、提供商配置
- 命令面板
直接用 WebView 内嵌这个网页，开发量极小，但体验完整。
不需要用 REST API 从头写一套原生 UI。
项目结构
opencode-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/opencode/
│   │   │   ├── MainActivity.kt                 # Compose Activity 入口（连接页）
│   │   │   ├── NativeWebViewActivity.kt        # 主 WebView Activity（通知/SSE/文件上传）
│   │   │   ├── OpencodePermissionReceiver.kt   # BroadcastReceiver 处理权限通知
│   │   │   ├── ui/
│   │   │   │   ├── theme/                       # Material 3 主题配色
│   │   │   │   ├── screen/
│   │   │   │   │   ├── ConnectScreen.kt         # 连接页：IP、端口、密码
│   │   │   │   │   └── WebViewScreen.kt         # 调试用 Compose WebView（带 debug overlay）
│   │   │   ├── data/
│   │   │   │   ├── ConnectionSettings.kt        # 连接配置数据类
│   │   │   │   └── Preferences.kt               # DataStore 持久化保存设置
│   │   │   └── network/
│   │   │       └── HealthCheck.kt               # 检测服务器是否可达
│   │   ├── res/                                 # 字符串、图标等资源
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml                       # 依赖版本统一管理
├── build.gradle.kts                              # 根构建脚本
├── settings.gradle.kts
└── gradle.properties
技术栈
层面	选型
语言	Kotlin
UI 框架	Jetpack Compose + Material 3
WebView	Android System WebView（基于 Chromium）
网络检测	OkHttp 4.12（检查服务器健康 /global/health）
本地存储	Jetpack DataStore (Preferences)
构建	Gradle Kotlin DSL
最低 SDK	26 (Android 8.0)
目标 SDK	35 (Android 15)
编译 SDK	35
核心功能
当前已实现
- 连接页面：输入 IP:Port 和可选密码
- WebView 加载 opencode 网页
- HTTP Basic Auth 自动处理
- 记住上次的连接设置（DataStore 持久化）
- 服务器连通性检测（开屏先探活）
- SSE 事件监听（/event 端点），通过原生通知推送任务/权限事件
- 权限请求通知支持 Allow/Deny 快捷操作
- 全屏沉浸模式
- 文件上传支持（WebView 文件选择器）
- 键盘自适应（键盘弹起时 WebView 底部上移）
- INTERNET、POST_NOTIFICATIONS、READ_MEDIA_* 及 READ_EXTERNAL_STORAGE 权限（用于 WebView 文件上传和通知推送）
后续可加
- mDNS 自动发现局域网内的 opencode 服务（opencode.local）
- 连接历史记录（多台 PC 快速切换）
- 后台保持连接 / 通知提醒
- 页面下拉刷新
开发环境搭建
1. 安装 Android 开发工具
# Arch Linux
yay -S android-studio android-sdk android-sdk-platform-tools

# Ubuntu
sudo snap install android-studio --classic
# 打开 Android Studio，按向导安装 SDK 35
确保已安装：
- JDK 17+
- Android SDK Platform 35
- Android SDK Build-Tools 35+
- Gradle 8.x（CI 通过 setup-gradle Action 安装，本地若构建需自行配置）
2. 初始化项目
# 在 Android Studio 中创建新项目：
# - 模板：Empty Activity（Compose）
# - 包名：com.example.opencode
# - 语言：Kotlin
# - Minimum SDK：API 26

# 或者用命令行
# （以下为手动创建，非必须）
mkdir -p app/src/main/java/com/example/opencode/ui/screen
mkdir -p app/src/main/java/com/example/opencode/data
mkdir -p app/src/main/java/com/example/opencode/network
mkdir -p app/src/main/res/values
3. 依赖库（app/build.gradle.kts）
dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // OkHttp (健康检查)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
4. 提交并推送代码
git add .
git commit -m "Update opencode Android APK"
git push origin main

5. 远端自动构建 APK
- 推送到 `https://github.com/LuFengyuan666/opencode-apk.git` 后，由 GitHub Actions 自动触发构建。
- 在 GitHub 仓库的 Actions 页面查看构建状态。
- 构建完成后，从 Actions artifact 或 Release 页面下载 APK。
- 不在本地执行任何 Gradle 构建、测试、lint 或 APK 安装命令。
Linux 端 opencode 服务配置
启动命令
# 基础版（局域网可访问）
opencode web --hostname 0.0.0.0 --port 4096

# 带密码保护
OPENCODE_SERVER_PASSWORD=你的密码 opencode web --hostname 0.0.0.0 --port 4096

# 无密码 + mDNS 自动发现
opencode web --mdns --port 4096

# 只想启动 API 服务（不自动打开浏览器）
opencode serve --hostname 0.0.0.0 --port 4096
查看 PC 的局域网 IP
ip addr show | grep 'inet ' | grep -v 127.0.0.1
# 或者
hostname -I
例如输出 192.168.1.100，那在手机上就输入 192.168.1.100:4096。
防火墙放行
# 如果 PC 开了防火墙，需要放行端口
sudo ufw allow 4096/tcp
# 或者 iptables
sudo iptables -A INPUT -p tcp --dport 4096 -j ACCEPT
关键实现要点
1. WebView 配置
// WebView 需要做以下设置：
webView.settings.apply {
    javaScriptEnabled = true       // opencode web UI 依赖 JS
    domStorageEnabled = true       // localStorage 等
    allowFileAccess = false        // 安全：禁止访问本地文件
    mixedContentMode = MIXED_CONTENT_ALWAYS_ALLOW  // 局域网 HTTP
    cacheMode = LOAD_DEFAULT       // 启用缓存
    setSupportZoom(true)           // 允许缩放
    builtInZoomControls = true
    displayZoomControls = false    // 隐藏缩放控件
}
2. HTTP Basic Auth 处理
webView.webViewClient = object : WebViewClient() {
    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String
    ) {
        // 使用用户输入的密码自动登录
        handler.proceed(username, password)
    }
}
3. 服务器连通性检测
// 启动时先请求 /global/health 探活
// 收到 { "healthy": true, "version": "..." } 表示连接成功
suspend fun checkHealth(url: String): Boolean {
    val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder().url("$url/global/health").build()
    return try {
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }
}
代码风格约定
- Kotlin 写法：遵循 Kotlin 官方风格。能用 val 不用 var，优先表达式体函数。
- Compose 写法：遵循官方指南。状态提升（hoisting）、单向数据流。
- 命名：Composable 用 PascalCase，其余用 camelCase。资源 ID 用 snake_case。
- 导入：不用通配符导入（*），不用别名导入。
- 注释：只写不显而易见的逻辑，不写废话注释。
- 不用 emoji。

构建与验证
- 不允许在本地执行 Gradle 构建、测试、lint 或 APK 安装命令
- 构建由 GitHub Actions 自动完成：
  - 推送到 main 分支 → .github/workflows/android-debug-apk.yml 触发 debug 构建
  - 推送 v* 标签 → .github/workflows/release-debug-apk.yml 创建 GitHub Release
- APK 只能从 GitHub Actions 的构建产物或发布页获取，不使用本地输出目录
- 查看构建结果：GitHub 仓库 Actions 页面或 Release 页面
- 可以用 adb logcat | grep -i opencode 查看应用日志
- 如遇到仓库权限、Action 配置、签名配置等不明确的问题，先询问项目负责人，不自行假设

安全提醒（个人使用场景）
- WebView 默认不信任所有证书，这是安全的，不用改动。
- 密码通过 HTTP Basic Auth 传输，仅在可信局域网内使用。
- 服务器密码明文保存在 DataStore 中（个人使用足够简单，如需分发可用 EncryptedSharedPreferences）。
- 申请 INTERNET、POST_NOTIFICATIONS 及文件读取相关权限（用于 WebView 文件上传），不申请相机、位置等无关权限。

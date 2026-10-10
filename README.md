<!--
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at https://mozilla.org/MPL/2.0/.
-->
# Bili++ - 哔哩哔哩播放量辅助工具

基于 Android WebView 的哔哩哔哩视频播放量辅助工具。通过模拟独立浏览器访问，在本地循环刷新视频页面并自动检测播放量变化。

## 核心代码

本项目移植自两个核心文件：

| 文件 | 说明 |
|------|------|
| `1.py` | **主程序原型**。Python + Selenium 实现的PC端播放量辅助脚本，包含随机User-Agent池（Chrome/Edge/QQ/360/2345/搜狗）、浏览器启动配置、防检测设置、页面停留模拟人类行为（随机滚动+鼠标事件）、通过B站API获取播放量、循环控制等全部核心逻辑。Android端的WebView方案即基于此移植。 |
| `bvtoav.txt` | **BV/AV号转换算法**。TypeScript实现的B站视频号互转工具，基于B站的58进制编码表和XOR混淆算法，支持 BV→AV 和 AV→BV 双向转换。Android端的 `BvAvConverter.kt` 直接移植自此文件的算法。 |

## 功能

- **多格式链接支持**：BV号、AV号、b23.tv 短链接（自动解析重定向）
- **自动循环播放**：访问视频 → 停留30秒 → 获取播放量 → 关闭页面并清理Cookie/缓存 → 冷却30秒 → 重复
- **播放量实时监测**：每轮循环调用B站API获取播放量，显示当前值和变化量 Δ
- **静音播放**：系统级静音 + 页面级 JS 静音，双重保障
- **冷却倒计时**：冷却期间显示30秒倒计时页面，平板/手机自适应布局（平板左右分栏，手机上下排列）
- **后台保活**：前台服务 + 通知栏显示播放量、WakeLock 防休眠
- **防检测**：每次刷新随机切换PC端 User-Agent，清除 Cookie/缓存/LocalStorage
- **电池优化**：一键关闭电池优化，防止系统杀后台
- **通知权限**：Android 13+ 自动请求通知权限用于保活

## 技术栈

- Kotlin
- Android WebView
- OkHttp（短链接解析、播放量API）
- Material Design Components
- 前台服务（Foreground Service）

## 项目结构

```
bili++/
├── 1.py                          # 核心原型 - Python+Selenium 主程序
├── bvtoav.txt                    # 核心算法 - BV/AV号互转 (TypeScript)
├── .gitignore
├── README.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── app/src/main/
    ├── AndroidManifest.xml
    ├── assets/
    │   └── cooldown.html          # 冷却倒计时页面
    ├── java/com/bili/biliplus/
    │   ├── MainActivity.kt        # 主界面、WebView管理、循环逻辑
    │   ├── ViewCountService.kt    # 前台服务保活 + 通知
    │   └── BvAvConverter.kt       # BV/AV号互转 (移植自bvtoav.txt)
    └── res/
        ├── layout/activity_main.xml
        ├── menu/main_menu.xml
        ├── values/strings.xml, colors.xml, themes.xml
        ├── drawable/ic_notification.xml
        └── xml/network_security_config.xml
```

## 构建

```bash
# 设置 JAVA_HOME（Android Studio JBR）
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

# 使用 Gradle 构建
gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

也可直接用 Android Studio 打开项目目录，Sync Gradle 后编译运行。

## 使用方法

1. 安装 APK 到 Android 设备
2. 打开应用，输入视频链接（支持以下格式）：
   - `https://www.bilibili.com/video/BV1HfKiz3Ezf`
   - `https://www.bilibili.com/video/av114753305447594`
   - `https://b23.tv/xxxxx`
3. 点击「开始」
4. 首次启动会立即获取一次播放量作为基准值
5. 之后每轮循环：
   - 加载视频页面并停留30秒
   - 获取最新播放量，计算变化量
   - 关闭页面，清理Cookie和缓存
   - 冷却30秒（显示倒计时页面）
   - 刷新视频页面，开始下一轮
6. 点击「停止」结束循环

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 访问B站网页和API |
| FOREGROUND_SERVICE | 前台服务保活 |
| POST_NOTIFICATIONS | 通知栏显示播放量（Android 13+） |
| WAKE_LOCK | 防止CPU休眠 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 关闭电池优化 |

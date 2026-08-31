# GSI 管理器 (GsiManager)

基于 Android 系统 DSU（Dynamic System Update）机制的 GSI 管理 App。
通过 Root 调用系统 DSU 服务（`com.android.dynsystem`）与 `gsi_tool`，实现在手机上直接安装、管理、撤销 GSI（Generic System Image），无需电脑刷机。

## 功能

| 功能 | 说明 |
|------|------|
| Root 检测 | 未获取 Root 显示红色，已获取显示蓝色 |
| GSI 状态检测 | LOGO 卡片展示 GSI 状态（运行中 / 已安装 / 已启用 / 未安装），背景可换颜色或图片 |
| 安装 GSI | 选择 userdata 容量（8/16/32/64 GB 或自定义）→ 选择 zip 安装包 → 通过系统 DSU 安装（DSU Sideloader 方法） |
| 重启到 DSU | 安装完成后重启进入 GSI 系统 |
| 撤销 GSI | 通过 `gsi_tool wipe` 完全移除已安装的 GSI |
| GSI 信息 | 查看 DSU 槽位、已安装镜像、AVB 公钥与原始状态输出 |

## UI 布局

- 浅灰背景 + 白色大圆角卡片风格
- 顶部：长方形 LOGO 卡片（应用名 + Root 状态指示灯 + GSI 状态 + 背景设置），宽度略小于屏幕
- 中部：纵向白色功能卡片（左侧彩色圆形图标 + 标题/说明），对应各功能
- 底部：蓝色胶囊主按钮「安装 GSI」

## 使用条件

- Android 9 (API 28) 及以上，Treble 兼容设备
- 已解锁 bootloader，设备自带 DSU 支持（`/system/priv-app/DynamicSystemInstallationService`）
- 已获取 Root 权限（Magisk 等）
- GSI 安装包 zip（内含 `system.img`，例如 Google 官方 GSI 包）

## 安装

1. 使用 Android Studio 打开本项目，或命令行构建（见下节）。
2. 将生成的 `app-debug.apk` 安装到手机：
   `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. 打开应用，允许 Root 授权。

## 命令行构建

```bash
export ANDROID_HOME=/opt/android-sdk
# 安装 JDK 17 与 Android SDK(platforms;android-34, build-tools;34.0.0)
/opt/gradle-8.6/bin/gradle --no-daemon assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

## 安装原理（DSU Sideloader 方法）

安装不再调用 `gsi_tool install`（其 stdin 直写方式与多数厂商 ROM 的 gsi_tool 不兼容），改为参照 [VegaBobo/DSU-Sideloader](https://github.com/VegaBobo/DSU-Sideloader) 的 `DsuInstallationHandler` 做法，交由系统 DSU 应用安装：

1. App 启动本地 HTTP 服务（127.0.0.1 随机端口，带 Range 支持）提供 zip 包，并以前台服务保活；
2. 通过 `am force-stop com.android.dynsystem` 重置系统 DSU 状态；
3. 启动系统 DSU 安装界面：

   `am start-activity -n com.android.dynsystem/com.android.dynsystem.VerificationActivity -a android.os.image.action.START_INSTALL -d <本地URL> --el KEY_USERDATA_SIZE <字节> --el KEY_SYSTEM_SIZE <字节>`

4. 系统 DSU（DynamicSystemInstallationService）从本地 URL 下载 zip 并完成安装，进度显示在系统界面与通知中。

## 底层命令映射

App 内部通过 `su` 调用的命令：

| 操作 | 命令 |
|------|------|
| 状态检测 | `gsi_tool status`（不支持时回退 `gsi_tool getstatus`） |
| 安装 GSI | 见上文「安装原理」，由 `com.android.dynsystem` 完成 |
| 安装前清 userdata（可选） | `gsi_tool wipe-data` |
| 重启到 DSU | `reboot` |
| 撤销 GSI | `gsi_tool wipe` |
| 重新启用 | `gsi_tool enable` |

> `gsi_tool status` 输出解析：`running` 表示正在运行 GSI；`installed`/`enabled`/`disabled` 表示已安装及启用状态；`normal` 表示未安装任何 GSI。

> 安装期间请保持 App 在后台运行（本地服务需存活），下载/安装进度见系统 DSU 界面与通知；完成后点击通知或 App 内「重启到 DSU」进入 GSI。

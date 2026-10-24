# SkyADB-Pro 项目文档

本文档基于当前源码实际状态（v1.0.1）整理，用于说明 SkyADB-Pro 的功能范围、项目结构、开发规范、维护规范和性能要求。后续开发应在功能或工程结构变化后同步更新本文档。

## 项目定位

SkyADB-Pro 是一款运行在 Android 手机上的 ADB 调试管理工具，包名 `com.qwadb.skyadb3`，基于 [SkyADB](https://github.com/sky22333/skyadb) 优化改进，遵循 GPL-3.0 协议。它通过 WiFi ADB / Wireless Debugging 连接并管理目标 Android 设备，适用于手机、平板、电视、盒子等设备的日常维护。

当前交付目标是 Android App，未来仅做少量架构预留，方便后续评估 Windows 端支持，不在当前版本中引入复杂跨平台工程。

## 当前功能

- **手动连接**：输入目标设备 IP 和 ADB 端口（默认 5555）。
- **无线配对**：输入配对 IP、配对端口、6 位配对码后调用 Kadb Pairing。
- **扫码连接**：扫描目标设备无线调试二维码，自动解析配对地址与配对码并完成连接（CameraX + ZXing）。
- **局域网发现**：优先通过 Android NSD / mDNS 自动发现 ADB 服务，支持 `_adb._tcp.`、`_adb-tls-pairing._tcp.`、`_adb-tls-connect._tcp.`；无结果时保留 TCP 网段扫描与 ADB 协议轻握手兜底。
- **USB OTG**：识别 ADB / Fastboot 接口，授权后连接；Fastboot 命令可在 Shell 页执行。
- **最近设备**：连接成功后保存设备记录，支持点击回填。
- **连接分享**：为当前连接地址生成二维码，扫码即可 `adb connect`。
- **设备详情**：读取品牌、型号、Android 版本、SDK、ABI、分辨率、电池信息。
- **控制模式（屏幕镜像）**：基于官方 `scrcpy-server-v4.1`，支持实时画面、远程触控、音频转发与中文输入；镜像页可显示/隐藏控制区，画面按远端比例居中。
- **观看模式**：纯观看目标设备屏幕，不含控制。
- **截图**：调用 `screencap` 拉取截图到本机缓存并预览，需要时由用户选择保存位置。
- **虚拟遥控器**：模拟返回、主页、最近任务、方向键、确认、音量、静音、媒体控制等常用按键，支持自定义组合键（按 keyCode 构建，兼容 R8 混淆）。
- **应用管理**：读取目标设备应用列表，支持用户/系统应用分类、搜索、启动、停止、**冻结 / 解冻**、卸载、导出，支持多选批量卸载 / 导出。
- **本机应用**：列出本机用户应用，支持单 APK 导出并安装到目标设备。
- **安装 APK**：选择本机 APK 安装到已连接设备。
- **在线下载**：从 HTTP / HTTPS 链接下载 APK 或普通文件，支持进度、取消、断点续传，下载后安装或推送。
- **文件管理**：浏览目标设备目录、上传、下载、新建文件夹、删除文件，支持多选批量传输。
- **上传到设备**：将本机文件一键 Push 到目标设备指定目录，支持多选与进度展示。
- **从设备拉取**：将目标设备文件 Pull 到本机 Download 目录，支持多选批量拉取。
- **存储空间**：概览目标设备各分区存储占用情况。
- **ADB5555**：一键开启已连接设备的 5555 无线 ADB 常连端口（优先 `adb tcpip`，失败回退 `setprop`），并在 adbd 重启后自动重连；异常时给出明确提示。
- **查看摄像头**：查看目标设备前置 / 后置摄像头画面。
- **媒体库**：浏览目标设备媒体文件。
- **系统状态**：网络、电池、存储、CPU、内存、屏幕亮度、当前应用与进程列表实时监控。
- **系统日志**：读取目标设备最近系统日志，支持复制。
- **Shell**：执行单条 Shell 命令并展示输出、错误输出、退出码，支持输出复制、命令历史与收藏持久化。
- **诊断日志**：仅记录错误和异常信息，覆盖连接、配对、应用、文件、截图、系统日志、遥控器、屏幕镜像等功能，支持复制排查。
- **设置**：默认端口、连接超时、命令超时、扫描网段、镜像画质（流畅/均衡/高清/自定义）、摄像头画质、状态刷新间隔、主题模式、语言（中文/English）、清理最近设备。
- **版本更新**：设置页检查 GitHub Releases 最新版本，支持应用内下载并安装更新。

## 技术栈

- Kotlin 2.3.20
- Android Gradle Plugin 9.2.0（本地 Gradle Wrapper 9.4.1，CI 使用 9.5.1），JDK 17
- Jetpack Compose + Material 3
- Navigation Compose
- Lifecycle ViewModel + StateFlow
- Kotlin Coroutines / Flow
- DataStore Preferences
- Kadb 2.1.3（ADB）
- scrcpy-server 4.1（屏幕镜像）
- OkHttp 5.3.2 / Okio
- CameraX（扫码相机）
- ZXing（二维码解析）
- fastboot-java（USB OTG Fastboot）
- Timber
- JUnit

## 运行范围

- 运行 SkyADB-Pro 的设备：最低 Android 7.0，API 24。
- 编译目标：compileSdk 37，targetSdk 37。
- Android 17 局域网：targetSdk 37 需声明并运行时申请 `ACCESS_LOCAL_NETWORK`，用于 mDNS 发现、网段扫描和 WiFi ADB 连接。
- 被连接设备：以目标设备是否支持 ADB over TCP / Wireless Debugging 为准。Android 11+ 推荐使用无线调试配对；传统 WiFi ADB 设备通常使用 `5555` 端口。
- 屏幕镜像目标设备：以目标设备是否支持 scrcpy server、视频编码和 ADB socket 转发为准；默认使用均衡画质，高负载设备可在设置中切换流畅模式。

## 项目结构

```text
SkyADB-Pro/
├── .github/
│   └── workflows/
│       └── android-ci.yml                 # GitHub Actions：测试、lint、签名构建、发布 Release APK
├── app/
│   ├── build.gradle.kts                   # App 配置、版本号、签名、依赖
│   └── src/main/
│       ├── AndroidManifest.xml            # 权限、应用入口、图标、应用名、CAMERA 权限
│       ├── assets/scrcpy/scrcpy-server-v4.1 # 官方 scrcpy server 资产
│       ├── java/com/qwadb/app/
│       │   ├── AdbManagerApplication.kt   # Application 初始化入口
│       │   ├── AppServices.kt             # 轻量服务容器
│       │   ├── MainActivity.kt            # Compose Activity 入口
│       │   ├── adb/                       # Kadb 封装：连接、配对、Shell、安装、传输、截图、tcpip
│       │   ├── apps/                      # 应用信息补全
│       │   ├── data/                      # DataStore：设置、最近设备
│       │   ├── diagnostics/               # 诊断日志模型、收集、格式化
│       │   ├── discovery/                 # mDNS 发现、TCP 网段扫描、协议探测
│       │   ├── download/                  # OkHttp 下载、进度、取消
│       │   ├── files/                     # 本地缓存与 SAF 文件复制
│       │   ├── i18n/                      # 语言与文案
│       │   ├── localapps/                 # 本机应用导出与图标
│       │   ├── model/                     # 设备、应用、结果、文件、状态模型
│       │   ├── repository/                # AdbRepository：面向 UI 的 ADB 仓库层
│       │   ├── scrcpy/                    # 镜像会话、协议、解码、音频、摄像头
│       │   ├── status/                    # 系统状态采集
│       │   ├── usb/                       # USB OTG
│       │   ├── validation/                # IP/端口/URL/路径校验
│       │   └── ui/                        # Compose 页面（apps/camera/components/device/
│       │       │                          #   diagnostics/discovery/download/files/gallery/
│       │       │                          #   home/install/localapps/logs/mirror/pairing/remote/
│       │       │                          #   screenshot/settings/shared/shell/status/storage/theme/watch）
│       │       ├── AdbManagerApp.kt       # 导航、底部栏、页面切换动画
│       │       ├── pairing/QrScanScreen.kt # 扫码连接
│       │       ├── files/UploadScreen.kt   # 上传到设备
│       │       ├── files/DeviceFilesScreen.kt # 从设备拉取
│       │       └── storage/StorageOverviewScreen.kt # 存储空间概览
│       └── res/
│           ├── mipmap-*/ic_launcher.png   # 应用图标
│           └── values*/                    # strings.xml / values-zh-rCN 双语资源
├── docs/
│   ├── README.md                          # 当前项目总文档
│   └── img/                               # 预览图片
├── gradle/
│   └── libs.versions.toml                 # 依赖版本集中管理
├── .editorconfig / .gitattributes / .gitignore
├── build.gradle.kts / settings.gradle.kts
└── README.md                              # 面向用户的简洁产品介绍
```

## 架构说明

当前架构采用单 Android App 模块，保持实现直接、边界清晰。

- UI 层：Compose 页面只负责展示状态和派发用户事件。
- ViewModel 层：持有页面状态，调用仓库、下载、文件、扫描等服务。
- Repository 层：面向 UI 提供稳定的 ADB 操作接口，隐藏 Kadb 细节。
- KadbManager：集中封装真实 ADB 能力（含 `tcpip`、冻结/解冻、文件 Push/Pull、存储概览），避免页面层直接拼接底层 API。
- DataStore：保存设置、最近设备、Shell 历史与收藏，所有读取以 Flow 形式向上游暴露。
- Discovery：自动发现和扫描逻辑独立于 UI。mDNS 基于平台 `NsdManager` 仅在发现页前台运行，TCP 扫描支持并发限制、超时、取消和进度节流。
- Download：下载逻辑独立于页面，支持取消、缓存目录、进度节流和文件名推断。
- Diagnostics：错误日志仅保存在进程内，用于定位连接、ADB 操作和功能异常，不做永久存储。
- Scrcpy：屏幕镜像逻辑独立于页面，使用单独 KADB 流式连接、官方 server、MediaCodec 硬解码和控制协议封装。
- Camera：扫码使用 CameraX + ZXing，手动抽取 Y 平面解码，兼容旋转角度。
- Validation：输入校验集中维护，避免每个页面重复实现 IP、端口、URL、路径判断。

## UI 与交互规范

- App 内所有可见文案必须使用简体中文（并提供 English 资源），品牌名统一写作 `SkyADB-Pro`。
- 使用 Material 3 作为基础，保持现代、扁平、克制、工具型产品风格。
- 页面不能有粗糙默认感，按钮、卡片、输入框、状态徽章、列表间距必须统一。
- 首页、设置页、设备页、Shell 页等长内容页面使用 `LazyColumn`，避免滚动不跟手。
- 页面切换使用轻量 fade + slide，动画时长控制在 120 到 180 ms。
- 列表项要尽量轻量，避免在列表滚动时做阻塞 IO、ADB 调用或大图加载。
- 耗时操作必须展示状态，失败必须给出中文原因和下一步建议。
- 危险操作（卸载、冻结、删除文件）应保留确认流程。
- 屏幕镜像页以画面为主，顶部只保留关闭和控制区开关，底部控制区可隐藏。

## 性能要求

- 所有 ADB、下载、文件、扫描、本机应用读取等耗时任务必须放到 `Dispatchers.IO` 或 ViewModel 协程中执行，不能阻塞主线程。
- 下载进度和扫描进度必须节流，避免高频状态更新造成 Compose 频繁重组。
- 局域网扫描必须限制并发（默认不超过 64），默认超时 600 ms。
- mDNS 自动发现只在进入局域网发现页时启动，离开必须停止，不做后台常驻监听。
- 默认扫描 `/24` 网段，最多手动配置 6 个网段。
- 扫描只在用户主动触发时执行，不做后台常驻扫描。
- 下载文件和导出的 APK 放在缓存目录，导出前清理旧 APK，避免缓存无限膨胀。
- 大列表必须优先使用 `LazyColumn`，列表项保持稳定 key。
- ViewModel 中的 Flow 收集和协程任务要跟随生命周期释放，避免 Activity 泄漏。
- 不在 Compose 页面中直接持有 `Context` 长生命周期引用；需要上下文的服务使用 `applicationContext`。
- 屏幕镜像默认均衡画质，用户可切换流畅/均衡/高清/自定义；高清不作为默认值。
- 屏幕镜像使用 `SurfaceView` 按远端视频比例居中显示，触摸坐标必须与实际画面区域一致。
- 屏幕镜像不因短暂切后台或 Surface 重建而拆除会话；回前台通过换绑 Surface 并 `RESET_VIDEO` 恢复画面。

## 开发规范

- 开发前先阅读当前源码、已有 docs、相关依赖实际 API 和官方文档。
- 不凭经验猜测 Kadb、Compose、DataStore、OkHttp、Android 平台 API 行为。
- 每次改动保持小范围、可解释、可验证，不做无关重构。
- 所有新增页面和功能必须有加载、空状态、错误态、执行中、成功态的处理。
- 业务逻辑优先放在 ViewModel、Repository、Manager 或 Validator 中，不塞进 Composable。
- 不重复造同类输入校验、状态展示、结果封装。
- 新增 ADB 命令时必须封装在 KadbManager 或 Repository 层，页面只调用语义化方法。
- 新增设置项必须进入 `AppSettingsStore`，并在设置页保持布局紧凑统一。
- 新增持久化数据要考虑字段升级、默认值和读取失败兜底。
- 新增网络能力必须考虑超时、取消、错误提示、缓存清理和进度节流。
- 新增 UI 要遵循现有 `AppDimens`、主题颜色、圆角和 Material Icons 风格（Compose 图标为扩展属性，须逐个 import）。
- 新增镜像参数必须通过 `MirrorQualityPreset` 或 `ScrcpyOptions` 集中管理，不能散落硬编码。

## 验收标准

- 功能必须生产可用，不以实验、演示或临时代码为交付目标。
- 实现必须直击需求根因，优先复用现有项目结构和已有组件，不堆砌兜底逻辑。
- 代码量保持必要且最少，不新增重复抽象、重复状态、重复入口或重复校验。
- 引入新实现时必须同步删除被替代的旧实现、旧文案、旧状态和无用代码，不保留死代码。
- 业务逻辑必须闭环：有入口、有执行中状态、有成功反馈、有失败原因、有释放资源或取消路径。
- 失败信息必须尽量反映真实原因（如冻结命令返回成功但设备实际状态未变时如实报错），并进入诊断日志。
- 所有耗时、网络、ADB、文件、解码、扫描和 socket 操作不得阻塞主线程。
- 资源必须按生命周期释放，避免后台常驻、协程泄漏、流未关闭、缓存无限增长和重复扫描。
- 默认行为必须兼顾省电、低占用和兼容性；高性能或高画质能力必须由用户主动选择。
- 交互必须形成闭环：用户知道当前状态、能取消或返回、失败后知道下一步。
- 新增功能必须覆盖关键路径测试；测试要少而准确，优先覆盖解析、映射、参数、错误回退等纯逻辑。
- 不使用已知即将弃用或已弃用的 API；遇到不确定时必须查阅最新官方文档或当前源码。
- 不提交临时计划文档、缓存文件、构建产物、签名证书、APK、AAB。
- 改动完成后至少执行静态检查，确认 `git diff --check` 通过；本地无法编译时由 CI 验证。

## 维护规范

- 每次改动保持小范围、可审查、可验证，不新增临时计划文档。
- 依赖升级、CI 调整、Kadb API 行为变化要同步更新本文档。
- GitHub Actions 构建失败时，先根据日志定位具体任务，再修复 workflow 或源码。
- Release 构建需要仓库 Secrets：`SIGNING_KEY_BASE64`、`KEY_ALIAS`、`KEY_STORE_PASSWORD`、`KEY_PASSWORD`。
- 签名证书必须使用 PKCS#12 格式，不允许提交到仓库。

## 编码与换行规范

- 文本文件默认 UTF-8；默认 LF 换行。
- Markdown、YAML、JSON、XML 使用 2 空格缩进；Kotlin 使用 4 空格缩进。
- Windows 脚本文件允许 CRLF。
- 二进制文件、图片、APK、签名证书按 `.gitattributes` 标记处理。
- 签名证书、构建产物、APK、AAB 不提交到仓库。

## 发布规范

- **版本号方案**：正式版 `versionCode = (MAJOR * 10000 + MINOR * 100 + PATCH) * 10 + 9`。例如 `1.0.0=100009`、`1.0.1=100019`。
- 本地默认 `versionCode=1000`、`versionName=1.0.1`（兜底值），正式包必须由 CI 注入 `VERSION_CODE` / `VERSION_NAME`。
- **命名规范（关键）**：发布 tag 用 `v{版本}`（如 `v1.0.1`）；**发行 APK 文件名不带 v**，固定为 `SkyADB-Pro-{版本}-release.apk`。
  - 依据 `ui/settings/SettingsViewModel.kt`：`fallbackApkUrl` = `https://github.com/CN-QiuWan/SkyADB-Pro/releases/download/v$version/SkyADB-Pro-$version-release.apk`，`downloadUpdate` 的 `preferredFileName` = `SkyADB-Pro-$latestVersion-release.apk`；文件名带 v 会导致 assets 解析走兜底时 404。
  - 更新检测只看 Release 的 `tag_name`（`removePrefix("v")` 后与 `BuildConfig.VERSION_NAME` 比较），与文件名无关；文件名只影响下载链路。
- 正式发布由 GitHub Actions（`android-ci.yml`，手动 `workflow_dispatch`）触发，输入 `v主.次.修`；测试版追加 `-beta`。
- Release APK 由 CI 使用 PKCS#12 证书（v2+v3 双签名）签名后发布到 GitHub Releases，资产名固定 `SkyADB-Pro-{版本}-release.apk`。
- Release notes 可用 CI 自动生成（基于上一个 `v*` tag 到当前提交的 commit），也可人工撰写更新介绍。

## 当前风险与注意事项

- 屏幕镜像依赖 `app/src/main/assets/scrcpy/scrcpy-server-v4.1`，更新 server 版本时必须同步更新 `ScrcpyConstants` 和相关协议适配。
- 屏幕镜像会话与 Surface 解耦：`surfaceDestroyed` 只暂停渲染，不拆除 scrcpy；新 Surface 通过 `MediaCodec.setOutputSurface` 与 `RESET_VIDEO` 恢复。
- `pm disable-user`（冻结）在部分厂商 ROM（小米 HyperOS/MIUI、华为 EMUI、三星等）可能对系统应用或受保护组件静默忽略（exit 0 但不生效）；已做真实状态回查并如实报错，仍不支持时用户可改用系统设置或 device-owner/root。
- ADB5555 重启 adbd 会中断当前会话，采用 `adb tcpip` 优先并对重启后自动重连；部分 ROM 可能限制 `tcpip`，此时提示手动连接。
- mDNS 自动发现依赖局域网 multicast / DNS-SD 支持，部分路由器、访客网络、热点隔离、跨网段场景可能无法发现服务；此时可继续使用网段扫描或手动配置网段。
- 本机应用安装到目标设备当前只支持用户应用的单 APK 导出；Split APK / App Bundle 不在当前范围。
- Shell 是单条命令执行，不是交互式终端。
- 设备断线后的主动心跳和自动重连当前未作为常驻功能实现，避免增加耗电和后台复杂度。

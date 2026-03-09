# SVCMonitor v4.0

**ARM64 SVC 系统调用监控工具** — KernelPatch Module (KPM) + Android APP 完整方案

适用平台: Google Pixel 6 (oriole) · Android 12 · Kernel 5.10.43 · KernelPatch v0.10.7 (a07)

---

## 目录

- [项目概述](#项目概述)
- [架构设计](#架构设计)
- [目录结构](#目录结构)
- [KPM 模块](#kpm-模块)
  - [模块文件说明](#模块文件说明)
  - [编译方法](#编译方法)
  - [安装与加载](#安装与加载)
  - [CTL0 命令接口](#ctl0-命令接口)
  - [技术细节](#技术细节)
- [Android APP](#android-app)
  - [功能说明](#功能说明)
  - [编译方法 (APP)](#编译方法-app)
  - [使用方法](#使用方法)
- [数据保存与日志](#数据保存与日志)
- [版本历史](#版本历史)
- [故障排查](#故障排查)
- [许可证](#许可证)

---

## 项目概述

SVCMonitor 是一套针对 ARM64 平台的 SVC (Supervisor Call) 系统调用监控工具,
由内核模块 (KPM) 和 Android APP 两部分组成:

- **KPM 模块**: 运行在内核层,通过 Hook 系统调用入口,捕获指定进程的 SVC 调用事件
- **Android APP**: 运行在用户层,通过 KernelPatch 的 SuperCall 协议与 KPM 通信,
  提供图形化的包名选择、模式切换、事件查看和数据导出功能

### 核心特性

| 特性 | 说明 |
|------|------|
| **进程级监控** | 按 PID 过滤,只监控指定 APP 的系统调用 |
| **双模式监控** | basic 模式(仅记录调用号) / detail 模式(记录参数和返回值) |
| **实时事件查看** | APP 自动轮询拉取事件,实时展示 |
| **双重日志保存** | 内核侧写入 /data/local/tmp/ 日志文件 + APP 侧 CSV 本地保存 |
| **CSV 导出** | 一键导出到 Downloads/SVCMonitor/ 目录,方便后续分析 |
| **动态 Hook 管理** | 支持运行时添加/删除 syscall hook |
| **环形缓冲区** | 2048 槽位的内核环形缓冲区,零丢失高性能捕获 |
| **符号运行时解析** | 通过 kallsyms_lookup_name 解析所有内核函数,无未导出符号依赖 |

---

## 架构设计

```
┌─────────────────────────────────────────────────┐
│                 Android APP (v4.0)               │
│  ┌───────────┐  ┌────────────┐  ┌─────────────┐ │
│  │ MainActivity│  │MainViewModel│  │ LogExporter │ │
│  │   (UI)     │  │ (Logic)    │  │ (CSV/Export) │ │
│  └─────┬─────┘  └─────┬──────┘  └──────┬──────┘ │
│        │               │                │        │
│  ┌─────┴───────────────┴────────────────┘        │
│  │           KpmBridge (SuperCall)                │
│  └─────────────────┬─────────────────────────────┘
│                    │
│    /data/adb/kpatch <superkey> kpm ctl0 svc-monitor "<cmd>"
│                    │
├────────────────────┼─────────────────────────────┤
│   Kernel Layer     │                              │
│  ┌─────────────────▼──────────────────────┐      │
│  │         KPM Module (svc-monitor)        │      │
│  │  ┌──────────┐  ┌────────────────────┐  │      │
│  │  │ main.c   │  │  hook_engine.c     │  │      │
│  │  │(CTL0调度) │  │  (syscall hooks)   │  │      │
│  │  └────┬─────┘  └────────┬───────────┘  │      │
│  │       │                  │              │      │
│  │  ┌────▼──────────────────▼───────────┐  │      │
│  │  │         event_ring.c              │  │      │
│  │  │    (2048 slot ring buffer)        │  │      │
│  │  └──────────────┬────────────────────┘  │      │
│  │                 │                       │      │
│  │  ┌──────────────▼──────────┐            │      │
│  │  │     file_logger.c       │            │      │
│  │  │ (/data/local/tmp/*.log) │            │      │
│  │  └─────────────────────────┘            │      │
│  │  ┌──────────────┐  ┌─────────────────┐  │      │
│  │  │ symbols.c    │  │response_builder │  │      │
│  │  │(runtime解析)  │  │   (JSON构建)    │  │      │
│  │  └──────────────┘  └─────────────────┘  │      │
│  └─────────────────────────────────────────┘      │
└───────────────────────────────────────────────────┘
```

---

## 目录结构

```
SVCMonitor_v4/
├── README.md                          ← 本文件
├── kpm/                               ← KPM 内核模块
│   ├── Makefile                       ← 编译脚本 (gcc -r 链接)
│   ├── svc-hello.c                    ← 最小测试模块
│   └── src/
│       ├── include/
│       │   └── svc_monitor.h          ← 核心头文件 (类型/宏/函数指针)
│       ├── symbols.c                  ← ★ 运行时符号解析 (kallsyms_lookup_name)
│       ├── main.c                     ← KPM 入口 + CTL0 命令分发
│       ├── hook_engine.c              ← syscall Hook 安装/卸载
│       ├── event_ring.c              ← 环形缓冲区 (2048 slots)
│       ├── file_logger.c             ← 内核侧文件日志
│       ├── pkg_cache.c               ← UID→包名 缓存 (64 条目)
│       └── response_builder.c        ← JSON 响应构建器
│
└── app/                               ← Android APP 项目
    ├── build.gradle.kts               ← 根 Gradle 配置
    ├── settings.gradle.kts
    ├── gradle.properties
    ├── gradle/wrapper/
    │   └── gradle-wrapper.properties
    └── app/
        ├── build.gradle.kts           ← APP Gradle 配置 (v4.0.0)
        ├── proguard-rules.pro
        └── src/main/
            ├── AndroidManifest.xml
            ├── java/com/svcmonitor/app/
            │   ├── core/
            │   │   ├── KpmBridge.kt       ← ★ KPM 通信桥接 (SuperCall)
            │   │   ├── StatusParser.kt    ← JSON 解析 (org.json)
            │   │   ├── AppResolver.kt     ← APP 包名→PID 解析
            │   │   └── LogExporter.kt     ← ★ CSV 日志保存/导出
            │   └── ui/
            │       ├── MainActivity.kt    ← 主界面
            │       └── MainViewModel.kt   ← 业务逻辑 ViewModel
            └── res/
                ├── layout/
                │   └── activity_main.xml  ← 主界面布局
                └── values/
                    ├── colors.xml
                    ├── strings.xml
                    └── themes.xml
```

---

## KPM 模块

### 模块文件说明

| 文件 | 功能 | 关键点 |
|------|------|--------|
| `symbols.c` | 运行时符号解析 | 通过 `kallsyms_lookup_name` 解析 `__kmalloc`, `kfree`, `_raw_spin_lock_irqsave`, `_raw_spin_unlock_irqrestore`, `ktime_get_ns`, `filp_open`, `filp_close`, `kernel_write`, `snprintf` |
| `main.c` | KPM 入口 + CTL0 | 初始化顺序: symbols → config → event_ring → hooks; CTL0 命令调度 |
| `hook_engine.c` | 系统调用 Hook | 使用 KP 导出的 `fp_hook_syscalln` / `inline_hook_syscalln`; 默认 Hook 15 个常用 syscall |
| `event_ring.c` | 环形缓冲区 | 2048 slot, 无锁写入 (atomic), 自旋锁保护读取 |
| `file_logger.c` | 内核文件日志 | 写入 `/data/local/tmp/svc_monitor_*.log`, 管道分隔格式, 4MB 自动轮转 |
| `pkg_cache.c` | 包名缓存 | UID→包名映射, 最多 64 条目 |
| `response_builder.c` | JSON 构建 | 构建 status/events/hooks/help 的 JSON 响应 |
| `svc_monitor.h` | 核心头文件 | 所有函数指针类型定义, `svc_alloc`/`svc_free` 宏, `svc_spinlock_t`, 配置结构体 |

### 编译方法

**前置条件:**

```bash
# 1. 安装 aarch64 交叉编译工具链
sudo apt install gcc-aarch64-linux-gnu

# 2. 克隆 KernelPatch 源码 (用于头文件)
git clone https://github.com/bmax121/KernelPatch.git
```

**编译:**

```bash
cd SVCMonitor_v4/kpm

# 设置 KernelPatch 源码路径
export KP_DIR=/path/to/KernelPatch

# 设置交叉编译器前缀
export TARGET_COMPILE=aarch64-linux-gnu-

# 编译
make

# 验证产物
make verify
```

**验证输出示例:**

```
=== ELF Header ===
  Class:                             ELF64
  Type:                              REL (Relocatable file)
  Machine:                           AArch64

=== KPM Sections ===
  .kpm.name  .kpm.version  .kpm.license  .kpm.author
  .kpm.description  .kpm.init  .kpm.exit  .kpm.ctl0

=== Expected UND Symbols ===
  kallsyms_lookup_name    ← KP 导出 symbol #2
  compat_copy_to_user     ← KP 导出 symbol #30
  fp_hook_syscalln        ← KP hook.c
  fp_unhook_syscalln      ← KP fphook.c
  inline_hook_syscalln    ← KP hook.c
  kf_snprintf             ← KP 导出 symbol #83
  kf_memset / kf_memcpy   ← KP libs.c
  kf_strcmp / kf_strncmp   ← KP libs.c
```

### 安装与加载

```bash
# 1. 将 KPM 文件推送到设备
adb push svc-monitor.kpm /data/local/tmp/

# 2. 加载模块 (需要 SuperKey)
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm load /data/local/tmp/svc-monitor.kpm'

# 3. 验证加载成功
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm list'
# 应看到: svc-monitor  3.1.0  running

# 4. 测试 CTL0 通信
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm ctl0 svc-monitor "status"'
# 应返回 JSON: {"running":0,"mode":"basic",...}

# 5. 卸载模块 (如需)
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm unload svc-monitor'
```

### CTL0 命令接口

通过 `kpatch <KEY> kpm ctl0 svc-monitor "<command>"` 与模块通信:

| 命令 | 说明 | 示例响应 |
|------|------|----------|
| `start` | 开始监控 | `{"ok":"monitoring started"}` |
| `stop` | 停止监控 | `{"ok":"monitoring stopped"}` |
| `status` | 查询状态 | `{"running":1,"mode":"basic","hooks":15,"pids":2,...}` |
| `pid add <pid>` | 添加监控 PID | `{"ok":"pid added"}` |
| `pid del <pid>` | 删除监控 PID | `{"ok":"pid removed"}` |
| `pid clear` | 清除所有 PID | `{"ok":"all pids cleared"}` |
| `pkg add <uid> <name>` | 注册包名映射 | `{"ok":"package registered"}` |
| `mode basic` | 切换为基础模式 | `{"ok":"mode set to basic"}` |
| `mode detail` | 切换为详细模式 | `{"ok":"mode set to detail"}` |
| `events [count]` | 获取事件 (默认50) | `[{"ts":...,"pid":...,"nr":...},...]` |
| `hooks list` | 列出已安装 Hook | `[{"nr":56,"name":"openat","nargs":4},...]` |
| `hooks add <nr> [nargs]` | 安装 syscall hook | `{"ok":"hook installed"}` |
| `hooks del <nr>` | 删除 syscall hook | `{"ok":"hook removed"}` |
| `log on` | 开启文件日志 | `{"ok":"file logging enabled"}` |
| `log off` | 关闭文件日志 | `{"ok":"file logging disabled"}` |
| `help` | 显示帮助 | 命令列表 JSON |

### 技术细节

#### 符号解析策略

KernelPatch v0.10.7 导出 103 个符号,但部分常用内核函数 **未导出**:
- `kf__raw_spin_lock_irqsave` / `kf__raw_spin_unlock_irqrestore` — 未导出
- `tlsf_malloc` / `tlsf_free` — 未导出 (KP 内部内存分配器)
- `kp_rw_mem` — 未导出
- `symbol_lookup_name` — 未导出

**解决方案** (`symbols.c`):
利用 KP 导出的 `kallsyms_lookup_name` (symbol #2) 在模块初始化时
解析所有需要的内核函数地址,存入全局函数指针:

```c
// symbols.c 核心逻辑
extern unsigned long kallsyms_lookup_name(const char *name);

void *(*k_kmalloc)(unsigned long size, unsigned int flags) = 0;
void  (*k_kfree)(const void *ptr) = 0;
// ... 更多函数指针

int svc_resolve_symbols(void) {
    k_kmalloc = (void *)kallsyms_lookup_name("__kmalloc");
    k_kfree   = (void *)kallsyms_lookup_name("kfree");
    // ... 逐个解析
    return 0; // 或 -1 如果关键符号解析失败
}
```

内存分配使用宏封装:
```c
#define svc_alloc(size)  (k_kmalloc ? k_kmalloc(size, 0xCC0) : (void*)0)
#define svc_free(ptr)    do { if (k_kfree && ptr) k_kfree(ptr); } while(0)
```

#### 默认 Hook 的系统调用

模块加载时自动 Hook 15 个常用 ARM64 系统调用:

| NR | 系统调用 | 参数数 | 用途 |
|----|---------|--------|------|
| 56 | openat | 4 | 文件打开 |
| 57 | close | 1 | 文件关闭 |
| 63 | read | 3 | 读取数据 |
| 64 | write | 3 | 写入数据 |
| 48 | faccessat | 3 | 权限检查 |
| 78 | readlinkat | 4 | 读取链接 |
| 160 | uname | 1 | 系统信息 |
| 172 | getpid | 0 | 获取 PID |
| 198 | socket | 3 | 创建 socket |
| 203 | connect | 3 | 网络连接 |
| 206 | sendto | 6 | 发送数据 |
| 207 | recvfrom | 6 | 接收数据 |
| 220 | clone | 5 | 创建进程 |
| 221 | execve | 3 | 执行程序 |
| 261 | prlimit64 | 4 | 资源限制 |

---

## Android APP

### 功能说明

| 功能模块 | 说明 |
|----------|------|
| **SuperKey 连接** | 4 步验证: 定位 kpatch → 验证 Key → 检查模块 → 测试通信 |
| **包名选择** | 列出设备已安装 APP,自动解析 PID,添加到监控列表 |
| **模式切换** | basic (基础) / detail (详细) 两种监控模式 |
| **实时事件** | 自动 2s 轮询,展示 SVC 调用事件 (syscall 名称、PID、时间戳) |
| **CSV 导出** | 事件保存为 CSV 文件,一键导出到 Downloads/SVCMonitor/ |
| **KPM 日志拉取** | 从设备 /data/local/tmp/ 拉取内核侧日志 |
| **Hook 管理** | 查看已安装 Hook,动态添加/删除 |

### APP 核心文件说明

| 文件 | 功能 |
|------|------|
| `KpmBridge.kt` | KPM 通信核心 — 执行 shell 命令调用 kpatch, 全路径 `/data/adb/kpatch` |
| `StatusParser.kt` | JSON 解析 — 使用 Android 内置 `org.json`, 兼容纯数组和包装格式 |
| `AppResolver.kt` | 包名→PID — 使用 `pidof` / `ps -e` 获取进程 ID |
| `LogExporter.kt` | 日志管理 — CSV 本地保存 + Downloads 导出 + KPM 日志拉取 |
| `MainViewModel.kt` | 业务逻辑 — 连接、监控、事件获取、日志导出的完整流程 |
| `MainActivity.kt` | 主界面 — Material Design, 5 个功能卡片 |

### 编译方法 (APP)

**前置条件:**

- Android Studio (Arctic Fox 或更新版本)
- SDK 33+ (compileSdk)
- minSdk 26 (Android 8.0+)

**编译步骤:**

```bash
cd SVCMonitor_v4/app

# 用 Android Studio 打开项目
# 或使用命令行:
./gradlew assembleDebug

# 输出 APK: app/build/outputs/apk/debug/app-debug.apk
```

**无外部依赖**:
- 使用 Android 内置 `org.json` (无需 Gson)
- 使用标准 AndroidX 库 (Material, Lifecycle, Coroutines)
- 使用 `Runtime.exec()` 执行 root shell 命令 (无需 libsu)

### 使用方法

#### 1. 安装与首次连接

```
1. 确保 KernelPatch 已安装且模块已加载
2. 安装 APK: adb install svc-monitor-v4.apk
3. 打开 APP
4. 在 SuperKey 输入框中输入你的 KernelPatch SuperKey
5. 点击 "Connect"
6. 连接成功后会显示 "Connected (KP: x.x.x)"
```

#### 2. 选择监控目标

```
1. 在 "Package Filter" 区域,系统会列出已安装的 APP
2. 选择要监控的 APP (如: com.example.app)
3. APP 自动使用 pidof 获取进程 PID
4. 点击 "Add" 将 PID 加入监控列表
5. 如果目标 APP 重启,点击 "Refresh PIDs" 更新
```

#### 3. 开始监控

```
1. 选择监控模式: Basic (仅 syscall 号) 或 Detail (含参数)
2. 点击 "Start" 开始监控
3. Events 区域会实时显示捕获的 SVC 调用
4. 点击 "Stop" 停止监控
```

#### 4. 日志与导出

```
1. 点击 "Export CSV" — 将本地缓存的事件导出为 CSV 文件到 Downloads/SVCMonitor/
2. 点击 "Pull KPM Log" — 从设备拉取内核侧日志
3. 点击 "Clear Logs" — 清除 APP 本地日志缓存
```

---

## 数据保存与日志

SVCMonitor 提供 **双重日志保存机制**:

### 1. 内核侧日志 (file_logger.c)

- **路径**: `/data/local/tmp/svc_monitor_0.log`, `..._1.log`, `..._2.log`, ...
- **格式**: 管道 `|` 分隔的文本
- **字段**: `timestamp|pid|uid|syscall_nr|syscall_name|arg0|arg1|...|ret`
- **自动轮转**: 单文件 4MB 上限,自动创建新文件
- **启用方式**: CTL0 命令 `log on` 或 APP 中启用

```
# 示例日志行 (管道分隔):
1709012345678|1234|10150|56|openat|0xFFFFFF9C|0x7FFC8A00|0x0|0x0|0x0|0x0|3
1709012345679|1234|10150|63|read|3|0x7FFC8000|4096|0x0|0x0|0x0|1024
```

### 2. APP 侧 CSV 日志 (LogExporter.kt)

- **本地路径**: `/data/data/com.svcmonitor.app/files/svc_logs/events_YYYYMMDD.csv`
- **导出路径**: `/sdcard/Downloads/SVCMonitor/svc_events_export_YYYYMMDD_HHmmss.csv`
- **格式**: 标准 CSV (逗号分隔)
- **字段**: `timestamp,pid,uid,syscall_nr,syscall_name,args,return_value`
- **自动轮转**: 单文件 10MB 上限

```csv
# 导出的 CSV 示例:
timestamp,pid,uid,syscall_nr,syscall_name,args,return_value
1709012345678,1234,10150,56,openat,"0xFFFFFF9C,0x7FFC8A00,0x0,0x0",3
1709012345679,1234,10150,63,read,"3,0x7FFC8000,4096",1024
```

---

## 版本历史

| 版本 | 变更 |
|------|------|
| **v4.0** | 完全重写 APP — 修复连接失败, 添加 CSV 日志导出, 移除 Gson 依赖 |
| **v3.1** | 修复 6 个未导出符号错误 — 添加 `symbols.c` 运行时解析 |
| **v3.0** | 修复 Makefile (gcc -r), ELF 格式正确, 模块首次成功加载 |
| **v2.1** | 修复 API 调用错误 |
| **v2.0** | 首个完整版本 (KPM + APP) |
| **v1.0** | 原型版本 |

### v4.0 修复的 APP 问题

1. **kpatch 路径错误**: 旧代码使用 `kpatch` (裸命令), 改为 `/data/adb/kpatch` (完整路径)
2. **SuperKey 验证错误**: 旧代码 `kpatch <key> hello` (无效命令), 改为 `kpatch <key> -v`
3. **模块检测错误**: 旧代码发送 "help" 检测, 改为 `kpatch <key> kpm list` 检查 "svc-monitor"
4. **JSON 格式不匹配**: 旧代码只支持 `{"events":[...]}`, 新代码同时支持纯数组 `[...]`
5. **移除 Gson 依赖**: 全部改用 Android 内置 `org.json`
6. **新增 CSV 导出**: `LogExporter.kt` 支持本地保存和导出到 Downloads

---

## 故障排查

### 模块加载失败

```bash
# 检查 kpatch 是否存在
adb shell ls -la /data/adb/kpatch

# 检查 KP 版本
adb shell su -c '/data/adb/kpatch <KEY> -v'

# 验证 KPM 文件格式
readelf -h svc-monitor.kpm
# 必须是: Type = REL, Machine = AArch64

# 检查 KPM sections
readelf -S svc-monitor.kpm | grep kpm
# 必须有: .kpm.init .kpm.exit .kpm.ctl0 .kpm.name 等

# 查看内核日志
adb shell dmesg | grep -i "svc\|kpm\|kpatch"
```

### APP 连接失败

```bash
# 1. 确认 kpatch 路径
adb shell which kpatch
adb shell ls /data/adb/kpatch

# 2. 确认 SuperKey 正确
adb shell su -c '/data/adb/kpatch <KEY> -v'
# 应返回版本号如: 0.10.7

# 3. 确认模块已加载
adb shell su -c '/data/adb/kpatch <KEY> kpm list'
# 应看到: svc-monitor

# 4. 手动测试 CTL0
adb shell su -c '/data/adb/kpatch <KEY> kpm ctl0 svc-monitor "status"'
# 应返回 JSON 状态

# 5. 确认 APP 有 root 权限
# 某些 root 方案需要在 root 管理器中授权 APP
```

### 常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| "kpatch not found" | kpatch 二进制不在预期路径 | 确认 KernelPatch 已正确安装 |
| "Invalid SuperKey" | SuperKey 错误 | 检查输入的 SuperKey 是否正确 |
| "Module not loaded" | KPM 未加载 | 执行 `kpatch <key> kpm load /path/to/svc-monitor.kpm` |
| "Module not responding" | CTL0 通信失败 | 查看 dmesg 日志, 可能模块初始化失败 |
| "unknown symbol" (dmesg) | KP 版本不匹配 | 确认使用 KP v0.10.7, 运行 `make verify` 检查 |
| No events after start | 未添加 PID 或目标进程无活动 | 确认已添加正确的 PID, 操作目标 APP |

---

## 许可证

GPL v2 — 与 KernelPatch 保持一致

---

*SVCMonitor v4.0 — ARM64 SVC System Call Monitor*
*适用于安全研究和隐私分析*

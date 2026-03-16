# SVC Call / SVC Monitor

**ARM64 系统调用监控与逆向分析工具链** — KernelPatch Module (KPM) + Android App + PC Web Viewer

本仓库当前的主架构是：
- Android App 负责采集/落库/解析（TID、调用链、fd/path、maps 等），并作为事件服务端对外推送增量事件
- PC Viewer 作为客户端连接手机事件服务端，并提供 WebSocket-only 的 Web 分析界面（搜索、线程追踪、maps 可视化、符号解析等）

---

## 目录

- [架构与数据流](#架构与数据流)
- [快速开始（推荐链路）](#快速开始推荐链路)
- [功能清单](#功能清单)
- [目录结构](#目录结构)
- [事件字段约定](#事件字段约定)
- [故障排查](#故障排查)

---

## 架构与数据流

```
┌──────────────┐          ┌─────────────────────────┐
│  Kernel (KPM) │          │      PC (Python)        │
│  svc_monitor  │          │   SVC_PC_View/server     │
│  syscall hook │          │  - connect app-socket    │
└──────┬────────┘          │  - in-mem store + index  │
       │                   │  - WebSocket-only UI     │
       │ SuperCall ctl0    └───────┬─────────────────┘
       ▼                           │ Socket.IO (websocket only)
┌─────────────────────────┐        ▼
│      Android App         │   ┌──────────────┐
│  android/app (Kotlin)    │   │   Browser    │
│  - KpmBridge ctl0        │   │  Web UI SPA  │
│  - parse + Room DB       │   │  search/tid  │
│  - fp_chain + maps       │   │  maps/symbol │
│  - ServerSocket:8080     │   └──────────────┘
└──────────┬──────────────┘
           │ ADB forward tcp:8080 -> tcp:8080
           ▼
      PC 本机 127.0.0.1:8080
```

说明：
- Web UI 与 PC Server 的交互强制使用 WebSocket（Socket.IO websocket transport），不做定时 AJAX 轮询拉事件。
- App 服务端推送的是 **增量事件**（支持 HELLO last_seq），PC 端会持久化 last_seq，避免断线重连重复回放历史。

---

## 快速开始（推荐链路）

### 依赖

- Android 设备：已安装 KernelPatch / kpatch（需要 root 权限）
- PC：`adb`、`python3`
- PC Viewer Python 依赖（必须包含 eventlet 才能 WebSocket-only）：

```bash
pip install -r SVC_PC_View/requirements.txt
```

### 1）安装/运行 Android App

```bash
cd android
/tmp/gradle-8.1.1/bin/gradle :app:assembleDebug
```

安装 APK：

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

打开 App 后：
- 配置 SuperKey（用于 kpatch supercall）
- 一键启用监控（或选择预设/NR）
- 打开 “App 服务端 (PC 用 adb forward 连接)”（默认端口 8080）

### 2）启动 PC Viewer（一条命令）

```bash
bash SVC_PC_View/run_app_socket.sh 8080 0
```

- `8080`：App ServerSocket 端口
- `0`：Web UI 自动选择空闲端口（避免 5000/5001 被占用）

终端会打印 Web UI 地址，例如 `http://localhost:5001`。

---

## 功能清单

- **事件流**：App -> PC -> Web 全链路增量推送
- **全量搜索/过滤**：关键字、PID(TGID)、TID、NR、进程名等
- **线程对话式追踪**：左侧 TID 列表，勾选后仅展示该线程执行流
- **颜色标签**：PROC / NET / FILE / ENV syscall 分类高亮
- **线程分析**：线程统计与线程树（clone/clone3）
- **Maps Analyzer**：
  - 解析 `/proc/<pid>/maps` 为结构化 mapping
  - 地址空间可视化（按权限 r/w/x 着色）
  - 可疑区域高亮（匿名 RWX、非标准 so 路径）
  - 支持路径/权限/地址过滤
  - 与 mmap/mprotect 事件联动定位
- **Backtrace 符号解析**：
  - maps 级别：`0xADDR -> libfoo.so + 0xOFF`
  - 可选：上传 so/symbol 后用 `addr2line/llvm-addr2line` 解析函数名
- **导出**：JSONL/CSV（下载/上传属于文件传输接口，不是事件轮询）

---

## 目录结构

```
SVC_Call/
├── README.md
├── kpm/                       # KernelPatch Module: svc_monitor
├── android/                   # Android App (Kotlin)
└── SVC_PC_View/               # PC Web Viewer (Python + SPA)
    ├── requirements.txt
    ├── run_app_socket.sh      # 一键启动：adb forward + server/app.py
    ├── server/app.py          # Flask-SocketIO 后端（WebSocket-only + app-socket client）
    └── static/index.html      # Web 前端（Vue3 + ECharts + Socket.IO）
```

---

## 事件字段约定

为支持线程分析与跨端一致性，事件里关键字段含义如下：

- `tgid`：进程号（线程组号）
- `pid`：线程号（TID）
- PC Viewer 会做兼容归一化：`pid <- tgid`，`tid <- pid`，避免旧界面只识别 pid 的问题
- `fp_chain`：解析后的 FP 调用链（含 so 名与偏移），会用于搜索与详情展示

---

## 故障排查

- Web UI 打不开或提示 transport=polling：
  - 确认安装了 `eventlet`：`pip install -r SVC_PC_View/requirements.txt`
- Web UI 端口占用：
  - 使用脚本的第二个参数 `0` 自动选端口：`run_app_socket.sh 8080 0`
- App 服务端连不上：
  - 确认 USB 连接、`adb devices` 正常
  - 确认执行了 `adb forward tcp:8080 tcp:8080`（脚本会自动做）
- maps 读取失败：
  - 需要 root；PC 端会尝试 `adb shell cat`，失败会 fallback `adb shell su -c cat`
- so 函数名解析失败：
  - 需要上传带符号的 so/symbol 文件，并且 PC 需要 `llvm-addr2line` 或 `addr2line`

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
cd kpm

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
adb push kpm/svc_monitor.kpm /data/local/tmp/

# 2. 加载模块 (需要 SuperKey)
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm load /data/local/tmp/svc_monitor.kpm'

# 3. 验证加载成功
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm list'
# 应看到: svc_monitor  ...  running

# 4. 测试 CTL0 通信
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm ctl0 svc_monitor "status"'

# 5. 卸载模块 (如需)
adb shell su -c '/data/adb/kpatch <YOUR_SUPERKEY> kpm unload svc_monitor'
```

### CTL0 命令接口

通过 `kpatch <KEY> kpm ctl0 svc_monitor "<command>"` 与模块通信（App 内部同样使用这套接口）:

| 命令 | 说明 | 示例响应 |
|------|------|----------|
| `enable` | 开始监控（开启回调） | `{"ok":true,...}` |
| `disable` | 停止监控（关闭回调） | `{"ok":true,...}` |
| `status` | 查询状态/已选 NR/UID 等 | `{"enabled":1,"nr_count":...}` |
| `uid <uid>` | 设置目标 UID（-1 表示全部） | `{"ok":true,...}` |
| `preset <name>` | 应用预设规则集 | `{"ok":true,...}` |
| `set_nrs 56,57,...` | 设置 NR 列表 | `{"ok":true,...}` |
| `enable_nr <nr>` | 单独开启某个 NR | `{"ok":true,...}` |
| `disable_nr <nr>` | 单独关闭某个 NR | `{"ok":true,...}` |
| `enable_all` | 开启全部已 hook NR | `{"ok":true,...}` |
| `disable_all` | 关闭全部已 hook NR | `{"ok":true,...}` |
| `tier2 on/off` | Tier2 扩展开关 | `{"ok":true,...}` |
| `clear` | 清空内核事件缓存 | `{"ok":true,...}` |
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

## Android App

Android App 位于 [android](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/android)：

- 通过 `KpmBridge` 控制 KPM：`/data/adb/kpatch <KEY> kpm ctl0 svc_monitor "<cmd>"`
- 负责事件采集与解析：`tgid/pid(tid)`、`fp_chain`、线程树、fd/path、maps 等
- 事件落库（Room），并提供 App 服务端（ServerSocket，默认 8080）供 PC Viewer 连接

关键文件：
- [KpmBridge.kt](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/android/app/src/main/java/com/svcmonitor/app/KpmBridge.kt)
- [MainViewModel.kt](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/android/app/src/main/java/com/svcmonitor/app/MainViewModel.kt)
- [MainActivity.kt](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/android/app/src/main/java/com/svcmonitor/app/MainActivity.kt)

---

## PC Web Viewer

PC Viewer 位于 [SVC_PC_View](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/SVC_PC_View)：

- Python 后端：`server/app.py`（WebSocket-only + app-socket client + in-memory 索引）
- Web 前端：`static/index.html`（搜索、线程追踪、Maps Analyzer、符号解析）

更完整的 PC Viewer 使用说明见：
- [SVC_PC_View/README.md](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/SVC_PC_View/README.md)

---

## 许可证

GPL v2（与 KernelPatch 生态保持一致）

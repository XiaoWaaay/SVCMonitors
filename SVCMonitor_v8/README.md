# SVC Monitor v8.0

ARM64 内核模块 + Android 控制 APP，用于实时监控系统调用。

## 核心架构

- **KPM 模块**：加载即 hook 44 个 Tier1 系统调用，始终运行
- **模块启动时 g_enabled=0**：等待 APP 发送 `enable` 命令才开始记录
- **APP 角色**：选择目标 APP → 选择预设 → 点"一键启用监控"
- **过滤机制**：Bitmap NR 过滤 + UID 过滤，lock-free，~15ns 快速拒绝

## 使用流程

```
1. 编译并加载 KPM 模块 (钩子自动安装，但监控暂停)
2. 安装 APP
3. 在 APP 中选择目标 APP (获取 UID)
4. 选择监控预设 (逆向基础/文件/网络/...)
5. 点击"一键启用监控" → APP 发送 uid + preset + enable
6. 事件流实时显示
7. 点击"停止监控" → 发送 disable
```

## 仅使用官方 KPM API

```c
// Hook (用哪个成功用哪个):
inline_hook_syscalln(nr, narg, before, after, udata);   // 首选
fp_hook_syscalln(nr, narg, before, after, udata);        // 备选

// Unhook:
inline_unhook_syscalln(nr, before, after);
fp_unhook_syscalln(nr, before, after);

// 读取系统调用参数 (自动处理 syscall_wrapper):
syscall_argn(fargs, n);

// 获取当前 UID:
current_uid();           // from kputils.h

// 用户空间数据操作:
compat_strncpy_from_user(dst, src, count);
compat_copy_to_user(to, from, n);

// 文件输出 (通过 raw_syscall):
raw_syscall4(__NR_openat, ...);
raw_syscall3(__NR_write, ...);
raw_syscall1(__NR_close, ...);

// CTL0 签名:
static long svc_ctl0(const char *args, char *__user out_msg, int outlen);
```

## 编译 KPM

```bash
cd kpm
export KP_DIR=/path/to/KernelPatch
export CROSS_COMPILE=aarch64-linux-gnu-
make
```

## 安装 KPM

```bash
adb push svc_monitor.kpm /data/local/tmp/
adb shell su -c /data/adb/ap/bin/kpatch XiaoLu0129 kpm load /data/local/tmp/svc_monitor.kpm
```

## CTL0 命令

| 命令 | 说明 |
|------|------|
| `status` | 获取模块状态 JSON |
| `enable` / `resume` / `start` | 开启监控 |
| `disable` / `pause` / `stop` | 暂停监控 |
| `uid <n>` | 设置目标 UID (-1=全部) |
| `enable_nr <n>` | 启用某个 NR |
| `disable_nr <n>` | 禁用某个 NR |
| `set_nrs <n1>,<n2>,...` | 批量设置 NR |
| `enable_all` | 启用所有已 hook 的 NR |
| `disable_all` | 禁用所有 NR |
| `preset <name>` | 应用预设 |
| `tier2 on/off` | 加载/卸载 Tier2 |
| `drain <max>` | 获取事件 |
| `clear` | 清空事件 |

## 预设

| 名称 | 说明 |
|------|------|
| re_basic | 逆向基础 (openat/read/write/mmap/mprotect/prctl/ptrace/clone/execve/connect) |
| re_full | 逆向完整 (re_basic + close/memory/bpf/socket/exit 等) |
| file | 文件操作 |
| net | 网络通信 |
| proc | 进程管理 |
| mem | 内存管理 |
| security | 安全审计 |
| all | 全部启用 |

## 文件结构

```
SVCMonitor_v8/
├── kpm/
│   ├── Makefile
│   └── src/svc_monitor.c         # KPM 内核模块
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       └── java/com/svcmonitor/app/
│           ├── MainActivity.kt    # 主界面 (一键监控)
│           ├── MainViewModel.kt   # 业务逻辑
│           ├── KpmBridge.kt       # KPM 通信
│           ├── StatusParser.kt    # JSON 解析
│           ├── AppResolver.kt     # APP 列表
│           └── LogExporter.kt     # 日志导出
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## 目标设备

- Google Pixel 6 (oriole), Android 12, kernel 5.10.43
- APatch (KernelPatch)
- SuperKey: XiaoLu0129

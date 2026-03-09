# SVCMonitor v3.1 — SVC System Call Monitor

## v3.1 修复内容

### 根因分析

v3.0 在加载时报 6 个 `unknown symbol` 错误：

| 未导出符号 | 来源 | 原因 |
|---|---|---|
| `kf__raw_spin_lock_irqsave` | `spin_lock_irqsave()` 宏 | `misc.c` 中定义但**未** `KP_EXPORT_SYMBOL` |
| `kf__raw_spin_unlock_irqrestore` | `spin_unlock_irqrestore()` 宏 | 同上 |
| `symbol_lookup_name` | KP 内部符号查找 | `symbol.c` 中定义但**未导出** |
| `tlsf_malloc` | `kp_malloc()` 内联函数 | TLSF 分配器**未导出** |
| `tlsf_free` | `kp_free()` 内联函数 | 同上 |
| `kp_rw_mem` | `kp_malloc()`/`compat_copy_to_user()` | TLSF 内存池变量**未导出** |

**根本原因**: KernelPatch v0.10.7 通过 `KP_EXPORT_SYMBOL` 导出了 103 个符号到 `.kp.symbol` 段。上述 6 个符号虽然存在于 KP 内部，但**不在**导出列表中。KPM 模块加载器 (`module.c` 的 `simplify_symbols()`) 只能解析 `.kp.symbol` 段中的符号。

### 修复方案

**核心思路**: 使用 `kallsyms_lookup_name`（导出符号 #2）在模块初始化时动态解析所有需要的内核函数，完全绕开 KP 的 `kf_*` 中间层。

| 原来的用法 | v3.1 替代方案 | 说明 |
|---|---|---|
| `kp_malloc(size)` → `tlsf_malloc` | `svc_alloc(size)` → `__kmalloc` | 通过 kallsyms 解析内核原生 kmalloc |
| `kp_free(ptr)` → `tlsf_free` | `svc_free(ptr)` → `kfree` | 通过 kallsyms 解析内核原生 kfree |
| `spin_lock_irqsave()` → `kf__raw_spin_*` | `svc_spin_lock_irqsave()` | 通过 kallsyms 解析 `_raw_spin_lock_irqsave` |
| `kallsyms_lookup_name()` | 直接使用（已导出） | KP 导出符号 #2 |
| `compat_copy_to_user()` | 直接使用（已导出） | KP 导出符号 #30 |
| `snprintf()` → `kf_snprintf` | 直接使用 `kf_snprintf` | KP 导出符号 #83 |

### 新增文件

- `src/symbols.c` — 符号解析器，在 `KPM_INIT` 最开始调用，解析所有需要的内核函数

### 符号依赖检查

编译后运行 `make verify`，应该看到：
```
OK: No kf__raw_spin_* references
OK: No tlsf_* references
OK: No kp_rw_mem references
OK: No symbol_lookup_name references
```

## 构建

```bash
# 1. 克隆 KernelPatch
git clone https://github.com/bmax121/KernelPatch.git

# 2. 设置环境变量
export KP_DIR=/path/to/KernelPatch
export TARGET_COMPILE=aarch64-linux-gnu-

# 3. 构建主模块
cd kpm
make

# 4. 验证符号 (重要!)
make verify

# 5. 构建最小测试模块 (可选，用于排除问题)
make hello
```

## 测试流程

### 第一步：先测试 svc-hello (最小模块)

```bash
# 推送到手机
adb push svc-hello.kpm /data/local/tmp/

# 加载
adb shell su -c '/data/adb/kpatch <SUPERKEY> kpm load /data/local/tmp/svc-hello.kpm'

# 检查日志
adb shell dmesg | grep "svc-hello"
# 应该看到: svc-hello: init OK! v3.1

# 测试 CTL0
adb shell su -c '/data/adb/kpatch <SUPERKEY> kpm ctl0 svc-hello "ping"'

# 卸载
adb shell su -c '/data/adb/kpatch <SUPERKEY> kpm unload svc-hello'
```

### 第二步：测试 svc-monitor (完整模块)

```bash
# 推送
adb push svc-monitor.kpm /data/local/tmp/

# 加载
adb shell su -c '/data/adb/kpatch <SUPERKEY> kpm load /data/local/tmp/svc-monitor.kpm'

# 检查日志 — 应该看到符号解析信息
adb shell dmesg | tail -50
# 应该看到:
#   SVC-Mon: resolving kernel symbols via kallsyms_lookup_name...
#   SVC-Mon: resolved __kmalloc -> 0x...
#   SVC-Mon: resolved kfree -> 0x...
#   SVC-Mon: resolved _raw_spin_lock_irqsave -> 0x...
#   ...
#   SVC-Mon: === init complete, hooks=15 ===

# 测试命令
adb shell su -c '/data/adb/kpatch <SUPERKEY> kpm ctl0 svc-monitor "status"'
adb shell su -c '/data/adb/kpatch <SUPERKEY> kpm ctl0 svc-monitor "help"'
adb shell su -c '/data/adb/kpatch <SUPERKEY> kpm ctl0 svc-monitor "start"'
```

## KP v0.10.7 导出符号参考

本模块使用的 KP 导出符号（全部在 103 个导出列表中）：

| # | 符号 | 来源 | 用途 |
|---|---|---|---|
| 2 | `kallsyms_lookup_name` | start.c | 解析所有内核函数 |
| 4 | `printk` | start.c | 日志输出 |
| 26 | `sys_call_table` | syscall.c | 系统调用表 |
| 28 | `has_syscall_wrapper` | syscall.c | 参数提取方式 |
| 30 | `compat_copy_to_user` | utils.c | CTL0 返回数据 |
| 9-21 | `hook_*`, `fp_hook_*` | hook.c/fphook.c | syscall hook |
| 64-68 | `kf_memset`, `kf_memcpy` | libs.c | 内存操作 |
| 46-47 | `kf_strcmp`, `kf_strncmp` | libs.c | 字符串比较 |
| 83 | `kf_snprintf` | libs.c | 格式化输出 |

## 项目结构

```
SVCMonitor_v3.1/
├── kpm/                          # KPM 内核模块
│   ├── Makefile                  # 构建系统
│   ├── svc-hello.c               # 最小测试模块
│   └── src/
│       ├── include/
│       │   └── svc_monitor.h     # 核心头文件 (自定义类型, 不依赖未导出符号)
│       ├── symbols.c             # [NEW] 符号解析器
│       ├── main.c                # KPM 入口 + CTL0 命令处理
│       ├── hook_engine.c         # Syscall hook 引擎
│       ├── event_ring.c          # 事件环形缓冲区
│       ├── file_logger.c         # 文件日志
│       ├── pkg_cache.c           # UID→包名缓存
│       └── response_builder.c    # JSON 响应构建器
└── app/                          # Android APP (与 v3.0 相同)
```

## 版本历史

| 版本 | 问题 | 修复 |
|---|---|---|
| v2.0 | 模块无法加载 (ELF格式错误) | - |
| v2.1 | hook API 调用方式错误 | 修正 fp_hook_syscalln 返回值类型 |
| v3.0 | Makefile 根本性错误 | 完全重写构建系统, 匹配官方 demo |
| **v3.1** | **6个未导出符号错误** | **通过 kallsyms_lookup_name 运行时解析** |

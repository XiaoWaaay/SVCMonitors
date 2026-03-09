# SVC_Call → svc_tracer（KPM 版）改造计划

## 目标

- 将当前 `src/` 下以“隐藏/反调试绕过”为主的 KPM 模块，改造成“**SVC(系统调用) 监控**”模块。
- 代码组织与 [开发文档.md](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/开发文档.md#L133-L149) 的模块划分保持一致（`symbol_resolver / hook_engine / syscall_monitor / caller_resolver / event_logger` 等），但 **不依赖系统源码树**，只使用本仓库 `KernelPatch/` 自带的头文件与导出能力。
- 只实现“监控/记录/导出”链路：按 PID/进程名过滤、syscall 分类、关键参数解析、可选反调试行为标记、可选 caller 信息（PC/LR/简易 backtrace）。

## 现状评估（基于仓库现有代码）

- 当前 `src/main.c` 已经是 KPM 结构（`KPM_NAME/KPM_INIT/KPM_CTL0/KPM_EXIT`），构建产物为 `module.kpm`（见 [Makefile](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/Makefile#L31-L55)）。
- 当前模块功能以隐藏/绕过为主（`hide_* / hwbp / 写文件并临时放开 SELinux` 等），与“监控”目标冲突，应替换为新模块逻辑。
- 仓库内已包含 KernelPatch 的 syscall hook 能力与示例（见 [demo-syscallhook](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/KernelPatch/kpms/demo-syscallhook/syscallhook.c#L73-L130)），可直接复用 `fp_hook_syscalln/inline_hook_syscalln` 与 `syscall_argn()`。

## 关键设计选择（对齐文档，但适配 KPM）

1. **Hook 方式**
   - 文档示例是替换 `sys_call_table`（自建 `install_syscall_hook/remove_syscall_hook`）。
   - KPM 版优先用 KernelPatch 已封装的 syscall hook：`fp_hook_syscalln()`（函数指针链）作为默认，必要时支持 `inline_hook_syscalln()` 作为备选。
   - 这仍然等价于“监控 SVC 触发的 syscall”，并且不需要拉取内核源码。

2. **用户态接口**
   - 文档示例通过 `/proc/svc_tracer/*` 输出与控制；但当前 KernelPatch 头文件环境不包含 `procfs` 相关定义，直接实现 `/proc` 风险较大。
   - KPM 版使用 `KPM_CTL0` 作为控制与导出入口：用命令字符串配置监控、并以文本形式返回日志（在 `outlen` 限制内分批输出）。

3. **内存/性能**
   - 文档的事件结构较大且默认 buffer 容量高；考虑你机器内存紧张，KPM 版默认：
     - Ring buffer 以“事件条目数”为容量（例如 256 或 512，可编译期宏调整）。
     - 默认关闭 backtrace 与 retval 记录，仅在需要时开启。

## 代码改造范围（文件级别）

### 1) 新增/替换的头文件

- `src/include/svc_tracer.h`
  - 按文档 [开发文档.md](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/开发文档.md#L152-L222) 迁移 `struct svc_event / struct tracer_config / 常量宏`。
  - 适配点：
    - buffer 容量宏改为“条目数”，避免默认占用过大。
    - 将 `MAX_DETAIL_LEN / MAX_BACKTRACE_DEPTH` 保留为可调宏。

### 2) 核心模块拆分（在 `src/` 下新增这些文件）

- `src/event_logger.h/.c`
  - 实现环形缓冲：`init/exit/write/read/pending/dropped`（对齐文档接口）。
  - 分配方式：使用 KernelPatch 的 `kp_malloc/kp_free`（见 [kpmalloc.h](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/KernelPatch/kernel/include/kpmalloc.h)），避免依赖内核 `kmalloc`。
  - 并发保护：用 `spinlock_t`（KernelPatch 已导出相关原语）。

- `src/caller_resolver.h/.c`
  - 最小实现：从 `current_pt_regs()` 抓取 `pc` 与 `x30(lr)`，写入 `evt->caller_pc / evt->caller_lr`（对应文档 [开发文档.md](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/开发文档.md#L540-L549)）。
  - 可选实现：简易用户栈回溯（最多 `MAX_BACKTRACE_DEPTH`），通过用户 `sp` 读取指针序列，失败则降级为空。

- `src/syscall_monitor.h/.c`
  - 提供 `syscall_monitor_init/exit`（对齐文档接口）。
  - 实现内容参考文档 [系统调用监控模块](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/开发文档.md#L551-L1096)：
    - `should_monitor()`：按 `g_config.enabled / target_pids / target_comm` 过滤。
    - `get_syscall_category()`：按 `__NR_*` 分类。
    - `parse_args()`：对关键 syscall 做参数解析（openat/execve/connect/socket/ptrace/prctl/rt_sigaction/read/write 等）。
    - `is_antidebug_behavior()`：仅做“标记”不做拦截。
  - KPM 适配点：hook 回调使用 KernelPatch 的 `hook_fargsN_t` + `syscall_argn()` + `fargs->ret`，无需自定义 `asmlinkage hooked_*`。

- `src/hook_engine.h/.c`
  - 封装“批量安装/卸载 hook”的逻辑，对外提供：
    - `int hook_engine_install_all(void);`
    - `void hook_engine_remove_all(void);`
    - （可选）`int hook_engine_set_type(enum hook_type type);`
  - 实现中维护一张“syscall hook 表”：syscall nr、narg、after callback、名字、已安装标记。

- `src/symbol_resolver.h/.c`
  - 负责解析可选符号（如 `ktime_get_ns`、必要的内核函数地址）：
    - 通过 `kallsyms_lookup_name()` 查找，失败则提供降级路径。
  - 目的：把“符号依赖”集中管理，便于适配不同 KernelPatch 版本。

### 3) 模块入口（替换 `src/main.c`）

- 更新 KPM 元信息：
  - `KPM_NAME("svc-tracer")`（或 `svc_tracer`，保持唯一即可）
  - 描述改为 syscall/svc 监控用途
- `kpm_init()`：
  - `event_logger_init()`
  - `symbol_resolver_init()`（如果需要）
  - `hook_engine_install_all()`（可根据 args 支持“只初始化不 hook”）
- `kpm_exit()`：
  - `hook_engine_remove_all()`
  - `event_logger_exit()`
- `KPM_CTL0`：
  - 实现控制命令解析与日志导出（见下方“控制协议”）。

### 4) 构建系统调整

- 更新顶层 [Makefile](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/Makefile#L31-L38) 的 `BASE_SRCS` 列表：
  - 移除：`hide_proc.c / hide_debbuger.c / hwbp.c / xjutils.c` 及对应头文件引用
  - 新增：`event_logger.c / caller_resolver.c / syscall_monitor.c / hook_engine.c / symbol_resolver.c`
- 保持输出仍为 `module.kpm`，不引入额外依赖。

## KPM_CTL0 控制协议（建议实现）

- `status`：返回 enabled、过滤条件、pending/dropped 统计
- `enable 0|1`
- `pid add <tgid>` / `pid clear`
- `comm set <name>` / `comm clear`
- `cat set <hexmask>`（例如 `0xff`）
- `bt 0|1`（backtrace 开关）
- `retval 0|1`（是否记录返回值）
- `log read <n>`：读取 n 条事件（文本格式），不足则返回现有
- `log clear`：清空 ring buffer 计数与内容

输出格式建议为单行一条，便于脚本解析，例如：
`ts=... tgid=... comm=... nr=__NR_openat ret=... pc=... lr=... detail="..."`

## 验证方式（实现阶段会执行）

- 本地构建：`make` 产出 `module.kpm`
- 设备侧：
  - `kpatch-android <SUPERKEY> kpm load /sdcard/Download/module.kpm`
  - 通过 `kpatch-android ... help` 查到 ctl0 调用方式，然后：
    - `status` 确认加载
    - `enable 1` + `pid add <目标tgid>` 开启过滤
    - 触发目标 app 的文件/网络/syscall 行为
    - `log read 50` 拉取日志，确认 detail/分类/pc-lr 字段存在

## 不做事项（避免偏离“监控”）

- 不包含隐藏进程/隐藏 maps/绕过 SELinux/持久化等能力。
- 不在内核侧写用户空间文件作为默认日志输出（避免权限与安全风险）；仅通过 ctl0 导出或 `printk` 辅助调试。


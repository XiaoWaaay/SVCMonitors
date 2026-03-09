# svc_tracer（KPM 内核模块）对齐功能列表与匿名内存支持：实施计划

## 目标（对应 功能列表.md）

在当前已能编译生成 `module.kpm` 的基础上，把监控能力进一步对齐 [功能列表.md](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/功能列表.md) 的关键点，重点补齐：

- **全部 syscall 监控**：可选从“白名单若干 syscall”升级为“尽可能覆盖全部 syscall”。
- **调用来源识别（含匿名内存）**：当 LR/PC 落在匿名映射/JIT 区域时仍然能给出可读的归因（至少标记为 `[anon]`），并且做到“只要在这个 App 的用户态地址空间范围内就记录/归因”。
- **PID 自动跟踪**：不依赖固定 PID，支持按 `comm`（进程名）或 `uid` 过滤，App 重启后无需重新设置 PID。
- **重新修正编译**：修改后仍保证 `make` 能输出 `module.kpm`。

说明：本仓库的 KernelPatch 头文件是裁剪版，缺少完整 VMA 遍历 API，因此“从内核直接遍历 VMA”不可行；计划采用 **地址范围判定 + /proc/<pid>/maps 文本解析缓存** 来实现“匿名内存/so 归因”。

## 现状与限制

- 已有：syscall hook → 过滤 → 参数解析 → PC/LR（+简易回溯）→ ring buffer → `ctl0` 导出。
- 限制：`vm_area_struct/mm_struct` 字段在本仓库头文件中大量缺失，无法用 `find_vma()` 一类接口做地址→VMA 归属判断。
- 可用替代：
  - `mm_struct_offset` 提供 `task_size/mmap_base/start_code/end_code/...` 等偏移（见 [mm_types.h](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/KernelPatch/kernel/linux/include/linux/mm_types.h#L306-L323)），可用于“用户态地址范围”判定。
  - `filp_open/kernel_read/filp_close` 等符号可通过 `kallsyms_lookup_name()` 解析（若不同内核版本符号名不一致，需要做 fallback）。

## 设计与实现步骤

### 1) 扩展事件结构，承载 caller 归因信息

- 修改 [svc_tracer.h](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/src/include/svc_tracer.h)：
  - 增加字段：`char caller_mod[64];`（模块/映射名，so 路径 basename 或 `[anon]`/`[stack]`/`[heap]` 等）
  - 增加字段：`u64 caller_off;`（相对映射起始的偏移，便于定位）
  - 兼容：保持现有字段顺序尽量稳定；输出时若未解析到映射名则为 `"-"`。

### 2) “只要在 App 内存范围就监控”的判定（覆盖匿名内存）

- 在 `caller_resolver` 中新增函数：
  - `int caller_in_user_range(struct task_struct *task, u64 addr);`
- 实现方式（不依赖 VMA）：
  - 通过 `get_task_mm(task)` 拿 `mm`；
  - 用 `mm_struct_offset.task_size_offset`/`mmap_base_offset` 读出 `task_size/mmap_base`；
  - 规则：
    - `addr < task_size` 视为用户态地址；
    - 进一步可选：`addr >= mmap_base` 标记为“mmap 区”（一般库/匿名/JIT 在这里），否则可能是低地址段（主程序/保留段），仍算用户态。
  - 若不在用户态范围：不做 maps 归因，事件仍可记录但 `caller_mod` 设为 `"[kernel]"` 或直接跳过（作为可配置项）。

### 3) /proc/<pid>/maps 解析与缓存（so/匿名归因）

新增 `src/maps_cache.h/.c`（新模块）：

- `int maps_cache_refresh(pid_t tgid);`
  - 读取 `/proc/<tgid>/maps`，解析每行的 `start-end perms offset dev inode pathname(optional)`。
  - 保存为区间数组：`[start,end,pathname,offset]`。
  - pathname 为空时归类为 `[anon]`；若行包含 `[stack]`/`[heap]`/`[vdso]` 等保留名则保留原名。
  - 只缓存“可执行段”优先（`perms` 含 `x`），否则可选缓存全部段用于更准确归因。
  - 内存：用 `kp_malloc` 分配一块连续内存（区间数组 + 字符串池），并提供 `maps_cache_clear()`。

- `int maps_cache_lookup(pid_t tgid, u64 addr, char *out_mod, int outlen, u64 *out_off);`
  - 在缓存区间中二分/线性查找包含 `addr` 的映射；
  - 输出 `out_mod`（basename 或短名），并填充 `out_off = addr - start + file_offset`。

符号依赖（在 `symbol_resolver` 中集中解析）：
- `filp_open`, `kernel_read`（或 `vfs_read` 作为 fallback）, `filp_close`
- `get_task_mm`, `mmput`

性能策略：
- syscall hot path 不直接读 /proc；
- `resolve_caller()` 先做用户态范围判定，然后：
  - 若缓存命中：直接填充 `caller_mod/caller_off`；
  - 若缓存无/过期：仅填 `[anon?]` 或 `"-"`，并允许用户通过 ctl0 主动 `maps refresh`。

### 4) “全部 syscall 监控”实现（两种模式）

在 `hook_engine` 增加两套安装策略，可通过 `KPM_ARGS` 或 ctl0 切换：

1) **精简模式（默认保守）**：保持现有白名单 hook（openat/execve/connect/ptrace…），开销小。
2) **全量模式（满足功能列表）**：
   - 读取 `__NR_syscalls`（uapi 定义）作为上限；
   - 对每个 nr：
     - 通过 `syscalln_addr(nr, 0)` 判断该 syscall 是否存在（返回 0 跳过）；
     - 统一使用 `narg=6`（捕获前 6 个寄存器参数），after callback 使用 `hook_fargs6_t`。
   - 失败处理：某个 nr hook 失败时跳过并计数，不中断整体安装（避免不同内核裁剪导致的失败）。

注意：全量 hook 可能增加内存与 hook 链数量；计划加入编译期/运行期上限保护（例如最多 hook 300 个或按类别过滤再 hook）。

### 5) PID 自动跟踪（comm/uid）

在 `g_config` 增加：
- `uid_t target_uid; int uid_enabled;`

过滤逻辑：
- 若设置了 PID 列表：PID 优先；
- 否则若设置了 `target_uid`：匹配 `current_uid()`；
- 否则若设置了 `target_comm`：匹配 `get_task_comm(current)`；

这样可覆盖“App 重启 PID 变化”的需求。

### 6) ctl0 命令扩展（与功能列表对齐）

在 [main.c](file:///Users/bytedance/Desktop/GithubProject/SVC_Call/src/main.c) 增加命令：
- `uid set <uid>` / `uid clear`
- `maps refresh` / `maps clear`
- `hook mode slim|all`（切换 hook 模式，切换时先 remove_all 再 install_all）
- `range 0|1`（是否要求 caller 地址必须在用户态范围内才记录）

### 7) 编译与验证

- 更新顶层 `Makefile` 的源文件列表（新增 `maps_cache.c`）。
- 本地验证：
  - `make clean && make` 产出 `module.kpm`
- 设备侧验证（手工）：
  - 加载：`kpatch-android <SUPERKEY> module load /sdcard/Download/module.kpm "all"`（或默认 slim）
  - 设置目标：`ctl0 svc-tracer "comm set <app进程名>"` 或 `ctl0 ... "uid set <uid>"`
  - 刷新 maps：`ctl0 ... "maps refresh"`
  - 拉取日志：`ctl0 ... "log read 50"`，确认 `caller_mod` 对 so 与匿名段都能输出（匿名段显示 `[anon]`/`[jit]` 等）

## 交付物（实现完成后你会得到）

- `module.kpm`：支持 slim/all syscall hook 模式
- 日志中包含 `caller_mod/caller_off`，匿名映射也可归因
- 过滤支持 pid/comm/uid，并提供 maps 缓存刷新命令


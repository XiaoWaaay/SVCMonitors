package com.svcmonitor.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import java.io.File

/**
 * MainActivity — 4-tab UI built programmatically (no XML layouts).
 *
 * Tabs: 监控 | 过滤 | 事件 | 设置
 *
 * CRITICAL: All tab views are pre-built in onCreate() BEFORE observeViewModel()
 *           to avoid UninitializedPropertyAccessException on lateinit properties.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var vm: MainViewModel
    private lateinit var logExporter: LogExporter
    private val toastLogTag = "SVCMon"

    /* ── views that observers touch (must be initialized before observeViewModel) ── */
    // Dashboard tab
    private lateinit var tvStatus: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvUid: TextView
    private lateinit var tvEventCount: TextView
    private lateinit var tvMonState: TextView
    private lateinit var btnStartStop: Button
    private lateinit var spinnerApp: Spinner
    private lateinit var spinnerPreset: Spinner

    // Events tab
    private lateinit var tvEvtCount: TextView
    private lateinit var llEventList: LinearLayout
    private lateinit var scrollEvents: ScrollView

    // Settings tab
    private lateinit var switchTier2: Switch
    private lateinit var tvSuperKey: EditText

    // Filter tab
    private lateinit var tvNrCount: TextView
    private lateinit var tvNrList: TextView

    /* ── app list data ────────────────────────────────────────── */
    private var appList: List<AppInfo> = emptyList()

    /* ── colors ───────────────────────────────────────────────── */
    private val cPrimary = Color.parseColor("#1565C0")
    private val cBg = Color.parseColor("#F5F5F5")
    private val cCard = Color.WHITE
    private val cText = Color.parseColor("#212121")
    private val cSecondary = Color.parseColor("#757575")
    private val cGreen = Color.parseColor("#2E7D32")
    private val cRed = Color.parseColor("#C62828")
    private val cAccent = Color.parseColor("#FF6F00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        logExporter = LogExporter(this)

        // Load app list
        appList = AppResolver.getInstalledApps(this)

        // Pre-build ALL tab views FIRST (before observeViewModel!)
        val dashboardView = buildDashboardTab()
        val filterView = buildFilterTab()
        val eventsView = buildEventsTab()
        val settingsView = buildSettingsTab()

        // Build main layout with TabHost
        val root = buildMainLayout(dashboardView, filterView, eventsView, settingsView)
        setContentView(root)

        // NOW observe — all lateinit properties are initialized
        observeViewModel()

        // Start polling
        vm.startPolling()
    }

    /* ══════════════════════════════════════════════════════════════
     *  MAIN LAYOUT with TabHost
     * ══════════════════════════════════════════════════════════════ */

    private fun buildMainLayout(
        dashboard: View, filter: View, events: View, settings: View
    ): View {
        val tabHost = TabHost(this, null).apply {
            id = android.R.id.tabhost
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val ll = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title bar
        val titleBar = TextView(this).apply {
            text = "SVCMonitor v8.1"
            setTextColor(Color.WHITE)
            setBackgroundColor(cPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        ll.addView(titleBar)

        // Tab widget
        val tabWidget = TabWidget(this).apply {
            id = android.R.id.tabs
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        ll.addView(tabWidget)

        // Tab content
        val tabContent = FrameLayout(this).apply {
            id = android.R.id.tabcontent
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            )
        }
        ll.addView(tabContent)

        tabHost.addView(ll)
        tabHost.setup()

        // Add tabs with pre-built views
        tabHost.addTab(tabHost.newTabSpec("dashboard").setIndicator("监控").setContent { dashboard })
        tabHost.addTab(tabHost.newTabSpec("filter").setIndicator("过滤").setContent { filter })
        tabHost.addTab(tabHost.newTabSpec("events").setIndicator("事件").setContent { events })
        tabHost.addTab(tabHost.newTabSpec("settings").setIndicator("设置").setContent { settings })

        return tabHost
    }

    /* ══════════════════════════════════════════════════════════════
     *  TAB 1: 监控 (Dashboard)
     * ══════════════════════════════════════════════════════════════ */

    private fun buildDashboardTab(): View {
        val sv = ScrollView(this).apply {
            setBackgroundColor(cBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        // Status card
        col.addView(makeCard {
            addView(makeLabel("模块状态"))
            tvStatus = makeValue("未知"); addView(tvStatus)
            tvVersion = makeValue("版本: —"); addView(tvVersion)
            tvUid = makeValue("目标 UID: —"); addView(tvUid)
            tvEventCount = makeValue("事件数: 0"); addView(tvEventCount)
            tvMonState = makeValue("状态: 未启动").apply {
                setTextColor(cSecondary)
            }; addView(tvMonState)
        })

        // Step 1: Select app
        col.addView(makeCard {
            addView(makeLabel("步骤 1: 选择目标应用"))
            spinnerApp = Spinner(this@MainActivity).apply {
                val names = listOf("— 请选择 —") + appList.map { "${it.label} (${it.packageName})" }
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        vm.selectedApp = if (pos > 0) appList[pos - 1] else null
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            addView(spinnerApp)
        })

        // Step 2: Select preset
        col.addView(makeCard {
            addView(makeLabel("步骤 2: 选择监控预设"))
            spinnerPreset = Spinner(this@MainActivity).apply {
                val presetNames = StatusParser.presets.map { "${it.name} — ${it.description}" }
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, presetNames)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        vm.selectedPreset = StatusParser.presets[pos].id
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            addView(spinnerPreset)
        })

        // Step 3: Start/Stop button
        col.addView(makeCard {
            addView(makeLabel("步骤 3: 启动监控"))
            btnStartStop = Button(this@MainActivity).apply {
                text = "一键启用监控"
                setBackgroundColor(cGreen)
                setTextColor(Color.WHITE)
                setOnClickListener { onStartStopClick() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            addView(btnStartStop)
        })

        sv.addView(col)
        return sv
    }

    /* ══════════════════════════════════════════════════════════════
     *  TAB 2: 过滤 (Filter)
     * ══════════════════════════════════════════════════════════════ */

    private fun buildFilterTab(): View {
        val sv = ScrollView(this).apply {
            setBackgroundColor(cBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        col.addView(makeCard {
            addView(makeLabel("当前 NR 过滤器"))
            tvNrCount = makeValue("已选: 0 个系统调用"); addView(tvNrCount)
            tvNrList = makeValue("NR列表: (空)").apply {
                maxLines = 10
                ellipsize = TextUtils.TruncateAt.END
            }; addView(tvNrList)
        })

        // Preset quick-apply
        col.addView(makeCard {
            addView(makeLabel("快速应用预设"))
            StatusParser.presets.forEach { preset ->
                addView(Button(this@MainActivity).apply {
                    text = "${preset.name}: ${preset.description}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    isAllCaps = false
                    setOnClickListener {
                        vm.selectedPreset = preset.id
                        vm.applyPreset(preset.id)
                        showToast("已应用: ${preset.name}")
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                })
            }
        })

        // Manual NR add/remove
        col.addView(makeCard {
            addView(makeLabel("手动管理 NR"))
            val etNr = EditText(this@MainActivity).apply {
                hint = "输入 NR 编号 (如 56)"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(etNr)

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.addView(Button(this@MainActivity).apply {
                text = "添加"
                setOnClickListener {
                    val nr = etNr.text.toString().toIntOrNull()
                    if (nr != null) { vm.addNr(nr); etNr.text.clear() }
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this@MainActivity).apply {
                text = "移除"
                setOnClickListener {
                    val nr = etNr.text.toString().toIntOrNull()
                    if (nr != null) { vm.removeNr(nr); etNr.text.clear() }
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(row)
        })

        sv.addView(col)
        return sv
    }

    /* ══════════════════════════════════════════════════════════════
     *  TAB 3: 事件 (Events)
     * ══════════════════════════════════════════════════════════════ */

    private fun buildEventsTab(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Top bar with count + export buttons
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
        }

        tvEvtCount = TextView(this).apply {
            text = "事件: 0"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(cText)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(tvEvtCount)

        topBar.addView(Button(this).apply {
            text = "CSV"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isAllCaps = false
            setOnClickListener { exportCsv() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
        })

        topBar.addView(Button(this).apply {
            text = "JSON"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            isAllCaps = false
            setOnClickListener { exportJson() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
        })

        topBar.addView(Button(this).apply {
            text = "清空"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(cRed)
            isAllCaps = false
            setOnClickListener { vm.clearEvents() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
        })

        root.addView(topBar)

        // Event list in ScrollView
        scrollEvents = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        llEventList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        scrollEvents.addView(llEventList)
        root.addView(scrollEvents)

        return root
    }

    /* ══════════════════════════════════════════════════════════════
     *  TAB 4: 设置 (Settings)
     * ══════════════════════════════════════════════════════════════ */

    private fun buildSettingsTab(): View {
        val sv = ScrollView(this).apply {
            setBackgroundColor(cBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
        }

        // SuperKey setting
        col.addView(makeCard {
            addView(makeLabel("SuperKey"))
            tvSuperKey = EditText(this@MainActivity).apply {
                setText(KpmBridge.getSuperKey())
                hint = "输入 SuperKey"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            addView(tvSuperKey)
            addView(Button(this@MainActivity).apply {
                text = "保存 SuperKey"
                setOnClickListener {
                    val key = tvSuperKey.text.toString().trim()
                    if (key.isNotEmpty()) {
                        KpmBridge.setSuperKey(key)
                        showToast("SuperKey 已更新")
                    }
                }
            })
        })

        // Tier2 toggle
        col.addView(makeCard {
            addView(makeLabel("Tier-2 扩展 Hook"))
            addView(TextView(this@MainActivity).apply {
                text = "启用后额外 Hook 24 个系统调用\n包括: mkdirat, unlinkat, renameat, statfs, faccessat, " +
                        "getsockname, getsockopt, shutdown, sendmsg, recvmsg, accept4, ppoll, " +
                        "eventfd2, timerfd, signalfd4, seccomp, bpf, getrandom, prlimit64 等"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(cSecondary)
            })
            switchTier2 = Switch(this@MainActivity).apply {
                text = "启用 Tier-2"
                setOnCheckedChangeListener { _, checked ->
                    vm.setTier2(checked)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            addView(switchTier2)
        })

        // About
        col.addView(makeCard {
            addView(makeLabel("关于"))
            addView(makeValue("SVCMonitor v8.1.0"))
            addView(makeValue("ARM64 SVC 系统调用监控工具"))
            addView(makeValue("支持 50+ 系统调用深度参数解析"))
            addView(makeValue("目标: Pixel 6 / Android 12 / APatch"))
        })

        sv.addView(col)
        return sv
    }

    /* ══════════════════════════════════════════════════════════════
     *  OBSERVE VIEW MODEL
     * ══════════════════════════════════════════════════════════════ */

    private fun observeViewModel() {
        // Status updates
        vm.status.observe(this) { s ->
            if (s != null) {
                tvVersion.text = "版本: ${s.version}"
                tvUid.text = "目标 UID: ${if (s.targetUid >= 0) s.targetUid.toString() else "全部"}"
                tvStatus.text = if (s.enabled) "已启用" else "已禁用"
                tvStatus.setTextColor(if (s.enabled) cGreen else cRed)
                tvNrCount.text = "已选: ${s.nrCount} 个系统调用"
                tvNrList.text = "NR列表: ${s.nrList.joinToString(", ") { "${StatusParser.nrToName(it)}($it)" }}"
                switchTier2.isChecked = s.tier2
            }
        }

        // Monitoring state
        vm.monitoring.observe(this) { mon ->
            if (mon) {
                tvMonState.text = "状态: 监控中"
                tvMonState.setTextColor(cGreen)
                btnStartStop.text = "停止监控"
                btnStartStop.setBackgroundColor(cRed)
            } else {
                tvMonState.text = "状态: 未启动"
                tvMonState.setTextColor(cSecondary)
                btnStartStop.text = "一键启用监控"
                btnStartStop.setBackgroundColor(cGreen)
            }
        }

        // Event count
        vm.eventCount.observe(this) { count ->
            tvEventCount.text = "事件数: $count"
            tvEvtCount.text = "事件: $count"
        }

        // Events list
        vm.events.observe(this) { events ->
            updateEventList(events)
        }

        // Toast
        vm.toast.observe(this) { msg ->
            if (msg != null) {
                showToast(msg)
                vm.toastConsumed()
            }
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  EVENT LIST rendering
     * ══════════════════════════════════════════════════════════════ */

    private fun updateEventList(events: List<StatusParser.SvcEvent>) {
        llEventList.removeAllViews()

        // Show last 100 events in reverse chronological order
        val display = events.take(100)
        if (display.isEmpty()) {
            llEventList.addView(TextView(this).apply {
                text = "暂无事件。启动监控后事件将自动显示。"
                setTextColor(cSecondary)
                setPadding(dp(8), dp(16), dp(8), dp(16))
                gravity = Gravity.CENTER
            })
            return
        }

        display.forEach { evt ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(cCard)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }
            }

            // Row 1: syscall name + NR + category
            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row1.addView(TextView(this).apply {
                text = "${evt.name}(${evt.nr})"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(cPrimary)
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row1.addView(TextView(this).apply {
                text = StatusParser.syscallCategory(evt.nr)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(cAccent)
                setPadding(dp(6), dp(2), dp(6), dp(2))
            })
            card.addView(row1)

            // Row 2: pid/uid/comm
            card.addView(TextView(this).apply {
                text = "pid=${evt.pid} uid=${evt.uid} comm=${evt.comm}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(cSecondary)
            })

            // Row 3: description (the deep-parsed args)
            if (evt.desc.isNotEmpty()) {
                card.addView(TextView(this).apply {
                    text = evt.desc
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(cText)
                    typeface = Typeface.MONOSPACE
                    maxLines = 6
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                })
            }

            // Click for detail dialog
            card.setOnClickListener {
                showEventDetail(evt)
            }

            llEventList.addView(card)
        }
    }

    private fun showEventDetail(evt: StatusParser.SvcEvent) {
        val detail = buildString {
            appendLine("═══ 系统调用详情 ═══")
            appendLine()
            appendLine("调用号: ${evt.nr} (${evt.name})")
            appendLine("分类: ${StatusParser.syscallCategory(evt.nr)}")
            appendLine("PID: ${evt.pid}")
            appendLine("UID: ${evt.uid}")
            appendLine("进程名: ${evt.comm}")
            appendLine()
            appendLine("═══ 原始参数 ═══")
            appendLine("  a0: 0x${java.lang.Long.toHexString(evt.a0)} (${evt.a0})")
            appendLine("  a1: 0x${java.lang.Long.toHexString(evt.a1)} (${evt.a1})")
            appendLine("  a2: 0x${java.lang.Long.toHexString(evt.a2)} (${evt.a2})")
            appendLine("  a3: 0x${java.lang.Long.toHexString(evt.a3)} (${evt.a3})")
            appendLine("  a4: 0x${java.lang.Long.toHexString(evt.a4)} (${evt.a4})")
            appendLine("  a5: 0x${java.lang.Long.toHexString(evt.a5)} (${evt.a5})")
            appendLine()
            appendLine("═══ 解析结果 ═══")
            appendLine(evt.desc)
        }

        AlertDialog.Builder(this)
            .setTitle("${evt.name}(${evt.nr})")
            .setMessage(detail)
            .setPositiveButton("确定", null)
            .setNeutralButton("复制") { _, _ ->
                val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("svc_event", detail))
                showToast("已复制到剪贴板")
            }
            .show()
    }

    /* ══════════════════════════════════════════════════════════════
     *  START/STOP click handler
     * ══════════════════════════════════════════════════════════════ */

    private fun onStartStopClick() {
        if (vm.monitoring.value == true) {
            vm.stopMonitoring()
        } else {
            val app = vm.selectedApp
            if (app == null) {
                showToast("请先选择目标应用", isError = true)
                return
            }
            vm.startMonitoring(app.uid, vm.selectedPreset)
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  UI HELPERS
     * ══════════════════════════════════════════════════════════════ */

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun makeCard(block: LinearLayout.() -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cCard)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
            elevation = dp(2).toFloat()
            block()
        }
    }

    private fun makeLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(cPrimary)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(6))
        }
    }

    private fun makeValue(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(cText)
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun showToast(message: String, isError: Boolean = false, duration: Int = Toast.LENGTH_SHORT) {
        val key = KpmBridge.getSuperKey()
        val safeMsg = if (key.isNotBlank() && message.contains(key)) {
            message.replace(key, "<SUPERKEY_REDACTED>")
        } else {
            message
        }
        if (isError) {
            Log.e(toastLogTag, "TOAST $safeMsg")
        } else {
            Log.i(toastLogTag, "TOAST $safeMsg")
        }
        Toast.makeText(this, message, duration).show()
    }

    private fun exportCsv() {
        val events = vm.events.value ?: emptyList()
        if (events.isEmpty()) {
            showToast("没有事件可导出")
            return
        }
        try {
            val file = logExporter.exportCsv(events)
            shareFile(file, "text/csv")
        } catch (e: Exception) {
            showToast("导出失败: ${e.message}", isError = true, duration = Toast.LENGTH_LONG)
        }
    }

    private fun exportJson() {
        val events = vm.events.value ?: emptyList()
        if (events.isEmpty()) {
            showToast("没有事件可导出")
            return
        }
        try {
            val file = logExporter.exportJson(events)
            shareFile(file, "application/json")
        } catch (e: Exception) {
            showToast("导出失败: ${e.message}", isError = true, duration = Toast.LENGTH_LONG)
        }
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享"))
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.stopPolling()
    }
}

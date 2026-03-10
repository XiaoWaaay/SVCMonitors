package com.svcmonitor.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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

    /* ── views that observers touch (must be initialized before observeViewModel) ── */
    // Dashboard tab
    private lateinit var tvStatus: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvUid: TextView
    private lateinit var tvEventCount: TextView
    private lateinit var tvMonState: TextView
    private lateinit var tvMsg: TextView
    private lateinit var btnStartStop: Button
    private lateinit var etAppSearch: EditText
    private lateinit var spinnerApp: Spinner
    private lateinit var tvDashNrCount: TextView
    private lateinit var tvDashNrList: TextView

    // Events tab
    private lateinit var tvEvtCount: TextView
    private lateinit var llEventList: LinearLayout
    private lateinit var scrollEvents: ScrollView

    // Settings tab
    private lateinit var switchTier2: Switch
    private lateinit var switchHideSystemApps: Switch
    private lateinit var switchOnlyLaunchableApps: Switch
    private lateinit var tvSuperKey: EditText

    // Filter tab
    private lateinit var tvNrCount: TextView
    private lateinit var tvNrList: TextView
    private lateinit var llSelectedNrs: LinearLayout
    private lateinit var switchDoFilpOpen: Switch
    private val nrNameViews = HashMap<Int, TextView>()
    private lateinit var filterListContainer: LinearLayout
    private var hookedNrSet: Set<Int> = emptySet()
    private var currentNrList: List<Int> = emptyList()

    /* ── app list data ────────────────────────────────────────── */
    private var appList: List<AppInfo> = emptyList()
    private var appSearchQuery: String = ""
    private var hideSystemApps: Boolean = false
    private var onlyLaunchableApps: Boolean = false

    private val prefs by lazy { getSharedPreferences("svcmon_prefs", MODE_PRIVATE) }

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

        hideSystemApps = prefs.getBoolean("hide_system_apps", false)
        onlyLaunchableApps = prefs.getBoolean("only_launchable_apps", false)
        vm.doFilpOpenEnabled = prefs.getBoolean("do_filp_open", true)
        appList = loadVisibleApps(appSearchQuery)

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

            tvMsg = makeValue("提示: -").apply {
                setTextColor(cSecondary)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }; addView(tvMsg)
        })

        // Step 1: Select app
        col.addView(makeCard {
            addView(makeLabel("步骤 1: 选择目标应用"))
            etAppSearch = EditText(this@MainActivity).apply {
                hint = "搜索应用名 / 包名"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        appSearchQuery = s?.toString()?.trim().orEmpty()
                        refreshAppSpinner()
                    }
                })
            }
            addView(etAppSearch)
            spinnerApp = Spinner(this@MainActivity).apply {
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        vm.selectedApp = if (pos > 0) appList[pos - 1] else null
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            }
            addView(spinnerApp)
            refreshAppSpinner()
        })

        // Step 2: Show selected NRs (managed in Filter tab)
        col.addView(makeCard {
            addView(makeLabel("步骤 2: 已选系统调用（在「过滤」页管理）"))
            tvDashNrCount = makeValue("已选: 0 个系统调用"); addView(tvDashNrCount)
            tvDashNrList = makeValue("NR列表: (空)").apply {
                maxLines = 6
                ellipsize = TextUtils.TruncateAt.END
            }; addView(tvDashNrList)
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
            addView(makeLabel("额外 Hook"))
            switchDoFilpOpen = Switch(this@MainActivity).apply {
                text = "开启 do_filp_open（更底层 open 路径）"
                isChecked = prefs.getBoolean("do_filp_open", true)
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("do_filp_open", checked).apply()
                    vm.setDoFilpOpen(checked)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            addView(switchDoFilpOpen)
        })

        col.addView(makeCard {
            addView(makeLabel("当前 NR 过滤器"))
            tvNrCount = makeValue("已选: 0 个系统调用"); addView(tvNrCount)
            tvNrList = makeValue("NR列表: (空)").apply {
                maxLines = 10
                ellipsize = TextUtils.TruncateAt.END
            }; addView(tvNrList)
            llSelectedNrs = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            }
            addView(llSelectedNrs)
            addView(Button(this@MainActivity).apply {
                text = "清空已选 NR"
                setTextColor(cRed)
                isAllCaps = false
                setOnClickListener { vm.disableAll() }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            })
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
                        applyPresetUi(preset.id)
                        tvMsg.text = "提示: 已应用预设 ${preset.name}"
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
                    if (nr != null) {
                        if (hookedNrSet.isNotEmpty() && !hookedNrSet.contains(nr)) {
                            tvMsg.text = "提示: NR 未被 hook，可能不会有事件"
                        }
                        vm.addNr(nr)
                        etNr.text.clear()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this@MainActivity).apply {
                text = "移除"
                setOnClickListener {
                    val nr = etNr.text.toString().toIntOrNull()
                    if (nr != null) {
                        vm.removeNr(nr)
                        etNr.text.clear()
                    }
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(row)
        })

        col.addView(makeCard {
            addView(makeLabel("按分类选择系统调用"))
            filterListContainer = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
            }
            addView(filterListContainer)
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
                        tvMsg.text = "提示: SuperKey 已更新"
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
                    vm.tier2(checked)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            }
            addView(switchTier2)
        })

        col.addView(makeCard {
            addView(makeLabel("应用列表"))
            switchHideSystemApps = Switch(this@MainActivity).apply {
                text = "隐藏系统应用"
                isChecked = hideSystemApps
                setOnCheckedChangeListener { _, checked ->
                    hideSystemApps = checked
                    prefs.edit().putBoolean("hide_system_apps", checked).apply()
                    refreshAppSpinner()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6) }
            }
            addView(switchHideSystemApps)

            switchOnlyLaunchableApps = Switch(this@MainActivity).apply {
                text = "仅显示可启动应用"
                isChecked = onlyLaunchableApps
                setOnCheckedChangeListener { _, checked ->
                    onlyLaunchableApps = checked
                    prefs.edit().putBoolean("only_launchable_apps", checked).apply()
                    refreshAppSpinner()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }
            addView(switchOnlyLaunchableApps)
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

    private fun loadVisibleApps(query: String): List<AppInfo> {
        return if (query.isBlank()) {
            AppResolver.getAllApps(this, hideSystemApps = hideSystemApps, onlyLaunchableApps = onlyLaunchableApps)
        } else {
            AppResolver.searchApps(
                this,
                query = query,
                hideSystemApps = hideSystemApps,
                onlyLaunchableApps = onlyLaunchableApps
            )
        }
    }

    private fun refreshAppSpinner() {
        val prevUid = vm.selectedApp?.uid
        appList = loadVisibleApps(appSearchQuery)
        val names = listOf("— 请选择 —") + appList.map { "${it.label} (${it.packageName})" }
        val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
        spinnerApp.adapter = adapter

        val idx = if (prevUid != null) appList.indexOfFirst { it.uid == prevUid } else -1
        if (idx >= 0) {
            spinnerApp.setSelection(idx + 1, false)
            vm.selectedApp = appList[idx]
        } else {
            spinnerApp.setSelection(0, false)
            vm.selectedApp = null
        }
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
                tvDashNrCount.text = "已选: ${s.nrCount} 个系统调用"
                tvDashNrList.text = "NR列表: ${s.nrList.joinToString(", ") { "${StatusParser.nrToName(it)}($it)" }}"
                currentNrList = s.nrList
                renderSelectedNrs(s.nrList)
                switchTier2.isChecked = s.tier2
                refreshNrHighlights(s.nrList)
                renderFilterList(s.hooks)
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
                tvMsg.text = "提示: $msg"
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
                text = "#${evt.seq}  ${evt.name}(${evt.nr})"
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
        vm.viewModelScope_launch {
            val pcResolved = formatAddrSoOffset(evt.pid, evt.pc)
            val callerResolved = formatAddrSoOffset(evt.pid, evt.caller)
            val cloneResolved = if (evt.cloneFn != 0L) formatAddrSoOffset(evt.pid, evt.cloneFn) else ""

            val fdResolved = if (nrUsesFd(evt.nr)) {
                val r = KpmBridge.readProcFdLink(evt.pid, evt.a0)
                if (r.isNotBlank()) r else ""
            } else {
                ""
            }

            val detail = buildString {
                appendLine("═══ 系统调用详情 ═══")
                appendLine()
                appendLine("#${evt.seq}  ${evt.name}(${evt.nr})")
                appendLine("分类: ${StatusParser.syscallCategory(evt.nr)}")
                appendLine("PID: ${evt.pid}  UID: ${evt.uid}")
                appendLine("进程名: ${evt.comm}")
                appendLine()
                appendLine("pc: $pcResolved")
                appendLine("caller: $callerResolved")
                if (evt.nr == 220 && evt.cloneFn != 0L) {
                    appendLine("clone_fn: $cloneResolved")
                }
                if (fdResolved.isNotEmpty()) {
                    appendLine("fd(${evt.a0}): $fdResolved")
                }
                appendLine()
                appendLine("═══ 参数 ═══")
                appendLine("a0: 0x${java.lang.Long.toHexString(evt.a0)} (${evt.a0})")
                appendLine("a1: 0x${java.lang.Long.toHexString(evt.a1)} (${evt.a1})")
                appendLine("a2: 0x${java.lang.Long.toHexString(evt.a2)} (${evt.a2})")
                appendLine("a3: 0x${java.lang.Long.toHexString(evt.a3)} (${evt.a3})")
                appendLine("a4: 0x${java.lang.Long.toHexString(evt.a4)} (${evt.a4})")
                appendLine("a5: 0x${java.lang.Long.toHexString(evt.a5)} (${evt.a5})")
                appendLine()
                appendLine("═══ 解析结果 ═══")
                appendLine(evt.desc)

                val addrs = extractHexAddrs(evt.desc).take(8)
                if (addrs.isNotEmpty()) {
                    appendLine()
                    appendLine("═══ desc 地址解析 ═══")
                    addrs.forEach { a ->
                        val abs = "0x${java.lang.Long.toHexString(a)}"
                        val so = resolveAddress(evt.pid, a)
                        appendLine(if (so.isNotEmpty()) "$abs -> $so" else "$abs -> unmapped")
                    }
                }
            }

            AlertDialog.Builder(this@MainActivity)
                .setTitle("${evt.name}(${evt.nr})")
                .setMessage(detail)
                .setPositiveButton("确定", null)
                .setNeutralButton("复制") { _, _ ->
                    val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clip.setPrimaryClip(android.content.ClipData.newPlainText("svc_event", detail))
                    tvMsg.text = "提示: 已复制到剪贴板"
                }
                .show()
        }
    }

    private fun exportCsv() {
        val events = vm.events.value ?: emptyList()
        if (events.isEmpty()) {
            tvMsg.text = "提示: 没有事件可导出"
            return
        }
        try {
            val file = logExporter.exportCsv(events)
            shareFile(file, "text/csv")
            tvMsg.text = "提示: 已导出 CSV"
        } catch (e: Exception) {
            tvMsg.text = "提示: 导出失败: ${e.message}"
        }
    }

    private fun exportJson() {
        val events = vm.events.value ?: emptyList()
        if (events.isEmpty()) {
            tvMsg.text = "提示: 没有事件可导出"
            return
        }
        try {
            val file = logExporter.exportJson(events)
            shareFile(file, "application/json")
            tvMsg.text = "提示: 已导出 JSON"
        } catch (e: Exception) {
            tvMsg.text = "提示: 导出失败: ${e.message}"
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

    /* ══════════════════════════════════════════════════════════════
     *  START/STOP click handler
     * ══════════════════════════════════════════════════════════════ */

    private fun onStartStopClick() {
        if (vm.monitoring.value == true) {
            vm.stopMonitoring()
        } else {
            val app = vm.selectedApp
            if (app == null) {
                tvMsg.text = "提示: 请先选择目标应用"
                return
            }
            val nrs = currentNrList
            if (nrs.isEmpty()) {
                vm.startMonitoring(app.uid)
            } else {
                vm.startMonitoringWithNrs(app.uid, nrs)
            }
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

    private fun refreshNrHighlights(nrs: List<Int>) {
        val set = nrs.toHashSet()
        for ((nr, tv) in nrNameViews) {
            if (set.contains(nr)) {
                tv.setTextColor(cGreen)
                tv.typeface = Typeface.DEFAULT_BOLD
            } else {
                tv.setTextColor(cText)
                tv.typeface = Typeface.DEFAULT
            }
        }
    }

    private fun renderFilterList(hooks: List<StatusParser.HookInfo>) {
        filterListContainer.removeAllViews()
        nrNameViews.clear()
        val syscallHooked = hooks.mapNotNull { if (it.nr >= 0) it.nr else null }.toHashSet()
        hookedNrSet = syscallHooked
        val nonSysHooks = hooks.filter { it.nr < 0 }
        if (syscallHooked.isEmpty() && nonSysHooks.isEmpty()) {
            filterListContainer.addView(TextView(this).apply {
                text = "暂无已安装的 Hook"
                setTextColor(cSecondary)
                setPadding(0, dp(4), 0, dp(4))
            })
            return
        }

        if (nonSysHooks.isNotEmpty()) {
            filterListContainer.addView(TextView(this).apply {
                text = "🔧 其他 Hook"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(cPrimary)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(4))
            })
            nonSysHooks.forEach { h ->
                filterListContainer.addView(TextView(this).apply {
                    text = "${h.name}  (${h.method})"
                    setTextColor(cText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setPadding(dp(8), dp(2), 0, dp(2))
                })
            }
        }

        val used = HashSet<Int>()
        StatusParser.categories.forEach { cat ->
            val list = cat.syscalls.filter { syscallHooked.contains(it.nr) }
            if (list.isEmpty()) return@forEach
            filterListContainer.addView(TextView(this).apply {
                text = "${cat.icon} ${cat.name}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(cPrimary)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(4))
            })
            list.forEach { sc ->
                used.add(sc.nr)
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, dp(2))
                }
                row.addView(TextView(this).apply {
                    text = sc.nr.toString()
                    typeface = Typeface.MONOSPACE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(cSecondary)
                    minWidth = dp(48)
                })
                val nameView = TextView(this).apply {
                    text = "${sc.name}  ${sc.description}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(cText)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                nrNameViews[sc.nr] = nameView
                row.addView(nameView)
                row.addView(Button(this).apply {
                    text = "+"
                    setOnClickListener { vm.addNr(sc.nr) }
                })
                row.addView(Button(this).apply {
                    text = "-"
                    setOnClickListener { vm.removeNr(sc.nr) }
                })
                filterListContainer.addView(row)
            }
        }

        val extra = hooks.filter { !used.contains(it.nr) }
        if (extra.isNotEmpty()) {
            filterListContainer.addView(TextView(this).apply {
                text = "＊ 其他"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(cPrimary)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(4))
            })
            extra.forEach { h ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(2), 0, dp(2))
                }
                row.addView(TextView(this).apply {
                    text = h.nr.toString()
                    typeface = Typeface.MONOSPACE
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(cSecondary)
                    minWidth = dp(48)
                })
                val nameView = TextView(this).apply {
                    text = h.name
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(cText)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                nrNameViews[h.nr] = nameView
                row.addView(nameView)
                row.addView(Button(this).apply {
                    text = "+"
                    setOnClickListener { vm.addNr(h.nr) }
                })
                row.addView(Button(this).apply {
                    text = "-"
                    setOnClickListener { vm.removeNr(h.nr) }
                })
                filterListContainer.addView(row)
            }
        }
    }

    private fun renderSelectedNrs(nrs: List<Int>) {
        llSelectedNrs.removeAllViews()
        if (nrs.isEmpty()) {
            llSelectedNrs.addView(TextView(this).apply {
                text = "未选择任何系统调用"
                setTextColor(cSecondary)
            })
            return
        }

        nrs.sorted().forEach { nr ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
            }
            row.addView(TextView(this).apply {
                text = "${StatusParser.nrToName(nr)}($nr)"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(cText)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Button(this).apply {
                text = "删除"
                setTextColor(cRed)
                isAllCaps = false
                setOnClickListener { vm.removeNr(nr) }
            })
            llSelectedNrs.addView(row)
        }
    }

    private fun applyPresetUi(presetId: String) {
        vm.selectedPreset = presetId
        vm.applyPreset(presetId)
    }

    private fun nrUsesFd(nr: Int): Boolean {
        return when (nr) {
            57, 62, 63, 64, 65, 66, 71, 80, 29, 46, 45, 43, 25 -> true
            else -> false
        }
    }

    private data class MapRegion(val start: Long, val end: Long, val mapOffset: Long, val path: String)
    private data class MapsSnapshot(val tsMs: Long, val regions: List<MapRegion>)
    private val mapsCache = HashMap<Int, MapsSnapshot>()
    private val mapsCacheTtlMs = 5000L

    private suspend fun resolveAddress(pid: Int, addr: Long): String {
        if (pid <= 0 || addr == 0L) return ""
        val regions = getMapsRegions(pid) ?: return ""
        val region = findMapRegion(regions, addr) ?: return ""
        val fileOffset = (addr - region.start) + region.mapOffset
        val name = if (region.path.isNotBlank()) region.path.substringAfterLast('/') else "[anon]"
        return "$name+0x${java.lang.Long.toHexString(fileOffset)}"
    }

    private suspend fun formatAddrSoOffset(pid: Int, addr: Long): String {
        val abs = "0x${java.lang.Long.toHexString(addr)}"
        val so = resolveAddress(pid, addr)
        return if (so.isNotEmpty()) "$so ($abs)" else "$abs (unmapped)"
    }

    private suspend fun getMapsRegions(pid: Int): List<MapRegion>? {
        val now = System.currentTimeMillis()
        val cached = mapsCache[pid]
        if (cached != null && now - cached.tsMs <= mapsCacheTtlMs) {
            return cached.regions
        }

        val maps = KpmBridge.readProcMaps(pid)
        if (maps.isBlank()) return null
        val regions = parseMapsRegions(maps)
        mapsCache[pid] = MapsSnapshot(now, regions)
        return regions
    }

    private fun parseMapsRegions(maps: String): List<MapRegion> {
        val out = ArrayList<MapRegion>(256)
        val lines = maps.split('\n')
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = line.trim().split(Regex("\\s+"), limit = 6)
            if (parts.size < 3) continue
            val range = parts[0]
            val offStr = parts[2]
            val path = if (parts.size >= 6) parts[5] else ""
            val dash = range.indexOf('-')
            if (dash <= 0) continue
            val start = range.substring(0, dash).toLongOrNull(16) ?: continue
            val end = range.substring(dash + 1).toLongOrNull(16) ?: continue
            val offset = offStr.toLongOrNull(16) ?: 0L
            out.add(MapRegion(start = start, end = end, mapOffset = offset, path = path))
        }
        return out
    }

    private fun findMapRegion(regions: List<MapRegion>, addr: Long): MapRegion? {
        for (r in regions) {
            if (addr >= r.start && addr < r.end) return r
        }
        return null
    }

    private fun extractHexAddrs(text: String): List<Long> {
        val re = Regex("0x[0-9a-fA-F]{6,16}")
        val seen = LinkedHashSet<Long>()
        for (m in re.findAll(text)) {
            val raw = m.value.substring(2)
            val v = raw.toLongOrNull(16) ?: continue
            if (v != 0L) seen.add(v)
            if (seen.size >= 16) break
        }
        return seen.toList()
    }

    /* helper for filter tab buttons to launch coroutine */
    private fun MainViewModel.viewModelScope_launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    override fun onDestroy() {
        super.onDestroy()
        vm.stopPolling()
    }
}

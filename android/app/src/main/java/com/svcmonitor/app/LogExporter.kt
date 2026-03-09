package com.svcmonitor.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * LogExporter v8.0 — Export events to CSV or JSON files.
 */
class LogExporter(private val ctx: Context) {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    fun exportCsv(events: List<StatusParser.SvcEvent>): File {
        val ts = dateFormat.format(Date())
        val file = File(ctx.cacheDir, "svc_events_$ts.csv")

        file.bufferedWriter().use { w ->
            w.write("nr,name,pid,uid,comm,a0,a1,a2,a3,a4,a5,desc")
            w.newLine()
            for (ev in events) {
                val desc = ev.desc.replace("\"", "\"\"")
                w.write("${ev.nr},${ev.name},${ev.pid},${ev.uid},${ev.comm},")
                w.write("${ev.a0},${ev.a1},${ev.a2},${ev.a3},${ev.a4},${ev.a5},")
                w.write("\"$desc\"")
                w.newLine()
            }
        }
        return file
    }

    fun exportJson(events: List<StatusParser.SvcEvent>): File {
        val ts = dateFormat.format(Date())
        val file = File(ctx.cacheDir, "svc_events_$ts.json")

        val arr = JSONArray()
        for (ev in events) {
            arr.put(JSONObject().apply {
                put("nr", ev.nr)
                put("name", ev.name)
                put("pid", ev.pid)
                put("uid", ev.uid)
                put("comm", ev.comm)
                put("a0", ev.a0)
                put("a1", ev.a1)
                put("a2", ev.a2)
                put("a3", ev.a3)
                put("a4", ev.a4)
                put("a5", ev.a5)
                put("desc", ev.desc)
            })
        }

        file.writeText(arr.toString(2))
        return file
    }
}

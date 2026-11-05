package com.ruanglega.xos

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvFree: TextView
    private lateinit var tvSavedLogInfo: TextView
    private lateinit var tvLog: TextView
    private val logBuilder = StringBuilder()
    private val executor = Executors.newSingleThreadExecutor()
    private var currentLogFileName = ""

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            appendLog("Izin Shizuku diberikan.")
            updateHeader()
        } else {
            appendLog("Izin Shizuku ditolak.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        currentLogFileName = "RuangLega-log-$stamp.txt"

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#18191B"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }

        tvStatus = TextView(this).apply {
            setTextColor(Color.parseColor("#8A9199"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        tvFree = TextView(this).apply {
            setTextColor(Color.parseColor("#8A9199"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(2), 0, dp(12))
        }

        root.addView(tvStatus)
        root.addView(tvFree)

        addButton(root, "BUKA SHIZUKU", null) {
            val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) startActivity(intent) else appendLog("Aplikasi Shizuku tidak ditemukan.")
        }

        addButton(root, "BERI IZIN SHIZUKU", null) {
            if (!Shizuku.pingBinder()) {
                appendLog("Shizuku belum berjalan. Buka aplikasi Shizuku lalu mulai lewat Wireless debugging.")
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            } else {
                appendLog("Izin Shizuku sudah diberikan.")
                updateHeader()
            }
        }

        // 1. ANALISIS PENYIMPANAN (Fix dengan --user 0 dan pembacaan folder aman)
        addButton(
            root,
            "1. ANALISIS PENYIMPANAN",
            "Menampilkan jumlah aplikasi terinstal dan daftar folder terbesar di memori."
        ) {
            runShell(
                "1. Analisis penyimpanan",
                """
                echo "=== JUMLAH APLIKASI PENGGUNA ==="
                pm list packages -3 --user 0 | wc -l
                echo "=== 15 DAFTAR PAKET APLIKASI ==="
                pm list packages -3 --user 0 | cut -d: -f2 | head -n 15
                echo "=== 10 FOLDER TERBESAR DI /sdcard ==="
                du -sh /sdcard/* 2>/dev/null | sort -hr | head -n 10
                """.trimIndent()
            )
        }

        // 2. BERSIHKAN CACHE SEMUA APLIKASI (Fix pembacaan storage via StatFs)
        addButton(
            root,
            "2. BERSIHKAN CACHE SEMUA APLIKASI",
            "Membersihkan cache sistem dan seluruh aplikasi pengguna."
        ) {
            runShell(
                "2. Bersihkan cache semua aplikasi",
                """
                echo "Memulai pembersihan cache sistem..."
                pm trim-caches 999999999999999999
                for d in /sdcard/Android/data/*/cache; do
                    if [ -d "${'$'}d" ]; then rm -rf "${'$'}d"/* 2>/dev/null; fi
                done
                echo "Selesai membersihkan cache seluruh aplikasi."
                """.trimIndent()
            )
        }

        // 3. BERSIHKAN CACHE EKSTERNAL
        addButton(
            root,
            "3. BERSIHKAN CACHE EKSTERNAL",
            "Mengosongkan folder cache di /sdcard/Android/data/<aplikasi>/cache."
        ) {
            runShell(
                "3. Bersihkan cache eksternal",
                """
                for d in /sdcard/Android/data/*/cache; do
                    if [ -d "${'$'}d" ]; then
                        size=${'$'}(du -sh "${'$'}d" 2>/dev/null | cut -f1)
                        echo "Mengosongkan (${'$'}size): ${'$'}d"
                        rm -rf "${'$'}d"/* "${'$'}d"/.* 2>/dev/null
                    fi
                done
                echo "Selesai membersihkan cache eksternal."
                """.trimIndent()
            )
        }

        // 4. BERSIHKAN LOG XOS / INFINIX
        addButton(
            root,
            "4. BERSIHKAN LOG XOS / INFINIX",
            "Mengosongkan /sdcard/mtklog, debuglogger, tranlog, dan buffer logcat di memori."
        ) {
            runShell(
                "4. Bersihkan log XOS / Infinix",
                """
                for logdir in /sdcard/mtklog /sdcard/debuglogger /sdcard/tranlog /sdcard/Android/data/com.transsion.phonemaster/cache; do
                    if [ -d "${'$'}logdir" ]; then
                        du -sh "${'$'}logdir" 2>/dev/null
                        rm -rf "${'$'}logdir"/* 2>/dev/null
                        echo "Dibersihkan: ${'$'}logdir"
                    fi
                done
                logcat -b all -c 2>/dev/null
                echo "Buffer logcat dan log sistem berhasil dikosongkan."
                """.trimIndent()
            )
        }

        // 5. RESET DEXOPT (Fix parameter --user 0 agar paket terdeteksi sempurna)
        addButton(
            root,
            "5. RESET DEXOPT (BYPASS INFINIX XOS)",
            "Mengembalikan hasil kompilasi aplikasi ke bawaan satu per satu."
        ) {
            runShell(
                "5. Reset dexopt (Mode Infinix XOS)",
                """
                echo "Mengambil daftar aplikasi..."
                pkgs=${'$'}(pm list packages -3 --user 0 | cut -d: -f2 | tr -d '\r')
                total=${'$'}(echo "${'$'}pkgs" | wc -l)
                echo "Total aplikasi ditemukan: ${'$'}total"
                i=0
                for p in ${'$'}pkgs; do
                    i=${'$'}((i + 1))
                    r1=${'$'}(cmd package compile --reset "${'$'}p" 2>&1)
                    r2=${'$'}(cmd package compile -m verify -f "${'$'}p" 2>&1)
                    echo "[${'$'}i/${'$'}total] ${'$'}p -> reset: ${'$'}r1 | verify: ${'$'}r2"
                done
                echo "Selesai! Seluruh ruang kompilasi aplikasi telah direset."
                """.trimIndent()
            )
        }

        addButton(root, "SALIN SELURUH LOG", null) {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("RuangLegaLog", logBuilder.toString()))
            Toast.makeText(this, "Seluruh log disalin", Toast.LENGTH_SHORT).show()
        }

        tvSavedLogInfo = TextView(this).apply {
            text = "Log lengkap disimpan otomatis ke Download/$currentLogFileName"
            setTextColor(Color.parseColor("#8A9199"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(tvSavedLogInfo)

        tvLog = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#9AA0A6"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        root.addView(tvLog)

        scroll.addView(root)
        setContentView(scroll)

        Shizuku.addRequestPermissionResultListener(permListener)
        updateHeader()
    }

    private fun addButton(parent: LinearLayout, title: String, desc: String?, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = title
            setTextColor(Color.parseColor("#E2E2E6"))
            setBackgroundColor(Color.parseColor("#424448"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
            lp.topMargin = dp(8)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        parent.addView(btn)

        if (desc != null) {
            val tvDesc = TextView(this).apply {
                text = desc
                setTextColor(Color.parseColor("#8A9199"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(4), 0, dp(6))
            }
            parent.addView(tvDesc)
        }
    }

    private fun runShell(title: String, script: String) {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            appendLog("Shizuku belum berjalan atau belum diberi izin.")
            return
        }
        appendLog("> $title")
        executor.execute {
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val proc = method.invoke(null, arrayOf("sh", "-c", script), null, null) as ShizukuRemoteProcess
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val out = line ?: ""
                    runOnUiThread { appendLog(out) }
                }
                proc.waitFor()
                runOnUiThread { updateHeader() }
            } catch (e: Exception) {
                runOnUiThread { appendLog("Gagal: ${e.message}") }
            }
        }
    }

    private fun updateHeader() {
        val alive = Shizuku.pingBinder()
        val granted = alive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        tvStatus.text = "Shizuku: " + if (granted) "siap" else if (alive) "belum diberi izin" else "belum berjalan"

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = (stat.availableBlocksLong * stat.blockSizeLong).toDouble() / (1024 * 1024 * 1024)
        tvFree.text = String.format(Locale.getDefault(), "Ruang bebas: %.2f GB", freeGb)
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$time  $msg\n"
        logBuilder.append(line)
        tvLog.text = logBuilder.toString()

        try {
            val dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val logFile = File(dlDir, currentLogFileName)
            logFile.appendText(line)
        } catch (_: Exception) {
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permListener)
    }
}

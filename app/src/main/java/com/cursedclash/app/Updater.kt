package com.cursedclash.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val REPO = "tacknown/cursed-clash"
    private const val APK_NAME = "CursedClash.apk"

    sealed class Result {
        object NoNet : Result()
        object Latest : Result()
        class Error(val msg: String) : Result()
        class Newer(val tag: Int, val url: String) : Result()
    }

    fun installedCode(ctx: Context): Long {
        return try {
            val i = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            PackageInfoCompat.getLongVersionCode(i)
        } catch (e: Exception) {
            0L
        }
    }

    fun versionName(ctx: Context): String {
        return try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    private fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val cap = cm.getNetworkCapabilities(n) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun fetch(ctx: Context): Result {
        if (!online(ctx)) return Result.NoNet
        var conn: HttpURLConnection? = null
        return try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "CursedClash")
            val code = c.responseCode
            if (code == 404) return Result.Latest
            if (code != 200) return Result.Error("HTTP $code")
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(txt)
            val tag = j.getString("tag_name").trim().removePrefix("v").removePrefix("V")
            val n = tag.toIntOrNull() ?: return Result.Error("tag")
            val assets = j.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", true)) {
                    url = a.getString("browser_download_url")
                    break
                }
            }
            if (url != null && n > installedCode(ctx)) Result.Newer(n, url) else Result.Latest
        } catch (e: IOException) {
            Result.NoNet
        } catch (e: Exception) {
            Result.Error(e.message ?: "erro")
        } finally {
            conn?.disconnect()
        }
    }

    fun autoCheck(act: Activity) {
        val sp = act.getSharedPreferences("upd", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last", 0L) < 6L * 3600L * 1000L) return
        Thread {
            val r = fetch(act)
            if (r is Result.Latest || r is Result.Newer) sp.edit().putLong("last", now).apply()
            if (r is Result.Newer) {
                act.runOnUiThread { if (!act.isFinishing) showDialog(act, r) }
            }
        }.start()
    }

    fun manualCheck(act: Activity, status: (String) -> Unit) {
        status("Procurando atualização…")
        Thread {
            val r = fetch(act)
            act.runOnUiThread {
                if (!act.isFinishing) {
                    when (r) {
                        is Result.NoNet -> status("Sem internet. Tente novamente.")
                        is Result.Error -> status("Não foi possível verificar agora.")
                        is Result.Latest -> status("Você já está na versão mais nova.")
                        is Result.Newer -> {
                            status("Nova versão encontrada: v${r.tag}")
                            showDialog(act, r)
                        }
                    }
                }
            }
        }.start()
    }

    private fun showDialog(act: Activity, r: Result.Newer) {
        AlertDialog.Builder(act, R.style.DialogDark)
            .setTitle("Nova versão disponível")
            .setMessage("A versão v${r.tag} está pronta para instalar. Seus dados continuam salvos.")
            .setPositiveButton("Atualizar") { _, _ -> startDownload(act, r) }
            .setNegativeButton("Depois", null)
            .show()
    }

    private fun canInstall(act: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= 26) act.packageManager.canRequestPackageInstalls() else true
    }

    private fun startDownload(act: Activity, r: Result.Newer) {
        if (!canInstall(act)) {
            AlertDialog.Builder(act, R.style.DialogDark)
                .setTitle("Permissão necessária")
                .setMessage("Para instalar a atualização, o Android precisa da sua permissão em \"Instalar apps desconhecidos\" para este app. Libere e toque em Atualizar de novo.")
                .setPositiveButton("Abrir configurações") { _, _ ->
                    try {
                        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}"))
                        act.startActivity(i)
                    } catch (e: Exception) {
                        Toast.makeText(act, "Abra as configurações do app e libere a instalação.", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        val pad = Ui.dp(act, 20f).toInt()
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, pad)
        val txt = TextView(act)
        txt.text = "Baixando… 0%"
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 100
        box.addView(txt)
        box.addView(bar)
        val dlg = AlertDialog.Builder(act, R.style.DialogDark)
            .setTitle("Atualizando")
            .setView(box)
            .setCancelable(false)
            .create()
        dlg.show()

        Thread {
            try {
                val dir = File(act.cacheDir, "updates")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, APK_NAME)
                val c = URL(r.url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "CursedClash")
                val total = c.contentLengthLong
                c.inputStream.use { ins ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(32 * 1024)
                        var done = 0L
                        var n = 0
                        var lastPct = -1
                        while (ins.read(buf).also { n = it } >= 0) {
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    act.runOnUiThread {
                                        bar.progress = pct
                                        txt.text = "Baixando… $pct%"
                                    }
                                }
                            }
                        }
                    }
                }
                act.runOnUiThread {
                    dlg.dismiss()
                    install(act, f)
                }
            } catch (e: Exception) {
                act.runOnUiThread {
                    dlg.dismiss()
                    Toast.makeText(act, "Falha no download. Tente de novo.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun install(act: Activity, f: File) {
        try {
            val uri = FileProvider.getUriForFile(act, "${act.packageName}.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW)
            i.setDataAndType(uri, "application/vnd.android.package-archive")
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            act.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(act, "Não foi possível abrir o instalador.", Toast.LENGTH_LONG).show()
        }
    }
}

object Novidades {
    class Entry(val id: String, val title: String, val items: List<String>)

    fun load(ctx: Context): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            val lines = ctx.assets.open("novidades.txt").bufferedReader().readLines()
            var id = ""
            var title = ""
            var items = ArrayList<String>()
            for (raw in lines) {
                val l = raw.trim()
                if (l.startsWith("#id ")) {
                    if (id.isNotEmpty()) out.add(Entry(id, title, items))
                    id = l.substring(4).trim()
                    title = ""
                    items = ArrayList()
                } else if (l.startsWith("#titulo ")) {
                    title = l.substring(8).trim()
                } else if (l.startsWith("- ")) {
                    items.add(l.substring(2).trim())
                }
            }
            if (id.isNotEmpty()) out.add(Entry(id, title, items))
        } catch (e: Exception) {
        }
        return out
    }

    fun showIfNew(act: Activity) {
        val e = load(act).firstOrNull() ?: return
        val sp = act.getSharedPreferences("nov", Context.MODE_PRIVATE)
        if (sp.getString("seen", "") == e.id) return
        sp.edit().putString("seen", e.id).apply()
        show(act, e)
    }

    fun showLatest(act: Activity) {
        val e = load(act).firstOrNull() ?: return
        show(act, e)
    }

    private fun show(act: Activity, e: Entry) {
        if (act.isFinishing) return
        val sb = StringBuilder()
        for (it in e.items) sb.append("• ").append(it).append("\n")
        AlertDialog.Builder(act, R.style.DialogDark)
            .setTitle("O que mudou — ${e.title}")
            .setMessage(sb.toString().trim())
            .setPositiveButton("Entendi", null)
            .show()
    }
}

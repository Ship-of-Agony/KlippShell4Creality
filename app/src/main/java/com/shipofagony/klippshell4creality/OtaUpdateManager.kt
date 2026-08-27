package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class OtaUpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val packageNameString: String,
    private val cacheDirFile: File,
    private val showPillDialog: (String, Array<String>, Array<String?>?, (Int) -> Unit) -> Unit,
    private val showToast: (String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun toPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun checkUpdatesSilentlyInBackground() {
        scope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.github.com/repos/Ship-of-Agony/KlippShell4Creality/releases").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    useCaches = false
                    setRequestProperty("User-Agent", "KlippShell-App")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val arr = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                    if (arr.length() > 0) {
                        val latestVersionTag = arr.getJSONObject(0).optString("tag_name", "").replace("v", "").trim()
                        val assetsArray = arr.getJSONObject(0).optJSONArray("assets")
                        val downloadUrl = if (assetsArray != null && assetsArray.length() > 0) assetsArray.getJSONObject(0).optString("browser_download_url", "") else ""
                        val currentVersionName = try {
                            context.packageManager.getPackageInfo(packageNameString, 0).versionName?.replace("v", "")?.trim() ?: "0.8.5"
                        } catch (e: Exception) { "0.8.5" }

                        if (isNewerVersion(latestVersionTag, currentVersionName) && downloadUrl.isNotEmpty()) {
                            withContext(Dispatchers.Main) { showUpdateAvailableDialog(latestVersionTag, downloadUrl) }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("KlippShell", "Silent update check failed", e) } finally { connection?.disconnect() }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        val cleanCurrent = current.replace(Regex("[^0-9.]"), "").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(cleanLatest.size, cleanCurrent.size)
        for (i in 0 until maxLen) {
            val l = cleanLatest.getOrElse(i) { 0 }
            val c = cleanCurrent.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun showUpdateAvailableDialog(newVersion: String, downloadUrl: String) {
        val options = arrayOf(context.getString(R.string.btn_download_now), context.getString(R.string.btn_later))
        showPillDialog(context.getString(R.string.update_available_title, newVersion), options, arrayOf("#4CAF50", null)) { index ->
            if (index == 0 && downloadUrl.isNotEmpty()) {
                downloadAndInstallApk(downloadUrl)
            }
        }
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(context).setCancelable(false).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = context.getString(R.string.download_in_progress)

        val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNightMode) Color.WHITE else Color.BLACK

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progressTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            setPadding(toPx(24), toPx(24), toPx(24), toPx(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvProgress = TextView(context).apply {
            text = "0%"
            gravity = Gravity.CENTER
            setTextColor(textColor)
            textSize = 16f
            setPadding(0, 0, 0, toPx(24))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)
        container?.addView(progressBar)
        container?.addView(tvProgress)
        dialog.show()

        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(apkUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                val fileLength = connection.contentLength
                val input = connection.inputStream

                val file = File(cacheDirFile, "update.apk")
                val output = FileOutputStream(file)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count
                    if (fileLength > 0) {
                        val prog = (total * 100 / fileLength).toInt()
                        withContext(Dispatchers.Main) {
                            progressBar.progress = prog
                            tvProgress.text = "$prog%"
                        }
                    }
                    output.write(data, 0, count)
                }
                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    installApk(file)
                }
            } catch (e: Exception) {
                Log.e("Updater", "Download error", e)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    showToast(context.getString(R.string.toast_download_failed))
                }
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "$packageNameString.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("Updater", "Install error", e)
            showToast(context.getString(R.string.toast_installation_failed))
        }
    }
}
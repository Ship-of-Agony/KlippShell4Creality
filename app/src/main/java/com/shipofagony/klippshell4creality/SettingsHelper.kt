package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class SettingsHelper(private val context: Context) {

    // Kapselt das asynchrone Laden der Textdateien aus den Assets
    suspend fun loadAssetFile(fileName: String): String {
        return withContext(Dispatchers.IO) {
            val sb = java.lang.StringBuilder()
            try {
                context.assets.open(fileName).use { stream ->
                    BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                        sb.append(line).append("\n")
                    }
                }
                sb.toString().trim()
            } catch (e: Exception) {
                Log.e("KlippShell", "Error reading asset file: $fileName", e)
                ""
            }
        }
    }

    // Übernimmt den asynchronen OTA-Check gegen die GitHub-API
    suspend fun checkForUpdates(): UpdateResult {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.github.com/repos/Ship-of-Agony/KlippShell4Creality/releases")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    useCaches = false
                    setRequestProperty("User-Agent", "KlippShell-App")
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(responseText)

                    if (jsonArray.length() > 0) {
                        val jsonObject = jsonArray.getJSONObject(0)
                        val latestVersionTag = jsonObject.optString("tag_name", "").replace("v", "").trim()
                        val assetsArray = jsonObject.optJSONArray("assets")
                        val downloadUrl = if (assetsArray != null && assetsArray.length() > 0) {
                            assetsArray.optJSONObject(0).optString("browser_download_url", "")
                        } else ""

                        val currentVersionName = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName?.replace("v", "")?.trim() ?: "0.8.6"
                        } catch (e: Exception) { "0.8.6" }

                        val cleanCurrent = currentVersionName.takeWhile { it.isDigit() || it == '.' }.trim('.')
                        val cleanLatest = latestVersionTag.takeWhile { it.isDigit() || it == '.' }.trim('.')

                        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
                        val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }

                        var isNewer = false
                        val maxLength = maxOf(currentParts.size, latestParts.size)
                        for (i in 0 until maxLength) {
                            val currentPart = currentParts.getOrElse(i) { 0 }
                            val latestPart = latestParts.getOrElse(i) { 0 }
                            if (latestPart > currentPart) { isNewer = true; break }
                            if (latestPart < currentPart) { isNewer = false; break }
                        }

                        if (isNewer && downloadUrl.isNotEmpty()) {
                            UpdateResult.UpdateAvailable(latestVersionTag, downloadUrl)
                        } else {
                            UpdateResult.UpToDate
                        }
                    } else UpdateResult.UpToDate
                } else UpdateResult.Error(connection.responseCode)
            } catch (e: Exception) {
                Log.e("KlippShell", "GitHub Update-Check failed", e)
                UpdateResult.ServerError
            } finally {
                connection?.disconnect()
            }
        }
    }

    sealed class UpdateResult {
        class UpdateAvailable(val versionTag: String, val url: String) : UpdateResult()
        object UpToDate : UpdateResult()
        class Error(val code: Int) : UpdateResult()
        object ServerError : UpdateResult()
    }
}
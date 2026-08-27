package com.shipofagony.klippshell4creality

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ThumbnailRenderHelper(private val getHostIp: () -> String) {

    suspend fun fetchGCodeThumbnailMetadata(filename: String): Bitmap? {
        val hostIp = getHostIp()
        if (hostIp.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            try {
                val queryUrl = "http://$hostIp:7125/server/files/metadata?filename=${Uri.encode(filename)}"
                val conn = URL(queryUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val thumbnails = json.optJSONObject("result")?.optJSONArray("thumbnails")
                    
                    if (thumbnails != null && thumbnails.length() > 0) {
                        var largestPath = ""
                        var maxW = 0
                        
                        for (i in 0 until thumbnails.length()) {
                            val thumb = thumbnails.getJSONObject(i)
                            val w = thumb.optInt("width", 0)
                            if (w >= maxW) {
                                maxW = w
                                largestPath = thumb.optString("relative_path", "")
                            }
                        }
                        
                        if (largestPath.isNotEmpty()) {
                            val imgUrl = "http://$hostIp:7125/server/files/gcodes/$largestPath"
                            return@withContext downloadThumbnailBitmap(imgUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "Error fetching thumbnail metadata", e)
            }
            null
        }
    }

    private suspend fun downloadThumbnailBitmap(urlString: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.doInput = true
                conn.connectTimeout = 3000
                conn.connect()
                conn.inputStream.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
        }
    }
}
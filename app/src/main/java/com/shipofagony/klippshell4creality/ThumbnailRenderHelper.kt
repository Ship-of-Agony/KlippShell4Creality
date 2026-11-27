package com.shipofagony.klippshell4creality

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class ThumbnailRenderHelper(private val getHostIp: () -> String) {

    suspend fun fetchGCodeThumbnailMetadata(filename: String): Bitmap? {
        val hostIp = getHostIp()
        if (hostIp.isEmpty() || filename.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val queryUrl = "http://$hostIp:7125/server/files/metadata?filename=${Uri.encode(filename)}"
                conn = (URL(queryUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                }

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
                            val cleanThumbPath = largestPath.trimStart('/')
                            val dirPrefix = if (filename.contains("/")) {
                                filename.substringBeforeLast("/") + "/"
                            } else {
                                ""
                            }

                            val candidatePaths = if (dirPrefix.isNotEmpty() && !cleanThumbPath.startsWith(dirPrefix)) {
                                listOf("$dirPrefix$cleanThumbPath", cleanThumbPath)
                            } else {
                                listOf(cleanThumbPath)
                            }

                            for (path in candidatePaths) {
                                val encodedPath = Uri.encode(path, "/")
                                val imgUrl = "http://$hostIp:7125/server/files/gcodes/$encodedPath"
                                val bitmap = downloadThumbnailBitmap(imgUrl)
                                if (bitmap != null) return@withContext bitmap
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "Error fetching thumbnail metadata", e)
            } finally {
                conn?.disconnect()
            }

            // Fallback: Eingebettetes Base64-Thumbnail (inkl. Creality THUMBNAIL_BLOCK_START/END) aus dem G-Code lesen
            extractThumbnailFromGCodeHeader(hostIp, filename)
        }
    }

    private fun extractThumbnailFromGCodeHeader(hostIp: String, filename: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            val encodedFile = Uri.encode(filename, "/")
            val fileUrl = "http://$hostIp:7125/server/files/gcodes/$encodedFile"
            conn = (URL(fileUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 4000
                setRequestProperty("Range", "bytes=0-524288")
            }

            val code = conn.responseCode
            if (code != 200 && code != 206) return null

            val headerText = conn.inputStream.bufferedReader().use { it.readText() }
            if (!headerText.contains("thumbnail", ignoreCase = true)) return null

            val pattern = Pattern.compile(
                ";\\s*(?:THUMBNAIL_BLOCK_START|(?:thumbnail|png|jpg|jpeg)\\s+begin[^\\r\\n]*)([\\r\\n]+)(.*?);\\s*(?:THUMBNAIL_BLOCK_END|(?:thumbnail|png|jpg|jpeg)\\s+end[^\\r\\n]*)",
                Pattern.DOTALL or Pattern.CASE_INSENSITIVE
            )
            val matcher = pattern.matcher(headerText)

            var bestBitmap: Bitmap? = null
            var bestArea = 0

            while (matcher.find()) {
                val rawBlock = matcher.group(2) ?: continue
                val cleanBase64 = rawBlock
                    .lines()
                    .map { line -> line.trim().removePrefix(";").trim() }
                    .filter { line ->
                        line.isNotEmpty() &&
                                !line.startsWith("THUMBNAIL_BLOCK_", ignoreCase = true) &&
                                !line.contains(" begin", ignoreCase = true) &&
                                !line.contains(" end", ignoreCase = true)
                    }
                    .joinToString("")
                    .replace("\\s+".toRegex(), "")

                if (cleanBase64.isEmpty()) continue
                val decodedBytes = try {
                    Base64.decode(cleanBase64, Base64.DEFAULT)
                } catch (_: Exception) {
                    null
                } ?: continue

                val bmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size) ?: continue
                val area = bmp.width * bmp.height
                if (area >= bestArea) {
                    bestBitmap?.let { if (!it.isRecycled) it.recycle() }
                    bestArea = area
                    bestBitmap = bmp
                } else {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            }

            bestBitmap
        } catch (e: Exception) {
            Log.d("KlippShell", "No embedded G-code thumbnail found for $filename", e)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun downloadThumbnailBitmap(urlString: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    doInput = true
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode == 200) {
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }
    }
}
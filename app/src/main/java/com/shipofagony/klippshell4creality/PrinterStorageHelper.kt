package com.shipofagony.klippshell4creality

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PrinterStorageHelper(private val prefs: SharedPreferences) {

    fun getPrintersList(): JSONArray {
        return try {
            JSONArray(prefs.getString("printers_list", "[]"))
        } catch (e: Exception) {
            prefs.edit().putString("printers_list", "[]").apply()
            JSONArray()
        }
    }

    fun savePrinter(name: String, ip: String, port: String, model: String, defaultView: String): Boolean {
        val list = getPrintersList()
        return try {
            list.put(
                JSONObject()
                    .put("name", name)
                    .put("ip", ip)
                    .put("port", port)
                    .put("model", model)
                    .put("defaultView", defaultView)
            )
            prefs.edit().putString("printers_list", list.toString()).apply()
            true
        } catch (e: JSONException) {
            Log.e("KlippShell", "Error saving printer to storage", e)
            false
        }
    }

    fun updatePrinterView(index: Int, defaultView: String): Boolean {
        return try {
            val arr = getPrintersList()
            if (index in 0 until arr.length()) {
                arr.getJSONObject(index).put("defaultView", defaultView)
                prefs.edit().putString("printers_list", arr.toString()).apply()
                true
            } else false
        } catch (e: Exception) {
            Log.e("KlippShell", "Error updating printer view", e)
            false
        }
    }

    fun deletePrinter(index: Int): Boolean {
        return try {
            val currentArray = getPrintersList()
            val newList = JSONArray()
            for (j in 0 until currentArray.length()) {
                if (j != index) {
                    newList.put(currentArray.get(j))
                }
            }
            prefs.edit().putString("printers_list", newList.toString()).apply()
            true
        } catch (e: Exception) {
            Log.e("KlippShell", "Error deleting printer", e)
            false
        }
    }
}
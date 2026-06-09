package com.shipofagony.klippshell4creality

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager

class TvChannelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        // Das System bittet die App, ihre Homescreen-Kanäle zu initialisieren
        if (intent.action == "android.media.tv.action.INITIALIZE_PROGRAMS") {
            Log.d("KlippShell", "TvChannelReceiver: INITIALIZE_PROGRAMS empfangen. Starte Kachel-Update...")
            try {
                // Typisierte Instanziierung des Workers zur Ausführung eines sofortigen Updates
                val workerClass = Class.forName("com.shipofagony.klippshell4creality.KlipperTvWorker") as Class<out androidx.work.ListenableWorker>
                WorkManager.getInstance(context.applicationContext).enqueue(
                    OneTimeWorkRequest.Builder(workerClass).build()
                )
            } catch (e: Exception) {
                Log.e("KlippShell", "TvChannelReceiver: Fehler beim Triggern des KlipperTvWorker", e)
            }
        }
    }
}